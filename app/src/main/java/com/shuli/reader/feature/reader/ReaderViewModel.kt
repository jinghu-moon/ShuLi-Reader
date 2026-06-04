package com.shuli.reader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.data.toFactoryType
import com.shuli.reader.core.data.toFontWeight
import com.shuli.reader.core.data.toPageAnimType
import com.shuli.reader.core.data.toStorageString
import com.shuli.reader.core.data.toTextAlign
import com.shuli.reader.core.data.toChineseConvert
import com.shuli.reader.core.data.toHeaderVisibility
import com.shuli.reader.core.data.toSlotContent
import com.shuli.reader.core.data.toTitleAlign
import com.shuli.reader.core.reader.HeaderConfig
import com.shuli.reader.core.reader.FooterConfig
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.core.reader.SlotResolver
import com.shuli.reader.core.reader.SlotResolution
import com.shuli.reader.core.reader.TitleAlign
import com.shuli.reader.core.reader.TitleStyleConfig
import com.shuli.reader.core.database.dao.BookmarkDao
import com.shuli.reader.core.database.dao.NoteDao
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.parser.DecodedSegment
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import com.shuli.reader.core.reader.ChapterProvider
import com.shuli.reader.core.reader.Paginator
import com.shuli.reader.core.reader.AndroidTextMeasurer
import com.shuli.reader.core.reader.SimpleTextMeasurer
import com.shuli.reader.core.reader.animation.PageDelegate
import com.shuli.reader.core.reader.animation.PageDelegateFactory
import com.shuli.reader.core.reader.cache.CacheManager
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.core.repository.BookRepository
import com.shuli.reader.core.repository.SearchResult
import com.shuli.reader.feature.reader.book.BookSessionManager
import com.shuli.reader.feature.reader.notes.BookmarkNotesManager
import com.shuli.reader.feature.reader.presets.ReaderPresetManager
import com.shuli.reader.feature.reader.pagination.ChapterPaginationCoordinator
import com.shuli.reader.feature.reader.tts.TtsPlaybackManager
import com.shuli.reader.core.tts.TtsConfig
import com.shuli.reader.core.tts.TtsController
import com.shuli.reader.core.tts.TtsEngine
import com.shuli.reader.core.tts.TtsState
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme
import com.shuli.reader.core.font.FontManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * 排版参数（变化时需 reflow）
 */
private data class ReaderLayoutPrefs(
    val fontSize: Float,
    val lineSpacing: Float,
    val paragraphSpacing: Float,
    val indent: Float,
    val marginHorizontal: Float,
    val marginVertical: Float,
    val letterSpacing: Float,
    val useZhLayout: Boolean,
    val chineseConvert: com.shuli.reader.core.data.ChineseConvert,
    val usePanguSpacing: Boolean,
    val bottomJustify: Boolean,
)

/**
 * 外观参数（变化时仅重绘）
 */
private data class ReaderVisualPrefs(
    val readingFont: String,
    val fontWeight: com.shuli.reader.core.data.ReaderFontWeight,
    val textAlign: com.shuli.reader.core.data.ReaderTextAlign,
    val pageAnimType: com.shuli.reader.core.data.PageAnimType,
    val header: HeaderConfig,
    val footer: FooterConfig,
    val headerFooterAlpha: Float,
    val showProgress: Boolean,
    val showHeaderLine: Boolean,
    val showFooterLine: Boolean,
    val headerFontSizeRatio: Float,
    val footerFontSizeRatio: Float,
)

/**
 * 行为参数（变化时仅更新标志位）
 */
private data class ReaderBehaviorPrefs(
    val brightness: Float,
    val keepScreenOn: Boolean,
    val volumeKeyTurnPage: Boolean,
    val edgeTurnPage: Boolean,
    val edgeWidthPercent: Float,
)

/**
 * 阅读器 UI 状态
 */
enum class OverlayPanel {
    NONE, DIRECTORY, QUICK_SETTINGS
}

data class ReaderUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bookId: Long = 0L,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val currentPage: TextPage? = null,
    val currentChapter: TextChapter? = null,
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val totalPages: Int = 0,
    val totalChapters: Int = 0,
    val showToolbar: Boolean = false,
    val overlayPanel: OverlayPanel = OverlayPanel.NONE,
    val showMenu: Boolean = false,
    val showSearch: Boolean = false,
    val pageAnimType: PageDelegateFactory.PageAnimType = PageDelegateFactory.PageAnimType.HORIZONTAL,
    val pageRenderMode: PageRenderMode = PageRenderMode.SEQUENTIAL,
    val readerPreferences: ReaderPreferences = ReaderPreferences(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val chapterTitles: List<String> = emptyList(),
    val chapterWordCounts: List<Int> = emptyList(),
    /** 已分页章节的真实页数（key=chapterIndex），用于精确计算全书进度 */
    val chapterPageCounts: Map<Int, Int> = emptyMap(),
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val currentSearchResultIndex: Int = -1,
    val selectedRange: SelectionRange? = null,
    val ttsState: TtsState = TtsState.IDLE,
    val ttsActiveRange: SelectionRange? = null,
    val sleepTimerRemainingSeconds: Int = -1,
    val presets: List<com.shuli.reader.core.database.entity.ReaderPresetEntity> = emptyList(),
    /** 用户导入的自定义字体列表 */
    val customFonts: List<com.shuli.reader.core.font.FontManager.FontEntry> = emptyList(),
    /** 缓存的主题颜色，避免每次访问 themeColors 都创建中间对象 */
    val themeColors: ThemeColors = readerPreferences.backgroundColor
        .toReaderColorScheme().toCanvasThemeColors(),
    /** 排版版本号，每次 reflow 递增，用于 Canvas 层 crossfade 判断 */
    val layoutVersion: Int = 0,
    /** 排版 reflow 进行中，为 true 时跳过 Paint 更新，避免旧页面用新字号渲染导致闪烁 */
    val isReflowing: Boolean = false,
) {
    val showDirectory: Boolean get() = overlayPanel == OverlayPanel.DIRECTORY
    val showQuickSettings: Boolean get() = overlayPanel == OverlayPanel.QUICK_SETTINGS
}

/**
 * 阅读器 ViewModel
 */
@OptIn(FlowPreview::class)
class ReaderViewModel(
    private val userPreferences: UserPreferences? = null,
    private val bookRepository: BookRepository? = null,
    private val bookmarkDao: BookmarkDao? = null,
    private val noteDao: NoteDao? = null,
    private val presetDao: com.shuli.reader.core.database.dao.ReaderPresetDao? = null,
    private val readingProgressDao: com.shuli.reader.core.database.dao.ReadingProgressDao? = null,
    private val paginator: Paginator = Paginator(SimpleTextMeasurer()),
    ttsEngine: TtsEngine? = null,
    private val fontManager: com.shuli.reader.core.font.FontManager? = null,
    private val stringResolver: () -> com.shuli.reader.core.i18n.AppStrings = { com.shuli.reader.core.i18n.AppStrings.ZhHans },
    private val appContext: android.content.Context? = null,
) : ViewModel() {

    companion object {
        private const val TOOLBAR_AUTO_HIDE_DELAY_MS = 5000L
        private const val TAG = "ReaderPerf"
    }

    /** 性能诊断：记录各阶段耗时 */
    private fun logPerf(label: String, startMs: Long) {
        if (com.shuli.reader.BuildConfig.DEBUG) {
            android.util.Log.d(TAG, "$label: ${System.currentTimeMillis() - startMs}ms")
        }
    }

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // AndroidTextMeasurer：首次 syncPaint 时惰性创建，此后复用
    private var _androidTextMeasurer: AndroidTextMeasurer? = null

    /**
     * 由 View 层调用，将 Canvas 的 [Paint] 配置同步到分页测量器。
     * 首次调用时替换 Paginator 的 measurer 为 [AndroidTextMeasurer]。
     *
     * @param paint View 层 textPaint 的属性快照（textSize / typeface / letterSpacing / fakeBold）
     */
    fun syncTextMeasurerPaint(paint: android.graphics.Paint) {
        val existing = _androidTextMeasurer
        if (existing == null) {
            _androidTextMeasurer = AndroidTextMeasurer(paint).also {
                paginator.textMeasurer = it
            }
        } else {
            existing.updatePaint(paint)
        }
    }

    // 工具栏自动隐藏计时器
    private var toolbarAutoHideJob: Job? = null

    // M1: 章节加载 Job（防止快速连续调用导致多个 openChapter 并发）
    private var chapterJob: Job? = null

    // T-41: 当前排版参数的哈希值，用于持久化 chapterPageCounts
    private var currentLayoutHash: String = ""

    // R7: 章节缓存管理器（从 BookCacheStore 获取，跨 ViewModel 生命周期复用）
    private var cacheManager: CacheManager = CacheManager.forMemoryClass(256)

    // R7: 章节提供器（预加载相邻章节）
    private val chapterProvider = ChapterProvider(paginator)

    // 章节分页协调器（SRP：分页/reflow/预加载/字数统计/坐标转换）
    private val paginationCoordinator = ChapterPaginationCoordinator(
        uiState = _uiState,
        paginator = paginator,
        cacheManager = cacheManager,
        bookRepository = bookRepository,
        appContext = appContext,
        scope = viewModelScope,
    )
    private val sessionManager = BookSessionManager(
        uiState = _uiState,
        bookRepository = bookRepository,
        readingProgressDao = readingProgressDao,
        scope = viewModelScope,
    ).apply {
        onGetLoadedBookContent = { loadedBookContent }
        onCharToByteOffset = { this@ReaderViewModel.charToByteOffset(it) }
        onIsCurrentBookEpub = { isCurrentBookEpub }
    }

    private val presetManager = ReaderPresetManager(
        uiState = _uiState,
        presetDao = presetDao,
        scope = viewModelScope,
    ).apply {
        onApplyPreferences = { prefs -> applyAllPreferences(prefs) }
    }

    init {
        sessionManager.initialize()
        loadPresets()
        loadCustomFonts()
    }

    // ── 字体管理 ──────────────────────────────────────────────

    fun loadCustomFonts() {
        val fm = fontManager
        if (fm == null) {
            android.util.Log.w("FontManager", "loadCustomFonts: fontManager 为 null")
            return
        }
        val fonts = fm.listFonts()
        android.util.Log.d("FontManager", "loadCustomFonts: 加载了 ${fonts.size} 个自定义字体")
        _uiState.value = _uiState.value.copy(customFonts = fonts)
    }

    fun importFont(uri: android.net.Uri, displayName: String? = null) {
        val fm = fontManager ?: run {
            android.util.Log.e("FontManager", "importFont: fontManager 为 null，无法导入")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = fm.importFont(uri, displayName)
                android.util.Log.d("FontManager", "字体导入成功: id=${entry.id}, file=${entry.file.name}, size=${entry.file.length()}")
                withContext(Dispatchers.Main) {
                    loadCustomFonts()
                    val count = _uiState.value.customFonts.size
                    android.util.Log.d("FontManager", "loadCustomFonts 完成, customFonts.size=$count")
                    android.widget.Toast.makeText(
                        fm.context,
                        stringResolver().fontImportSuccess(entry.name, count),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("FontManager", "字体导入失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        fm.context,
                        stringResolver().fontImportFailed(e.message ?: ""),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    fun deleteFont(fontId: String) {
        val fm = fontManager ?: return
        fm.deleteFontById(fontId)
        loadCustomFonts()
    }

    /**
     * 翻页动画委托
     */
    var pageDelegate: PageDelegate = PageDelegateFactory.create(_uiState.value.pageAnimType)
        private set


    private var loadedBookContent: BookContent? = null
    /** 缓存当前书籍文件路径，避免 paginateChapter 重复查询 DB */
    private var currentBookFilePath: String? = null
    /** 当前书籍是否为 EPUB（轻量解析时 content 为空，跳过全量字数统计） */
    private var isCurrentBookEpub: Boolean = false
    /** 当前章节的 utf16IndexToByte 映射表（v4：字节↔字符坐标桥接） — 委托给 paginationCoordinator */
    private var currentChapterUtf16Map: IntArray
        get() = paginationCoordinator.currentChapterUtf16Map
        set(value) { paginationCoordinator.updateUtf16Map(value) }
    /** 缓存当前章节已解码文本，避免 loadChapterContent + paginateChapterStreaming 重复读文件 — 委托给 paginationCoordinator */
    private var cachedChapterText: String?
        get() = paginationCoordinator.cachedChapterText
        set(value) { paginationCoordinator.cachedChapterText = value }
    private val ttsManager = TtsPlaybackManager(
        uiState = _uiState,
        ttsEngine = ttsEngine,
        appContext = appContext,
        scope = viewModelScope,
    ).apply {
        onNextPage = { this@ReaderViewModel.nextPage() }
        onOpenChapter = { this@ReaderViewModel.openChapter(it) }
        onGetChapterCount = { loadedBookContent?.let { paginationCoordinator.run { it.normalizedChapters() } }?.size ?: 0 }
        onSentenceRangesForCurrentPage = { this@ReaderViewModel.sentenceRangesForCurrentPage() }
    }
    private val notesManager = BookmarkNotesManager(
        uiState = _uiState,
        bookmarkDao = bookmarkDao,
        noteDao = noteDao,
        scope = viewModelScope,
    ).apply {
        onGetLoadedBookContent = { loadedBookContent }
        onCharToByteOffset = { this@ReaderViewModel.charToByteOffset(it) }
        onByteToCharOffset = { this@ReaderViewModel.byteToCharOffset(it) }
        onJumpToChapterPosition = { chapterIndex, byteOffset -> this@ReaderViewModel.jumpToChapterPosition(chapterIndex, byteOffset) }
        onClearTextSelection = { this@ReaderViewModel.clearTextSelection() }
    }

    var density: Float = 3f
        private set

    /** 屏幕像素尺寸，由 ReaderScreen 传入 */
    var screenWidthPx: Int = 1080
        private set
    var screenHeightPx: Int = 1920
        private set

    fun setDensity(value: Float) {
        if (density != value) {
            density = value
            paginationCoordinator.density = value
            reflowCurrentChapter(_uiState.value.readerPreferences)
        }
    }

    fun setScreenSize(widthPx: Int, heightPx: Int) {
        if (screenWidthPx == widthPx && screenHeightPx == heightPx) return
        screenWidthPx = widthPx
        screenHeightPx = heightPx
        paginationCoordinator.screenWidthPx = widthPx
        paginationCoordinator.screenHeightPx = heightPx
        reflowCurrentChapter(_uiState.value.readerPreferences)
    }

    // ── 字节↔字符坐标桥接（v4） ──────────────────────────────────

    /** UTF-16 char index → 章节内相对字节偏移 — 委托给 paginationCoordinator */
    private fun charToByteOffset(charIndex: Int): Int = paginationCoordinator.charToByteOffset(charIndex)

    /** 章节内相对字节偏移 → UTF-16 char index — 委托给 paginationCoordinator */
    private fun byteToCharOffset(byteOffset: Int): Int = paginationCoordinator.byteToCharOffset(byteOffset)

    init {
        // 监听用户偏好设置变化
        userPreferences?.let { prefs ->
            // Group A: 排版参数 → 变化时需 reflow（重新分页）
            viewModelScope.launch {
                combine(
                    prefs.defaultFontSize,
                    prefs.defaultLineSpacing,
                    prefs.defaultParagraphSpacing,
                    prefs.defaultIndent,
                    prefs.marginHorizontal,
                    prefs.marginVertical,
                    prefs.letterSpacing,
                    prefs.useZhLayout,
                    prefs.chineseConvert,
                    prefs.titleAlign,
                    prefs.titleSizeOffset,
                    prefs.titleMarginTop,
                    prefs.titleMarginBottom,
                    prefs.usePanguSpacing,
                    prefs.bottomJustify,
                ) { flows: Array<Any> ->
                    Triple(
                        ReaderLayoutPrefs(
                            fontSize = flows[0] as Float,
                            lineSpacing = flows[1] as Float,
                            paragraphSpacing = flows[2] as Float,
                            indent = flows[3] as Float,
                            marginHorizontal = flows[4] as Float,
                            marginVertical = flows[5] as Float,
                            letterSpacing = flows[6] as Float,
                            useZhLayout = flows[7] as Boolean,
                            chineseConvert = (flows[8] as String).toChineseConvert(),
                            usePanguSpacing = flows[13] as Boolean,
                            bottomJustify = flows[14] as Boolean,
                        ),
                        TitleStyleConfig(
                            align = (flows[9] as String).toTitleAlign(),
                            sizeOffsetSp = flows[10] as Int,
                            marginTopDp = flows[11] as Float,
                            marginBottomDp = flows[12] as Float,
                        ),
                        flows[8] as String, // chineseConvert raw
                    )
                }.collectLatest { (layoutPrefs, titleStyle, chineseConvertRaw) ->
                    val current = _uiState.value.readerPreferences
                    val updated = current.copy(
                        fontSize = layoutPrefs.fontSize,
                        lineSpacing = layoutPrefs.lineSpacing,
                        paragraphSpacing = layoutPrefs.paragraphSpacing,
                        indent = layoutPrefs.indent,
                        marginHorizontal = layoutPrefs.marginHorizontal,
                        marginVertical = layoutPrefs.marginVertical,
                        letterSpacing = layoutPrefs.letterSpacing,
                        useZhLayout = layoutPrefs.useZhLayout,
                        chineseConvert = chineseConvertRaw.toChineseConvert(),
                        usePanguSpacing = layoutPrefs.usePanguSpacing,
                        bottomJustify = layoutPrefs.bottomJustify,
                        titleStyle = titleStyle,
                    )
                    _uiState.value = _uiState.value.copy(readerPreferences = updated)
                    reflowCurrentChapter(updated)
                }
            }

            // Group B: 外观参数 → 变化时仅重绘（不 reflow）
            viewModelScope.launch {
                combine(
                    prefs.readingFont,
                    prefs.fontWeight,
                    prefs.textAlign,
                    prefs.defaultPageAnim,
                    prefs.headerVisibility,
                    prefs.headerLeft,
                    prefs.headerCenter,
                    prefs.headerRight,
                    prefs.footerVisibility,
                    prefs.footerLeft,
                    prefs.footerCenter,
                    prefs.footerRight,
                    prefs.headerFooterAlpha,
                    prefs.showProgress,
                    prefs.headerMarginTop,
                    prefs.footerMarginBottom,
                    prefs.showHeaderLine,
                    prefs.showFooterLine,
                    prefs.headerFontSizeRatio,
                    prefs.footerFontSizeRatio,
                ) { flows: Array<Any> ->
                    ReaderVisualPrefs(
                        readingFont = flows[0] as String,
                        fontWeight = (flows[1] as String).toFontWeight(),
                        textAlign = (flows[2] as String).toTextAlign(),
                        pageAnimType = (flows[3] as String).toPageAnimType(),
                        header = HeaderConfig(
                            visibility = (flows[4] as String).toHeaderVisibility(),
                            left = (flows[5] as String).toSlotContent(),
                            center = (flows[6] as String).toSlotContent(),
                            right = (flows[7] as String).toSlotContent(),
                            marginTop = flows[14] as Float,
                        ),
                        footer = FooterConfig(
                            visibility = (flows[8] as String).toHeaderVisibility(),
                            left = (flows[9] as String).toSlotContent(),
                            center = (flows[10] as String).toSlotContent(),
                            right = (flows[11] as String).toSlotContent(),
                            marginBottom = flows[15] as Float,
                        ),
                        headerFooterAlpha = flows[12] as Float,
                        showProgress = flows[13] as Boolean,
                        showHeaderLine = flows[16] as Boolean,
                        showFooterLine = flows[17] as Boolean,
                        headerFontSizeRatio = flows[18] as Float,
                        footerFontSizeRatio = flows[19] as Float,
                    )
                }.collectLatest { visual ->
                    val current = _uiState.value.readerPreferences
                    val updated = current.copy(
                        readingFont = visual.readingFont,
                        fontWeight = visual.fontWeight,
                        textAlign = visual.textAlign,
                        pageAnimType = visual.pageAnimType,
                        header = visual.header,
                        footer = visual.footer,
                        headerFooterAlpha = visual.headerFooterAlpha,
                        showProgress = visual.showProgress,
                        showHeaderLine = visual.showHeaderLine,
                        showFooterLine = visual.showFooterLine,
                        headerFontSizeRatio = visual.headerFontSizeRatio,
                        footerFontSizeRatio = visual.footerFontSizeRatio,
                    )
                    val factoryType = visual.pageAnimType.toFactoryType()
                    _uiState.value = _uiState.value.copy(
                        readerPreferences = updated,
                        pageAnimType = factoryType,
                    )
                    pageDelegate = PageDelegateFactory.create(factoryType)
                }
            }

            // Group C: 行为参数 → 变化时仅更新标志位（不重绘、不 reflow）
            viewModelScope.launch {
                combine(
                    prefs.brightness,
                    prefs.keepScreenOn,
                    prefs.volumeKeyTurnPage,
                    prefs.edgeTurnPage,
                    prefs.edgeWidthPercent,
                ) { flows: Array<Any> ->
                    ReaderBehaviorPrefs(
                        brightness = flows[0] as Float,
                        keepScreenOn = flows[1] as Boolean,
                        volumeKeyTurnPage = flows[2] as Boolean,
                        edgeTurnPage = flows[3] as Boolean,
                        edgeWidthPercent = flows[4] as Float,
                    )
                }.collectLatest { behavior ->
                    val current = _uiState.value.readerPreferences
                    _uiState.value = _uiState.value.copy(
                        readerPreferences = current.copy(
                            brightness = behavior.brightness,
                            keepScreenOn = behavior.keepScreenOn,
                            volumeKeyTurnPage = behavior.volumeKeyTurnPage,
                            edgeTurnPage = behavior.edgeTurnPage,
                            edgeWidthPercent = behavior.edgeWidthPercent,
                        ),
                    )
                }
            }
        }
    }

    /**
     * 打开书籍
     */
    fun openBook(bookId: Long) {
        val perfStart = System.currentTimeMillis()

        // R7: 切换书籍时释放旧书籍缓存到 BookCacheStore
        val oldBookId = _uiState.value.bookId
        if (oldBookId != 0L && oldBookId != bookId) {
            com.shuli.reader.core.reader.cache.BookCacheStore.releaseBook(oldBookId.toString())
        }

        // 从 BookCacheStore 获取当前书籍的缓存（可能复用之前的分页结果）
        cacheManager = com.shuli.reader.core.reader.cache.BookCacheStore.getBookCache(bookId.toString())

        // R7: 结束上一次阅读会话，开始新会话
        val sessionElapsed = sessionManager.endSession()
        if (oldBookId != 0L && sessionElapsed > 0L) {
            sessionManager.persistReadingTime(oldBookId, sessionElapsed)
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, bookId = bookId)

            try {
                val repository = bookRepository
                if (repository == null) {
                    openFallbackBook(bookId)
                    loadBookmarks()
                    loadNotes()
                    return@launch
                }

                // 1. 获取书籍元数据
                val dbStart = System.currentTimeMillis()
                val book = withContext(Dispatchers.IO) {
                    repository.getBookById(bookId).first()
                } ?: run {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Book not found: $bookId",
                    )
                    return@launch
                }
                logPerf("getBookById", dbStart)

                // 2. 确保章节目录索引存在（从 DB 加载或重新解析）
                val indexStart = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    repository.ensureChapterIndex(bookId)
                }
                val chapterIndexList = withContext(Dispatchers.IO) {
                    repository.getChapterIndex(bookId)
                }
                logPerf("ensureChapterIndex [${book.fileType}]", indexStart)

                currentBookFilePath = book.filePath
                paginationCoordinator.currentBookFilePath = book.filePath
                isCurrentBookEpub = book.fileType == "EPUB"

                val chapterCount = chapterIndexList.size
                if (chapterCount == 0) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                // v4: 通过 durByteOffset 查找当前章节（取代已删除的 durChapterIndex）
                val durByteOffset = book.durByteOffset
                val chapterIndex = chapterIndexList
                    .indexOfLast { it.byteStart <= durByteOffset }
                    .coerceIn(0, chapterCount - 1)

                _uiState.value = _uiState.value.copy(
                    bookTitle = book.title,
                    chapterTitle = book.durChapterTitle.orEmpty(),
                    chapterIndex = chapterIndex,
                    totalChapters = chapterCount,
                    chapterTitles = chapterIndexList.map { it.title },
                    chapterWordCounts = emptyList(), // 延迟计算，不阻塞首屏
                )

                // 3. 加载当前章节内容（从 DB 索引按需读取），同时获取 utf16IndexToByte 映射
                val parseStart = System.currentTimeMillis()
                val (content, segment) = loadChapterContent(repository, book, chapterIndexList, chapterIndex)
                loadedBookContent = content
                paginationCoordinator.loadedBookContent = content
                currentChapterUtf16Map = segment?.utf16IndexToByte ?: IntArray(0)
                cachedChapterText = content.content.ifEmpty { null }
                logPerf("loadChapterContent", parseStart)

                withContext(Dispatchers.IO) {
                    repository.updateLastReadTime(bookId)
                }
                loadBookmarks()
                loadNotes()
                sessionManager.startSession()

                logPerf("openBook.preparation", perfStart)

                // v4: 将 durByteOffset 转为章节内字符偏移，用于分页跳转
                val chapterByteStart = chapterIndexList[chapterIndex].byteStart
                val relativeByteOffset = (durByteOffset - chapterByteStart).toInt().coerceAtLeast(0)
                val targetCharOffset = byteToCharOffset(relativeByteOffset)

                // 4. 流式分页：首页秒开，目标位置自动跳转
                val paginateStart = System.currentTimeMillis()
                // 计算布局哈希，用于持久化 chapterPageCounts
                currentLayoutHash = paginationCoordinator.computeLayoutHash(_uiState.value.readerPreferences)
                chapterJob = paginationCoordinator.paginateChapterStreaming(
                    content = content,
                    index = chapterIndex,
                    targetCharOffset = targetCharOffset,
                    onDone = {
                        logPerf("firstPageReady", perfStart)
                        paginationCoordinator.preloadAdjacentChapters(content, chapterIndex)
                        // TXT: 从 DB 章节索引直接计算字数
                        if (!isCurrentBookEpub) {
                            paginationCoordinator.computeChapterWordCounts(chapterIndexList)
                        }
                        // 加载已持久化的页数缓存（异步，不阻塞首屏）
                        viewModelScope.launch(Dispatchers.Default) {
                            val ctx = appContext ?: return@launch
                            val persisted = com.shuli.reader.core.reader.cache.PageCountPersistence.load(
                                ctx, bookId.toString(), currentLayoutHash,
                            )
                            if (persisted.isNotEmpty()) {
                                _uiState.value = _uiState.value.copy(
                                    chapterPageCounts = persisted + _uiState.value.chapterPageCounts,
                                )
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message,
                )
            }
        }
    }

    /**
     * 根据 DB 章节索引加载当前章节内容。
     * TXT: 使用 byteStart/byteEnd 按需读取，返回 DecodedSegment（含 utf16IndexToByte）
     * EPUB: 使用 spineIndex 解析 XHTML
     *
     * @return Pair<BookContent, DecodedSegment?> — DecodedSegment 仅 TXT 有值
     */
    private suspend fun loadChapterContent(
        repository: BookRepository,
        book: com.shuli.reader.core.database.entity.BookEntity,
        chapterIndexList: List<com.shuli.reader.core.database.entity.BookChapterEntity>,
        chapterIndex: Int,
    ): Pair<BookContent, DecodedSegment?> = withContext(Dispatchers.IO) {
        val file = File(book.filePath)
        val chapter = chapterIndexList.getOrNull(chapterIndex)

        if (chapter == null) {
            // 兜底：无章节时返回空内容
            return@withContext BookContent(
                title = book.title,
                author = book.author,
                encoding = "UTF-8",
                totalLength = 0L,
                chapters = emptyList(),
                content = "",
                bookId = book.id,
            ) to null
        }

        // 构建 Chapter 列表供分页使用（v4: byteStart/byteEnd）
        val chapters = chapterIndexList.map { ch ->
            Chapter(
                title = ch.title,
                byteStart = ch.byteStart,
                byteEnd = ch.byteEnd,
                spineIndex = ch.spineIndex,
            )
        }

        if (isCurrentBookEpub) {
            // EPUB: 通过 repository 解析（内部用 spineIndex）
            val chapterText = repository.getChapterText(file, chapterIndex, chapters)
            BookContent(
                title = book.title,
                author = book.author,
                encoding = chapter.charset,
                totalLength = chapterText.length.toLong(),
                chapters = chapters,
                content = chapterText,
                bookId = book.id,
            ) to null
        } else {
            // TXT: 通过 loadChapterText 获取 DecodedSegment（含 utf16IndexToByte 映射）
            val segment = repository.loadChapterText(book.id, chapterIndex)
            val chapterText = segment?.text ?: ""
            BookContent(
                title = book.title,
                author = book.author,
                encoding = chapter.charset,
                totalLength = chapterText.length.toLong(),
                chapters = chapters,
                content = chapterText,
                bookId = book.id,
            ) to segment
        }
    }

    /**
     * 下一页
     */
    fun nextPage() {
        val state = _uiState.value
        val chapter = state.currentChapter ?: return

        if (state.pageIndex < chapter.lastIndex) {
            _uiState.value = state.copy(
                pageIndex = state.pageIndex + 1,
                currentPage = chapter.getPage(state.pageIndex + 1),
            )
            // R7: 翻页时防抖保存阅读进度
            saveReadingProgress(immediate = false)
        } else if (loadedBookContent != null && state.chapterIndex < paginationCoordinator.run { loadedBookContent!!.normalizedChapters() }.size - 1) {
            // 跨章：打开下一章
            openChapter(state.chapterIndex + 1)
        } else {
            // 已经是最后一章最后一页
            appContext?.let {
                android.widget.Toast.makeText(it, stringResolver().alreadyLastPage, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 上一页
     */
    fun prevPage() {
        val state = _uiState.value
        val chapter = state.currentChapter ?: return

        if (state.pageIndex > 0) {
            _uiState.value = state.copy(
                pageIndex = state.pageIndex - 1,
                currentPage = chapter.getPage(state.pageIndex - 1),
            )
            // R7: 翻页时防抖保存阅读进度
            saveReadingProgress(immediate = false)
        } else if (state.chapterIndex > 0) {
            // 跨章：打开上一章末页
            openChapter(state.chapterIndex - 1, targetToLastPage = true)
        } else {
            // 已经是第一章第一页
            appContext?.let {
                android.widget.Toast.makeText(it, stringResolver().alreadyFirstPage, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 跳转到指定页码
     */
    fun jumpToPage(pageIndex: Int) {
        val chapter = _uiState.value.currentChapter ?: return
        val safe = pageIndex.coerceIn(0, chapter.lastIndex)
        if (safe == _uiState.value.pageIndex) return

        _uiState.value = _uiState.value.copy(
            pageIndex = safe,
            currentPage = chapter.getPage(safe),
            pageRenderMode = PageRenderMode.JUMP,
            selectedRange = null,
            ttsActiveRange = null,
        )
        saveReadingProgress(immediate = true)
        // 一帧后回到 SEQUENTIAL，让 View 自然预热邻页
        viewModelScope.launch {
            delay(16)
            _uiState.value = _uiState.value.copy(pageRenderMode = PageRenderMode.SEQUENTIAL)
        }
    }

    /**
     * 跳转到指定章节的字节偏移位置（v4：统一入口）。
     * 同章跳转：用 utf16IndexToByte 映射转为 charOffset 后定位页码。
     * 跨章跳转：openChapter 加载新章节后自动跳转。
     */
    fun jumpToChapterPosition(chapterIndex: Int, byteOffset: Long) {
        val state = _uiState.value
        val chapter = state.currentChapter
        if (chapter?.chapterIndex == chapterIndex && chapter.pageSize > 0) {
            // 同章：将绝对 byteOffset 转为章节内相对 byteOffset，再转为 charOffset
            val chapters = loadedBookContent?.chapters
            val chapterByteStart = chapters?.getOrNull(chapterIndex)?.byteStart ?: 0L
            val relativeByte = (byteOffset - chapterByteStart).toInt().coerceAtLeast(0)
            val charOffset = byteToCharOffset(relativeByte)
            val pi = chapter.getPageIndexByCharIndex(charOffset)
            jumpToPage(pi)
        } else {
            // 跨章：openChapter 加载新章节后查找 byteOffset 对应的页
            openChapter(chapterIndex, targetByteOffset = byteOffset)
        }
    }

    // ── 进度条 Scrub 接口 ──────────────────────────────────────

    private val scrubChannel = Channel<Int>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            scrubChannel.consumeAsFlow()
                .sample(80.milliseconds)
                .collect { pageIndex -> emitScrubFrame(pageIndex) }
        }
    }

    fun startPageScrub() {
        _uiState.value = _uiState.value.copy(pageRenderMode = PageRenderMode.SCRUBBING)
    }

    fun scrubToPage(pageIndex: Int) {
        val chapter = _uiState.value.currentChapter ?: return
        val safe = pageIndex.coerceIn(0, chapter.lastIndex)
        // 立即更新 pageIndex（页脚数字跟手）
        _uiState.value = _uiState.value.copy(pageIndex = safe)
        // 把"换页"扔进节流 channel
        scrubChannel.trySend(safe)
    }

    private fun emitScrubFrame(pageIndex: Int) {
        val chapter = _uiState.value.currentChapter ?: return
        _uiState.value = _uiState.value.copy(currentPage = chapter.getPage(pageIndex))
    }

    fun commitPageScrub() {
        val state = _uiState.value
        val pi = state.pageIndex
        val chapter = state.currentChapter ?: return
        _uiState.value = state.copy(
            currentPage = chapter.getPage(pi),
            pageRenderMode = PageRenderMode.SEQUENTIAL,
        )
        sessionManager.saveReadingProgress(immediate = true)
    }

    private fun saveReadingProgress(immediate: Boolean) = sessionManager.saveReadingProgress(immediate)

    /**
     * 打开章节
     * @param targetToLastPage 是否跳转到末页（跨章前翻时使用）
     * @param targetByteOffset 目标字节偏移（书签/笔记/搜索跳转时使用，v4）
     */
    fun openChapter(
        index: Int,
        targetToLastPage: Boolean = false,
        targetByteOffset: Long = -1L,
    ) {
        resetToolbarAutoHide()
        val content = loadedBookContent
        if (content == null) {
            android.util.Log.w(TAG, "openChapter[$index]: loadedBookContent 为 null，走 fallback")
            viewModelScope.launch { openFallbackChapter(index) }
            return
        }
        val normalizedChapters = paginationCoordinator.run { content.normalizedChapters() }
        android.util.Log.d(TAG, "openChapter[$index]: loadedBookContent 非 null，章节数=${normalizedChapters.size}")

        val safeIndex = index.coerceIn(0, (normalizedChapters.size - 1).coerceAtLeast(0))

        // M1: 取消上一次章节加载，防止快速连续调用导致并发冲突
        chapterJob?.cancel()

        // v4: 将 byteOffset 转为 charOffset（跨章时需要重新加载章节获取新映射）
        val targetCharOffset = if (targetToLastPage) {
            -1
        } else if (targetByteOffset >= 0) {
            // 跨章：先加载新章节获取 utf16IndexToByte 映射，再转换
            // 由 paginateChapterStreaming 内部处理（加载章节后更新 currentChapterUtf16Map）
            // 这里先传 -1，由 onDone 中用 targetByteOffset 跳转
            -1
        } else {
            -1
        }

        chapterJob = paginationCoordinator.paginateChapterStreaming(
            content = content,
            index = safeIndex,
            targetCharOffset = targetCharOffset,
            onDone = {
                // v4: 如果有 targetByteOffset，加载完章节后用映射转为 charOffset 再跳转
                if (targetByteOffset >= 0 && !targetToLastPage) {
                    val chapters = paginationCoordinator.run { content.normalizedChapters() }
                    val chapterByteStart = chapters.getOrNull(safeIndex)?.byteStart ?: 0L
                    val relativeByte = (targetByteOffset - chapterByteStart).toInt().coerceAtLeast(0)
                    val charOffset = byteToCharOffset(relativeByte)
                    val chapter = _uiState.value.currentChapter
                    if (chapter != null && chapter.pageSize > 0) {
                        val pi = chapter.getPageIndexByCharIndex(charOffset)
                        _uiState.value = _uiState.value.copy(
                            pageIndex = pi,
                            currentPage = chapter.getPage(pi),
                        )
                    }
                }
                // targetToLastPage：分页完成后跳转到末页
                if (targetToLastPage) {
                    val chapter = _uiState.value.currentChapter ?: return@paginateChapterStreaming
                    val lastIdx = chapter.lastIndex
                    if (lastIdx >= 0) {
                        _uiState.value = _uiState.value.copy(
                            pageIndex = lastIdx,
                            currentPage = chapter.getPage(lastIdx),
                        )
                    }
                }
                saveReadingProgress(immediate = true)
                paginationCoordinator.preloadAdjacentChapters(content, safeIndex)
                // TTS 跨章连续播放：章节加载完成后从首页继续朗读
                ttsManager.onResumeAfterChapterLoad()
            },
        )
    }

    /**
     * 更新阅读主题
     */
    fun setReaderTheme(theme: ReaderTheme) {
        val currentPrefs = _uiState.value.readerPreferences
        val newPrefs = currentPrefs.copy(backgroundColor = theme)
        _uiState.value = _uiState.value.copy(
            readerPreferences = newPrefs,
            themeColors = theme.toReaderColorScheme().toCanvasThemeColors(),
        )
    }

    /**
     * 循环切换主题：LIGHT → DARK → PAPER → LIGHT
     */
    fun cycleTheme() {
        val current = _uiState.value.readerPreferences.backgroundColor
        val next = when (current) {
            ReaderTheme.LIGHT -> ReaderTheme.DARK
            ReaderTheme.DARK -> ReaderTheme.PAPER
            ReaderTheme.PAPER -> ReaderTheme.LIGHT
            ReaderTheme.OLED -> ReaderTheme.PAPER
        }
        setReaderTheme(next)
    }

    /**
     * 更新字号
     */
    fun setFontSize(size: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(fontSize = size) }, { it.setDefaultFontSize(size) }, reflow = true)
    }

    /**
     * 更新行距
     */
    fun setLineSpacing(spacing: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(lineSpacing = spacing) }, { it.setDefaultLineSpacing(spacing) }, reflow = true)
    }

    /**
     * 更新亮度
     */
    fun setBrightness(brightness: Float, finished: Boolean = false) {
        resetToolbarAutoHide()
        updatePrefs(
            { it.copy(brightness = brightness) },
            { if (finished) it.setBrightness(brightness) },
        )
    }

    /**
     * 更新段距
     */
    fun setParagraphSpacing(spacing: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(paragraphSpacing = spacing) }, { it.setDefaultParagraphSpacing(spacing) }, reflow = true)
    }

    /**
     * 更新首行缩进
     */
    fun setIndent(indent: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(indent = indent) }, { it.setDefaultIndent(indent) }, reflow = true)
    }

    /**
     * 更新左右边距
     */
    fun setMarginHorizontal(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(marginHorizontal = margin) }, { it.setMarginHorizontal(margin) }, reflow = true)
    }

    /**
     * 更新上下边距
     */
    fun setMarginVertical(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(marginVertical = margin) }, { it.setMarginVertical(margin) }, reflow = true)
    }

    /**
     * 更新页眉顶距
     */
    fun setHeaderMarginTop(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs(
            { it.copy(header = it.header.copy(marginTop = margin)) },
            { it.setHeaderMarginTop(margin) },
        )
    }

    /**
     * 更新页脚底距
     */
    fun setFooterMarginBottom(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs(
            { it.copy(footer = it.footer.copy(marginBottom = margin)) },
            { it.setFooterMarginBottom(margin) },
        )
    }

    /**
     * 更新阅读字体
     */
    fun setReadingFont(font: String) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(readingFont = font) }, { it.setReadingFont(font) })
    }

    /**
     * 更新字距
     */
    fun setLetterSpacing(spacing: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(letterSpacing = spacing) }, { it.setLetterSpacing(spacing) }, reflow = true)
    }

    /**
     * 更新字重（FakeBold 不改变字宽，无需 reflow）
     */
    fun setFontWeight(weight: ReaderFontWeight) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(fontWeight = weight) }, { it.setFontWeight(weight.toStorageString()) }, invalidate = true)
    }

    fun setTtsSpeed(speed: Float) {
        updatePrefs({ it.copy(ttsSpeed = speed) }, { it.setTtsSpeed(speed) })
    }

    fun setTtsPitch(pitch: Float) {
        updatePrefs({ it.copy(ttsPitch = pitch) }, { it.setTtsPitch(pitch) })
    }

    /**
     * 更新对齐方式（对齐不改变每行字符数，无需 reflow）
     */
    fun setTextAlign(align: ReaderTextAlign) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(textAlign = align) }, { it.setTextAlign(align.toStorageString()) }, invalidate = true)
    }

    /**
     * 更新简繁转换
     */
    fun setChineseConvert(convert: ChineseConvert) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(chineseConvert = convert) }, { it.setChineseConvert(convert.toStorageString()) }, reflow = true)
    }

    /**
     * 更新中文分行模式（改变断行位置，需要 reflow）
     */
    fun setUseZhLayout(enabled: Boolean) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(useZhLayout = enabled) }, { it.setUseZhLayout(enabled) }, reflow = true)
    }

    /**
     * 更新盘古之白（中英文间加空格，需要 reflow）
     */
    fun setPanguSpacing(enabled: Boolean) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(usePanguSpacing = enabled) }, { it.setUsePanguSpacing(enabled) }, reflow = true)
    }

    // ── 页眉脚设置 ──────────────────────────────────────────────

    fun setHeaderVisibility(visibility: HeaderVisibility) {
        updatePrefs(
            { it.copy(header = it.header.copy(visibility = visibility)) },
            { it.setHeaderVisibility(visibility.toStorageString()) },
            reflow = true,
        )
    }

    fun setHeaderLeft(slot: SlotContent) {
        updatePrefs(
            { it.copy(header = it.header.copy(left = slot)) },
            { it.setHeaderLeft(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setHeaderCenter(slot: SlotContent) {
        updatePrefs(
            { it.copy(header = it.header.copy(center = slot)) },
            { it.setHeaderCenter(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setHeaderRight(slot: SlotContent) {
        updatePrefs(
            { it.copy(header = it.header.copy(right = slot)) },
            { it.setHeaderRight(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setFooterVisibility(visibility: HeaderVisibility) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(visibility = visibility)) },
            { it.setFooterVisibility(visibility.toStorageString()) },
            reflow = true,
        )
    }

    fun setFooterLeft(slot: SlotContent) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(left = slot)) },
            { it.setFooterLeft(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setFooterCenter(slot: SlotContent) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(center = slot)) },
            { it.setFooterCenter(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setFooterRight(slot: SlotContent) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(right = slot)) },
            { it.setFooterRight(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setHeaderFooterAlpha(alpha: Float) {
        updatePrefs({ it.copy(headerFooterAlpha = alpha) }, { it.setHeaderFooterAlpha(alpha) }, invalidate = true)
    }

    fun setShowProgress(show: Boolean) {
        updatePrefs({ it.copy(showProgress = show) }, { it.setShowProgress(show) }, invalidate = true)
    }

    // ── 正文标题样式（章首页标题）──────────────────────────────

    /** 标题对齐：LEFT / CENTER / HIDDEN。改变后影响 titleAreaHeight，需重排 */
    fun setTitleAlign(align: TitleAlign) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(align = align)) },
            { it.setTitleAlign(align.toStorageString()) },
            reflow = true,
        )
    }

    /** 标题字号偏移（相对正文字号，sp）。改变后影响 titleAreaHeight，需重排 */
    fun setTitleSizeOffset(offsetSp: Int) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(sizeOffsetSp = offsetSp)) },
            { it.setTitleSizeOffset(offsetSp) },
            reflow = true,
        )
    }

    /** 标题上距（dp）。影响 titleAreaHeight，需重排 */
    fun setTitleMarginTop(dp: Float) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(marginTopDp = dp)) },
            { it.setTitleMarginTop(dp) },
            reflow = true,
        )
    }

    /** 标题下距（dp）。影响 titleAreaHeight，需重排 */
    fun setTitleMarginBottom(dp: Float) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(marginBottomDp = dp)) },
            { it.setTitleMarginBottom(dp) },
            reflow = true,
        )
    }

    /** 使当前页 recorder 失效并触发重绘（页眉脚/进度条变化时使用） */
    private fun currentPageInvalidate() {
        _uiState.value.currentPage?.invalidate()
    }

    // ── 偏好设置通用更新辅助 ──────────────────────────────────────

    /** 更新 ReaderPreferences 并同步持久化，需要 reflow 时触发重排 */
    private fun updatePrefs(
        transform: (ReaderPreferences) -> ReaderPreferences,
        save: suspend (UserPreferences) -> Unit,
        reflow: Boolean = false,
        invalidate: Boolean = false,
    ) {
        val updated = transform(_uiState.value.readerPreferences)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updated,
            isReflowing = reflow,
        )
        if (reflow) reflowCurrentChapter(updated)
        if (invalidate) currentPageInvalidate()
        viewModelScope.launch { userPreferences?.let { save(it) } }
    }

    // ── 阶段六：杂项设置 ──────────────────────────────────────

    fun setKeepScreenOn(enabled: Boolean) {
        updatePrefs({ it.copy(keepScreenOn = enabled) }, { it.setKeepScreenOn(enabled) })
    }

    fun setVolumeKeyTurnPage(enabled: Boolean) {
        updatePrefs({ it.copy(volumeKeyTurnPage = enabled) }, { it.setVolumeKeyTurnPage(enabled) })
    }

    fun setEdgeTurnPage(enabled: Boolean) {
        updatePrefs({ it.copy(edgeTurnPage = enabled) }, { it.setEdgeTurnPage(enabled) })
    }

    fun setEdgeWidthPercent(percent: Float) {
        updatePrefs({ it.copy(edgeWidthPercent = percent) }, { it.setEdgeWidthPercent(percent) })
    }

    // ── 页眉页脚增强 ──────────────────────────────────────────

    fun setShowHeaderLine(show: Boolean) {
        updatePrefs({ it.copy(showHeaderLine = show) }, { it.setShowHeaderLine(show) }, invalidate = true)
    }

    fun setShowFooterLine(show: Boolean) {
        updatePrefs({ it.copy(showFooterLine = show) }, { it.setShowFooterLine(show) }, invalidate = true)
    }

    fun setHeaderFontSizeRatio(ratio: Float) {
        updatePrefs({ it.copy(headerFontSizeRatio = ratio) }, { it.setHeaderFontSizeRatio(ratio) }, invalidate = true)
    }

    fun setFooterFontSizeRatio(ratio: Float) {
        updatePrefs({ it.copy(footerFontSizeRatio = ratio) }, { it.setFooterFontSizeRatio(ratio) }, invalidate = true)
    }

    // ── 底部对齐 ──────────────────────────────────────────────

    fun setBottomJustify(enabled: Boolean) {
        updatePrefs({ it.copy(bottomJustify = enabled) }, { it.setBottomJustify(enabled) }, reflow = true)
    }

    /**
     * 显示/隐藏工具栏
     */
    fun toggleToolbar() {
        toolbarAutoHideJob?.cancel()
        val showing = !_uiState.value.showToolbar
        _uiState.value = _uiState.value.copy(
            showToolbar = showing,
            overlayPanel = OverlayPanel.NONE,
        )
        if (showing) {
            startToolbarAutoHide()
        }
    }

    /**
     * 启动工具栏自动隐藏计时器
     */
    private fun startToolbarAutoHide() {
        toolbarAutoHideJob?.cancel()
        toolbarAutoHideJob = viewModelScope.launch {
            delay(TOOLBAR_AUTO_HIDE_DELAY_MS)
            _uiState.value = _uiState.value.copy(showToolbar = false)
        }
    }

    /**
     * 重置工具栏自动隐藏计时器（用户与工具栏 UI 交互时调用）
     */
    fun resetToolbarAutoHide() {
        if (_uiState.value.showToolbar) {
            startToolbarAutoHide()
        }
    }

    /**
     * 显示/隐藏目录
     */
    fun toggleDirectory() {
        toggleOverlay(OverlayPanel.DIRECTORY)
    }

    /**
     * 显示/隐藏快速设置
     */
    fun toggleQuickSettings() {
        toggleOverlay(OverlayPanel.QUICK_SETTINGS)
    }

    private fun toggleOverlay(panel: OverlayPanel) {
        resetToolbarAutoHide()
        val current = _uiState.value.overlayPanel
        _uiState.value = _uiState.value.copy(
            overlayPanel = if (current == panel) OverlayPanel.NONE else panel
        )
    }

    /**
     * 显示/隐藏菜单
     */
    fun toggleMenu() {
        _uiState.value = _uiState.value.copy(showMenu = !_uiState.value.showMenu)
    }

    /**
     * 显示/隐藏搜索
     */
    fun toggleSearch() {
        val showing = !_uiState.value.showSearch
        _uiState.value = _uiState.value.copy(
            showSearch = showing,
            overlayPanel = OverlayPanel.NONE,
        )
        if (!showing) {
            clearSearchResults()
        }
    }

    /**
     * 设置翻页动画类型
     */
    fun setPageAnimType(type: PageDelegateFactory.PageAnimType) {
        _uiState.value = _uiState.value.copy(pageAnimType = type)
        pageDelegate = PageDelegateFactory.create(type)
    }

    /**
     * 处理翻页方向
     */
    fun handlePageDirection(direction: PageDelegate.Direction) {
        when (direction) {
            PageDelegate.Direction.NEXT -> nextPage()
            PageDelegate.Direction.PREV -> prevPage()
            PageDelegate.Direction.NONE -> { /* 忽略 */ }
        }
    }

    fun selectText(range: SelectionRange) {
        _uiState.value = _uiState.value.copy(selectedRange = range)
        // 选区变化只需重绘整页（选区矩形在 page canvas 上绘制，不在 line recorder 里）
        // line 文字内容未变，line recorder 缓存仍然有效
        _uiState.value.currentPage?.invalidate()
    }

    fun clearTextSelection() {
        _uiState.value = _uiState.value.copy(selectedRange = null)
        _uiState.value.currentPage?.invalidate()
    }

    fun addBookmarkFromSelection() {
        notesManager.addBookmarkFromSelection()
    }

    fun addNoteFromSelection() {
        notesManager.addNoteFromSelection()
    }

    // ── TTS 朗读（委托给 ttsManager）──────────────────────────────

    fun startTts(config: TtsConfig = TtsConfig()) = ttsManager.startTts(config)
    fun pauseTts() = ttsManager.pauseTts()
    fun resumeTts() = ttsManager.resumeTts()
    fun stopTts() = ttsManager.stopTts()
    fun startSleepTimer(minutes: Int) = ttsManager.startSleepTimer(minutes)
    fun cancelSleepTimer() = ttsManager.cancelSleepTimer()
    fun pauseTtsOnBackground() = ttsManager.pauseTtsOnBackground()

    /** R7: 暂停阅读会话（进入后台、锁屏） */
    fun pauseReadingSession() = sessionManager.pauseReadingSession()

    /** R7: 恢复阅读会话（回到前台） */
    fun resumeReadingSession() = sessionManager.resumeReadingSession()

    fun releaseReaderResources() {
        // R7: 立即保存进度并结束阅读会话
        sessionManager.saveReadingProgress(immediate = true)
        val sessionElapsed = sessionManager.endSession()
        sessionManager.persistReadingTime(_uiState.value.bookId, sessionElapsed)
        sessionManager.release()
        chapterProvider.cancel()
        // 缓存保留在 BookCacheStore 中，短时间返回可复用

        toolbarAutoHideJob?.cancel()
        toolbarAutoHideJob = null
        ttsManager.release()
        notesManager.release()
        presetManager.release()
    }

    // ── 正文搜索 ──────────────────────────────────────────────

    fun searchInCurrentBook(query: String) {
        val repository = bookRepository
        val bookId = _uiState.value.bookId
        if (repository == null || bookId == 0L || query.isBlank()) {
            clearSearchResults(query)
            return
        }

        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                repository.searchInBook(bookId, query)
            }
            setSearchResults(query, results)
        }
    }

    fun setSearchResults(query: String, results: List<SearchResult>) {
        val nextIndex = if (results.isEmpty()) -1 else 0
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchResults = results,
            currentSearchResultIndex = nextIndex,
        )
        results.firstOrNull()?.let(::navigateToSearchResult)
    }

    fun goToNextSearchResult() {
        val state = _uiState.value
        if (state.searchResults.isEmpty()) return

        val nextIndex = if (state.currentSearchResultIndex < state.searchResults.lastIndex) {
            state.currentSearchResultIndex + 1
        } else {
            0
        }
        _uiState.value = state.copy(currentSearchResultIndex = nextIndex)
        navigateToSearchResult(state.searchResults[nextIndex])
    }

    fun goToPreviousSearchResult() {
        val state = _uiState.value
        if (state.searchResults.isEmpty()) return

        val previousIndex = if (state.currentSearchResultIndex > 0) {
            state.currentSearchResultIndex - 1
        } else {
            state.searchResults.lastIndex
        }
        _uiState.value = state.copy(currentSearchResultIndex = previousIndex)
        navigateToSearchResult(state.searchResults[previousIndex])
    }

    private fun clearSearchResults(query: String = "") {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchResults = emptyList(),
            currentSearchResultIndex = -1,
        )
    }

    private fun navigateToSearchResult(result: SearchResult) {
        jumpToChapterPosition(result.chapterIndex, result.byteOffset)
    }

    // ── 书签管理（委托给 notesManager）────────────────────────────

    fun addBookmark(selectedText: String? = null) = notesManager.addBookmark(selectedText)
    fun addBookmark(range: SelectionRange) = notesManager.addBookmark(range)
    fun deleteBookmark(bookmark: BookmarkEntity) = notesManager.deleteBookmark(bookmark)
    fun goToBookmark(bookmark: BookmarkEntity) = notesManager.goToBookmark(bookmark)
    fun loadBookmarks() = notesManager.loadBookmarks()

    // ── 笔记管理（委托给 notesManager）────────────────────────────

    fun addNote(startPos: Int, endPos: Int, content: String, color: String? = null) = notesManager.addNote(startPos, endPos, content, color)
    fun addNote(range: SelectionRange, content: String, color: String? = null) = notesManager.addNote(range, content, color)
    fun deleteNote(note: NoteEntity) = notesManager.deleteNote(note)
    fun updateNote(note: NoteEntity, newText: String, newColor: String? = note.color) = notesManager.updateNote(note, newText, newColor)
    fun goToNote(note: NoteEntity) = notesManager.goToNote(note)
    fun loadNotes() = notesManager.loadNotes()
    fun getVisibleNoteRanges(): List<Pair<SelectionRange, String?>> = notesManager.getVisibleNoteRanges()
    fun exportNotesAsMarkdown(): String? = notesManager.exportNotesAsMarkdown()

    private fun sentenceRangesForCurrentPage(): List<SelectionRange> {
        val page = _uiState.value.currentPage ?: return emptyList()
        val content = _uiState.value.currentChapter?.content ?: return emptyList()
        if (page.lines.isEmpty()) return emptyList()

        // 合并所有行文本，用换行符连接，保持原始偏移
        val fullText = StringBuilder()
        val lineOffsets = mutableListOf<Int>() // 每行在 fullText 中的起始位置
        for (line in page.lines) {
            lineOffsets.add(fullText.length)
            fullText.append(content, line.startCharOffset, line.endCharOffset)
            if (!line.isParagraphEnd) {
                fullText.append('\n')
            }
        }

        // 对合并后的文本按句子拆分
        val ranges = mutableListOf<SelectionRange>()
        var start = 0
        val text = fullText.toString()
        text.forEachIndexed { index, char ->
            if (char.isSentenceTerminator()) {
                val sentenceText = text.substring(start, index + 1).trim()
                if (sentenceText.isNotBlank()) {
                    // 找到句子起始位置在原文中的偏移
                    val firstLineIndex = lineOffsets.indexOfLast { it <= start }
                    val line = page.lines.getOrNull(firstLineIndex)
                    if (line != null) {
                        ranges += SelectionRange(
                            chapterIndex = page.chapterIndex,
                            startPos = line.startCharOffset + (start - lineOffsets[firstLineIndex]),
                            endPos = line.startCharOffset + (index + 1 - lineOffsets[firstLineIndex]),
                            selectedText = sentenceText,
                        )
                    }
                }
                start = index + 1
            }
        }
        // 处理最后一段（句尾无终止符）
        if (start < text.length) {
            val sentenceText = text.substring(start).trim()
            if (sentenceText.isNotBlank()) {
                val firstLineIndex = lineOffsets.indexOfLast { it <= start }
                val line = page.lines.getOrNull(firstLineIndex)
                if (line != null) {
                    ranges += SelectionRange(
                        chapterIndex = page.chapterIndex,
                        startPos = line.startCharOffset + (start - lineOffsets[firstLineIndex]),
                        endPos = line.startCharOffset + (text.length - lineOffsets[firstLineIndex]),
                        selectedText = sentenceText,
                    )
                }
            }
        }
        return ranges
    }

    private fun Char.isSentenceTerminator(): Boolean {
        return this == '.' ||
            this == '!' ||
            this == '?' ||
            this == '\u3002' ||  // 。
            this == '\uff01' ||  // ！
            this == '\uff1f' ||  // ？
            this == '\u2026'     // …
    }

    private fun openFallbackBook(bookId: Long) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            bookTitle = "Book $bookId",
            chapterTitle = "Chapter 1",
            totalChapters = 10,
            chapterTitles = (1..10).map { "Chapter $it" },
            chapterWordCounts = (1..10).map { (it * 1500) + 500 },
        )
    }

    private fun openFallbackChapter(index: Int) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            chapterIndex = index,
            chapterTitle = "Chapter ${index + 1}",
            pageIndex = 0,
        )
    }




    private fun reflowCurrentChapter(preferences: ReaderPreferences) {
        currentLayoutHash = paginationCoordinator.computeLayoutHash(preferences)
        paginationCoordinator.reflowCurrentChapter(preferences, currentLayoutHash)
    }

    // ── 预设管理（委托 presetManager）──────────────────────────

    fun loadPresets() = presetManager.loadPresets()
    fun saveCurrentAsPreset(name: String) = presetManager.saveCurrentAsPreset(name)
    fun applyPreset(presetId: Long) = presetManager.applyPreset(presetId)
    fun renamePreset(presetId: Long, newName: String) = presetManager.renamePreset(presetId, newName)
    fun deletePreset(presetId: Long) = presetManager.deletePreset(presetId)
    fun resetToDefault() = presetManager.resetToDefault()

    /** 将一组偏好依次通过 setter 应用（由 presetManager.onApplyPreferences 回调） */
    private fun applyAllPreferences(prefs: ReaderPreferences) {
        setFontSize(prefs.fontSize)
        setLineSpacing(prefs.lineSpacing)
        setParagraphSpacing(prefs.paragraphSpacing)
        setIndent(prefs.indent)
        setMarginHorizontal(prefs.marginHorizontal)
        setMarginVertical(prefs.marginVertical)
        setReadingFont(prefs.readingFont)
        setPageAnimType(prefs.pageAnimType.toFactoryType())
        setReaderTheme(prefs.backgroundColor)
        setLetterSpacing(prefs.letterSpacing)
        setFontWeight(prefs.fontWeight)
        setTextAlign(prefs.textAlign)
        setChineseConvert(prefs.chineseConvert)
        setBrightness(prefs.brightness)
        setHeaderVisibility(prefs.header.visibility)
        setHeaderLeft(prefs.header.left)
        setHeaderCenter(prefs.header.center)
        setHeaderRight(prefs.header.right)
        setFooterVisibility(prefs.footer.visibility)
        setFooterLeft(prefs.footer.left)
        setFooterCenter(prefs.footer.center)
        setFooterRight(prefs.footer.right)
        setHeaderFooterAlpha(prefs.headerFooterAlpha)
        setShowProgress(prefs.showProgress)
        setTitleAlign(prefs.titleStyle.align)
        setTitleSizeOffset(prefs.titleStyle.sizeOffsetSp)
        setTitleMarginTop(prefs.titleStyle.marginTopDp)
        setTitleMarginBottom(prefs.titleStyle.marginBottomDp)
        setKeepScreenOn(prefs.keepScreenOn)
        setVolumeKeyTurnPage(prefs.volumeKeyTurnPage)
        setEdgeTurnPage(prefs.edgeTurnPage)
        setEdgeWidthPercent(prefs.edgeWidthPercent)
        setShowHeaderLine(prefs.showHeaderLine)
        setShowFooterLine(prefs.showFooterLine)
        setHeaderFontSizeRatio(prefs.headerFontSizeRatio)
        setFooterFontSizeRatio(prefs.footerFontSizeRatio)
        setBottomJustify(prefs.bottomJustify)
    }

    private fun computeSynchronousBookProgress(): Triple<Long, Long, Float> {
        val state = _uiState.value
        val wordCounts = state.chapterWordCounts
        val pageCounts = state.chapterPageCounts
        val currentChapterIndex = state.chapterIndex
        val currentPages = state.totalPages.coerceAtLeast(1)
        val totalChapters = state.totalChapters.coerceAtLeast(1)

        // 加权平均：基于所有已分页章节的真实数据计算 wordsPerPage
        var sampledWords = 0L
        var sampledPages = 0L
        for ((i, p) in pageCounts) {
            if (p > 0) {
                sampledWords += (wordCounts.getOrNull(i) ?: 0).toLong()
                sampledPages += p.toLong()
            }
        }
        // 当前章节也纳入样本（它一定有真实页数）
        val currentChapterWords = wordCounts.getOrNull(currentChapterIndex)?.coerceAtLeast(1) ?: 1
        if (sampledPages == 0L) sampledPages = currentPages.toLong()
        if (sampledWords == 0L) sampledWords = currentChapterWords.toLong()
        val wordsPerPage = sampledWords.toDouble() / sampledPages

        // Fallback：完全无数据时降级为章节索引进度，分数槽位仍显示章节数
        if (wordCounts.isEmpty() && pageCounts.isEmpty()) {
            val progress = ((currentChapterIndex + state.pageIndex.toFloat() / currentPages) / totalChapters).coerceIn(0f, 1f)
            return Triple((currentChapterIndex + 1).toLong(), totalChapters.toLong(), progress)
        }

        // 计算当前页在全书中的位置
        var pagesBeforeCurrent = 0L
        for (i in 0 until currentChapterIndex) {
            val realCount = pageCounts[i]
            if (realCount != null) {
                pagesBeforeCurrent += realCount.toLong()
            } else {
                val words = wordCounts.getOrNull(i)?.toLong() ?: 0L
                pagesBeforeCurrent += (words / wordsPerPage).toLong()
            }
        }
        val currentBookPage = pagesBeforeCurrent + state.pageIndex + 1

        // 计算全书总页数
        var totalBookPages = pagesBeforeCurrent + currentPages
        for (i in (currentChapterIndex + 1) until totalChapters) {
            val realCount = pageCounts[i]
            if (realCount != null) {
                totalBookPages += realCount.toLong()
            } else {
                val words = wordCounts.getOrNull(i)?.toLong() ?: 0L
                totalBookPages += (words / wordsPerPage).toLong()
            }
        }
        totalBookPages = totalBookPages.coerceAtLeast(currentBookPage)

        val progress = if (totalBookPages > 0) currentBookPage.toFloat() / totalBookPages else 0f
        return Triple(currentBookPage, totalBookPages, progress.coerceIn(0f, 1f))
    }

    /** 统一解析页眉和页脚槽位为 SlotResolution，避免重复计算进度 */
    fun resolveHeaderAndFooterSlots(): Pair<SlotResolution, SlotResolution> {
        val state = _uiState.value
        val prefs = state.readerPreferences
        val (currentPos, totalPos, bookProgressPercent) = computeSynchronousBookProgress()

        val header = if (prefs.header.visibility == HeaderVisibility.ALWAYS_HIDE) {
            SlotResolution()
        } else {
            SlotResolver.resolveHeader(
                config = prefs.header,
                chapterTitle = state.chapterTitle,
                bookTitle = state.bookTitle,
                pageNumber = state.pageIndex + 1,
                totalPages = state.totalPages.coerceAtLeast(1),
                bookProgressPercent = bookProgressPercent,
                bookCurrentPosition = currentPos,
                bookTotalPosition = totalPos,
                batteryLevel = 100, // 实际上在 ReaderScreen 中会调用 updateHeaderFooter 覆盖
            )
        }

        val footer = if (prefs.footer.visibility == HeaderVisibility.ALWAYS_HIDE) {
            SlotResolution()
        } else {
            SlotResolver.resolveFooter(
                config = prefs.footer,
                chapterTitle = state.chapterTitle,
                bookTitle = state.bookTitle,
                pageNumber = state.pageIndex + 1,
                totalPages = state.totalPages.coerceAtLeast(1),
                bookProgressPercent = bookProgressPercent,
                bookCurrentPosition = currentPos,
                bookTotalPosition = totalPos,
                batteryLevel = 100, // 实际上在 ReaderScreen 中会调用 updateHeaderFooter 覆盖
            )
        }
        
        return Pair(header, footer)
    }

    override fun onCleared() {
        releaseReaderResources()
        super.onCleared()
    }
}
