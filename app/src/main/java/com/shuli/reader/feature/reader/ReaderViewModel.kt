package com.shuli.reader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.text.ChineseConverter
import com.shuli.reader.core.text.PanguSpacing
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
import com.shuli.reader.feature.reader.progress.normalizedChapters
import com.shuli.reader.core.parser.DecodedSegment
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import com.shuli.reader.core.reader.ChapterProvider
import com.shuli.reader.core.reader.Paginator
import com.shuli.reader.core.reader.ReadingStateManager
import com.shuli.reader.core.reader.AndroidTextMeasurer
import com.shuli.reader.core.reader.SimpleTextMeasurer
import com.shuli.reader.core.reader.animation.PageDelegate
import com.shuli.reader.core.reader.animation.PageDelegateFactory
import com.shuli.reader.core.reader.cache.CacheManager
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.core.repository.BookRepository
import com.shuli.reader.core.repository.SearchResult
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

    // reflow 防抖 Job
    private var reflowJob: Job? = null

    // 书签/笔记加载 Job（R11: 防止多次 openBook 堆积 Job）
    private var bookmarksJob: Job? = null
    private var notesJob: Job? = null

    // M1: 章节加载 Job（防止快速连续调用导致多个 openChapter 并发）
    private var chapterJob: Job? = null

    // R7: 章节缓存管理器（从 BookCacheStore 获取，跨 ViewModel 生命周期复用）
    private var cacheManager: CacheManager = CacheManager.forMemoryClass(256)

    // R7: 章节提供器（预加载相邻章节）
    private val chapterProvider = ChapterProvider(paginator)

    // R7: 阅读状态管理器（进度持久化 + 会话时长）
    private lateinit var readingStateManager: ReadingStateManager

    // 搜索管理器（从本类抽出，避免 ReaderViewModel 继续膨胀）
    private lateinit var textSearchManager: com.shuli.reader.feature.reader.search.TextSearchManager

    // 进度计算 + 页眉页脚解析 + 页数持久化（从本类抽出）
    private lateinit var progressTracker: com.shuli.reader.feature.reader.progress.ReadingProgressTracker

    // 偏好设置统一入口（从本类抽出）
    private lateinit var prefsBridge: com.shuli.reader.feature.reader.prefs.ReaderPreferencesBridge

    init {
        readingStateManager = ReadingStateManager(
            scope = viewModelScope,
            saveAction = { state ->
                // v4: ReadingStateManager 内部仍用 charPos，实际 byteOffset 由 saveReadingProgress 直接写入
                // 此处仅做兜底，正常路径不会走到这里
            },
        )
        textSearchManager = com.shuli.reader.feature.reader.search.TextSearchManager(
            bookRepository = bookRepository,
            uiState = _uiState,
            scope = viewModelScope,
            jumpTo = { chapterIndex, byteOffset ->
                jumpToChapterPosition(chapterIndex, byteOffset)
            },
        )
        progressTracker = com.shuli.reader.feature.reader.progress.ReadingProgressTracker(
            uiState = _uiState,
            scope = viewModelScope,
            appContext = { appContext },
            densityProvider = { density },
            screenSizeProvider = { screenWidthPx to screenHeightPx },
        )
        prefsBridge = com.shuli.reader.feature.reader.prefs.ReaderPreferencesBridge(
            uiState = _uiState,
            scope = viewModelScope,
            userPreferences = userPreferences,
            reflow = { updated -> reflowCurrentChapter(updated) },
            invalidate = { _uiState.value.currentPage?.invalidate() },
            resetToolbarAutoHide = { resetToolbarAutoHide() },
            fontOps = object : com.shuli.reader.feature.reader.prefs.ReaderPreferencesBridge.FontOps {
                override fun loadCustomFonts() = this@ReaderViewModel.loadCustomFonts()
                override fun importFont(uri: android.net.Uri, displayName: String?) =
                    this@ReaderViewModel.importFont(uri, displayName)
                override fun deleteFont(fontId: String) = this@ReaderViewModel.deleteFont(fontId)
            },
        )
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
    /** 当前章节的 utf16IndexToByte 映射表（v4：字节↔字符坐标桥接） */
    private var currentChapterUtf16Map: IntArray = IntArray(0)
    /** 缓存当前章节已解码文本，避免 loadChapterContent + paginateChapterStreaming 重复读文件 */
    private var cachedChapterText: String? = null
    private val ttsController = ttsEngine?.let { engine ->
        TtsController(
            engine = engine,
            onUtteranceCompleted = ::handleTtsUtteranceCompleted,
        )
    }
    private var activeTtsConfig = TtsConfig()
    private var ttsSentences: List<SelectionRange> = emptyList()
    private var ttsSentenceIndex: Int = -1
    private var ttsPendingResume: Boolean = false
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

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
            reflowCurrentChapter(_uiState.value.readerPreferences)
        }
    }

    fun setScreenSize(widthPx: Int, heightPx: Int) {
        if (screenWidthPx == widthPx && screenHeightPx == heightPx) return
        screenWidthPx = widthPx
        screenHeightPx = heightPx
        reflowCurrentChapter(_uiState.value.readerPreferences)
    }

    // ── 进度估算（v4） ──────────────────────────────────────────

    /**
     * TXT 进度估算：扫描完成 → 估算字符%；扫描未完成 → 字节%。
     * 内部存 byte%（精确），UI 显示用估算字符%（编码无关，用户感知一致）。
     */
    private fun computeDisplayProgress(byteOffset: Long, book: BookEntity?): Float {
        val fileSize = book?.fileSize?.coerceAtLeast(1L) ?: return 0f
        val estimatedTotalChars = book.estimatedTotalChars
        if (estimatedTotalChars <= 0L) {
            // 章节扫描未完成 → 退化为字节%
            return (byteOffset.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f)
        }
        // 用平均 bytesPerChar 估算字符位置
        val avgBpc = fileSize.toFloat() / estimatedTotalChars.toFloat()
        val estimatedCharPos = byteOffset.toFloat() / avgBpc
        return (estimatedCharPos / estimatedTotalChars.toFloat()).coerceIn(0f, 1f)
    }

    // ── 字节↔字符坐标桥接（v4） ──────────────────────────────────

    /** UTF-16 char index → 章节内相对字节偏移（O(n) 线性扫描，映射表已排序） */
    private fun charToByteOffset(charIndex: Int): Int {
        val map = currentChapterUtf16Map
        if (map.isEmpty()) return charIndex // 兜底：无映射时 1:1
        val idx = charIndex.coerceIn(0, map.size - 1)
        return map[idx]
    }

    /** 章节内相对字节偏移 → UTF-16 char index（O(n) 线性扫描） */
    private fun byteToCharOffset(byteOffset: Int): Int {
        val map = currentChapterUtf16Map
        if (map.isEmpty()) return byteOffset // 兜底：无映射时 1:1
        for (i in map.indices) {
            if (map[i] > byteOffset) return (i - 1).coerceAtLeast(0)
        }
        return map.size - 1
    }

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
        val sessionElapsed = readingStateManager.endSession()
        if (oldBookId != 0L && sessionElapsed > 0L) {
            persistReadingTime(oldBookId, sessionElapsed)
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
                currentChapterUtf16Map = segment?.utf16IndexToByte ?: IntArray(0)
                cachedChapterText = content.content.ifEmpty { null }
                logPerf("loadChapterContent", parseStart)

                withContext(Dispatchers.IO) {
                    repository.updateLastReadTime(bookId)
                }
                loadBookmarks()
                loadNotes()
                readingStateManager.startSession()

                logPerf("openBook.preparation", perfStart)

                // v4: 将 durByteOffset 转为章节内字符偏移，用于分页跳转
                val chapterByteStart = chapterIndexList[chapterIndex].byteStart
                val relativeByteOffset = (durByteOffset - chapterByteStart).toInt().coerceAtLeast(0)
                val targetCharOffset = byteToCharOffset(relativeByteOffset)

                // 4. 流式分页：首页秒开，目标位置自动跳转
                val paginateStart = System.currentTimeMillis()
                // 计算布局哈希，用于持久化 chapterPageCounts
                progressTracker.updateLayoutHash(_uiState.value.readerPreferences)
                chapterJob = paginateChapterStreaming(
                    content = content,
                    index = chapterIndex,
                    targetCharOffset = targetCharOffset,
                    onDone = {
                        logPerf("firstPageReady", perfStart)
                        preloadAdjacentChapters(content, chapterIndex)
                        // TXT: 从 DB 章节索引直接计算字数
                        if (!isCurrentBookEpub) {
                            computeChapterWordCounts(chapterIndexList)
                        }
                        // 加载已持久化的页数缓存（异步，不阻塞首屏）
                        progressTracker.loadPersistedAsync { persisted ->
                            _uiState.value = _uiState.value.copy(
                                chapterPageCounts = persisted + _uiState.value.chapterPageCounts,
                            )
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
        } else if (loadedBookContent != null && state.chapterIndex < loadedBookContent!!.normalizedChapters().size - 1) {
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
        saveReadingProgress(immediate = true)
    }

    private fun persistReadingTime(bookId: Long, elapsedMs: Long) {
        val dao = readingProgressDao ?: return
        if (bookId == 0L || elapsedMs < 1000L) return
        val elapsedSeconds = elapsedMs / 1000L
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getReadingDurationByBookId(bookId)
            if (existing != null) {
                dao.updateProgress(
                    bookId = bookId,
                    pageIndex = _uiState.value.pageIndex,
                    position = _uiState.value.currentPage?.startCharOffset ?: 0,
                    readTime = existing + elapsedSeconds,
                    updatedTime = System.currentTimeMillis(),
                )
            } else {
                dao.insertProgress(
                    com.shuli.reader.core.database.entity.ReadingProgressEntity(
                        bookId = bookId,
                        pageIndex = _uiState.value.pageIndex,
                        position = _uiState.value.currentPage?.startCharOffset ?: 0,
                        readTime = elapsedSeconds,
                        updatedTime = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    /**
     * R7: 保存阅读进度到数据库
     * @param immediate true 表示立即保存（翻章、退出），false 表示防抖保存（翻页）
     */
    private fun saveReadingProgress(immediate: Boolean) {
        val state = _uiState.value
        val page = state.currentPage ?: return
        val bookId = state.bookId
        if (bookId == 0L) return

        val chapterPos = page.startCharOffset
        val chapterTitle = state.chapterTitle

        // v4: ReadingStateManager 内部仍用 charPos 做防抖计时
        if (immediate) {
            readingStateManager.saveReadNow(bookId, state.chapterIndex, chapterPos, chapterTitle)
        } else {
            readingStateManager.saveReadDebounced(bookId, state.chapterIndex, chapterPos, chapterTitle)
        }

        // v4: 通过 utf16IndexToByte 映射将 charOffset 转为 byteOffset，写入 BookEntity
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository?.let { repo ->
                val chapters = loadedBookContent?.chapters ?: emptyList()
                val chapterByteStart = chapters.getOrNull(state.chapterIndex)?.byteStart ?: 0L
                val relativeByte = charToByteOffset(chapterPos)
                val absoluteByteOffset = chapterByteStart + relativeByte

                val progress = if (isCurrentBookEpub) {
                    val totalChapters = state.totalChapters.coerceAtLeast(1)
                    val chapterContentLength = state.currentChapter?.content?.length?.coerceAtLeast(1) ?: 1
                    val charOffsetRatio = (chapterPos.toFloat() / chapterContentLength).coerceIn(0f, 1f)
                    ((state.chapterIndex + charOffsetRatio) / totalChapters).coerceIn(0f, 1f)
                } else {
                    // TXT: 优先用 estimatedTotalChars 估算字符%，未完成退化为字节%
                    val book = repo.getBookById(bookId).first()
                    computeDisplayProgress(absoluteByteOffset, book)
                }

                repo.updateReadingPosition(
                    bookId = bookId,
                    byteOffset = absoluteByteOffset,
                    chapterTitle = chapterTitle,
                    progress = progress,
                )
            }
        }
    }

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
        android.util.Log.d(TAG, "openChapter[$index]: loadedBookContent 非 null，章节数=${content.normalizedChapters().size}")

        val safeIndex = index.coerceIn(0, (content.normalizedChapters().size - 1).coerceAtLeast(0))

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

        chapterJob = paginateChapterStreaming(
            content = content,
            index = safeIndex,
            targetCharOffset = targetCharOffset,
            onDone = {
                // v4: 如果有 targetByteOffset，加载完章节后用映射转为 charOffset 再跳转
                if (targetByteOffset >= 0 && !targetToLastPage) {
                    val chapters = content.normalizedChapters()
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
                preloadAdjacentChapters(content, safeIndex)
                // TTS 跨章连续播放：章节加载完成后从首页继续朗读
                if (ttsPendingResume) {
                    ttsPendingResume = false
                    ttsSentences = sentenceRangesForCurrentPage()
                    ttsSentenceIndex = 0
                    if (ttsSentences.isNotEmpty()) {
                        speakCurrentTtsSentence()
                    }
                }
            },
        )
    }

    /**
     * 更新阅读主题
     */
    fun setReaderTheme(theme: ReaderTheme) {
        prefsBridge.setReaderTheme(theme)
    }

    // ── 偏好设置（委托给 ReaderPreferencesBridge）────────────────

    fun cycleTheme() { prefsBridge.cycleTheme() }

    fun setFontSize(size: Float) { prefsBridge.setFontSize(size) }
    fun setLineSpacing(spacing: Float) { prefsBridge.setLineSpacing(spacing) }
    fun setBrightness(brightness: Float, finished: Boolean = false) { prefsBridge.setBrightness(brightness, finished) }
    fun setParagraphSpacing(spacing: Float) { prefsBridge.setParagraphSpacing(spacing) }
    fun setIndent(indent: Float) { prefsBridge.setIndent(indent) }
    fun setMarginHorizontal(margin: Float) { prefsBridge.setMarginHorizontal(margin) }
    fun setMarginVertical(margin: Float) { prefsBridge.setMarginVertical(margin) }
    fun setHeaderMarginTop(margin: Float) { prefsBridge.setHeaderMarginTop(margin) }
    fun setFooterMarginBottom(margin: Float) { prefsBridge.setFooterMarginBottom(margin) }
    fun setReadingFont(font: String) { prefsBridge.setReadingFont(font) }
    fun setLetterSpacing(spacing: Float) { prefsBridge.setLetterSpacing(spacing) }
    fun setFontWeight(weight: ReaderFontWeight) { prefsBridge.setFontWeight(weight) }
    fun setTtsSpeed(speed: Float) { prefsBridge.setTtsSpeed(speed) }
    fun setTtsPitch(pitch: Float) { prefsBridge.setTtsPitch(pitch) }
    fun setTextAlign(align: ReaderTextAlign) { prefsBridge.setTextAlign(align) }
    fun setChineseConvert(convert: ChineseConvert) { prefsBridge.setChineseConvert(convert) }
    fun setUseZhLayout(enabled: Boolean) { prefsBridge.setUseZhLayout(enabled) }
    fun setPanguSpacing(enabled: Boolean) { prefsBridge.setPanguSpacing(enabled) }
    fun setBottomJustify(enabled: Boolean) { prefsBridge.setBottomJustify(enabled) }

    fun setHeaderVisibility(visibility: HeaderVisibility) { prefsBridge.setHeaderVisibility(visibility) }
    fun setHeaderLeft(slot: SlotContent) { prefsBridge.setHeaderLeft(slot) }
    fun setHeaderCenter(slot: SlotContent) { prefsBridge.setHeaderCenter(slot) }
    fun setHeaderRight(slot: SlotContent) { prefsBridge.setHeaderRight(slot) }
    fun setFooterVisibility(visibility: HeaderVisibility) { prefsBridge.setFooterVisibility(visibility) }
    fun setFooterLeft(slot: SlotContent) { prefsBridge.setFooterLeft(slot) }
    fun setFooterCenter(slot: SlotContent) { prefsBridge.setFooterCenter(slot) }
    fun setFooterRight(slot: SlotContent) { prefsBridge.setFooterRight(slot) }
    fun setHeaderFooterAlpha(alpha: Float) { prefsBridge.setHeaderFooterAlpha(alpha) }
    fun setShowProgress(show: Boolean) { prefsBridge.setShowProgress(show) }
    fun setShowHeaderLine(show: Boolean) { prefsBridge.setShowHeaderLine(show) }
    fun setShowFooterLine(show: Boolean) { prefsBridge.setShowFooterLine(show) }
    fun setHeaderFontSizeRatio(ratio: Float) { prefsBridge.setHeaderFontSizeRatio(ratio) }
    fun setFooterFontSizeRatio(ratio: Float) { prefsBridge.setFooterFontSizeRatio(ratio) }

    fun setTitleAlign(align: TitleAlign) { prefsBridge.setTitleAlign(align) }
    fun setTitleSizeOffset(offsetSp: Int) { prefsBridge.setTitleSizeOffset(offsetSp) }
    fun setTitleMarginTop(dp: Float) { prefsBridge.setTitleMarginTop(dp) }
    fun setTitleMarginBottom(dp: Float) { prefsBridge.setTitleMarginBottom(dp) }

    fun setKeepScreenOn(enabled: Boolean) { prefsBridge.setKeepScreenOn(enabled) }
    fun setVolumeKeyTurnPage(enabled: Boolean) { prefsBridge.setVolumeKeyTurnPage(enabled) }
    fun setEdgeTurnPage(enabled: Boolean) { prefsBridge.setEdgeTurnPage(enabled) }
    fun setEdgeWidthPercent(percent: Float) { prefsBridge.setEdgeWidthPercent(percent) }

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
        val range = _uiState.value.selectedRange ?: return
        addBookmark(range)
        clearTextSelection()
    }

    fun addNoteFromSelection() {
        val range = _uiState.value.selectedRange ?: return
        val content = range.selectedText.orEmpty()
        if (content.isBlank()) return
        addNote(range, content)
        clearTextSelection()
    }

    // ── TTS 朗读 ──────────────────────────────────────────────

    fun startTts(config: TtsConfig = TtsConfig()) {
        val controller = ttsController ?: return
        activeTtsConfig = config
        controller.initialize(config.copy(autoPage = false))
        var sentences = sentenceRangesForCurrentPage()
        if (config.skipTitle && sentences.isNotEmpty()) {
            val title = _uiState.value.chapterTitle.trim()
            if (title.isNotBlank() && sentences.first().selectedText.orEmpty().trim() == title) {
                sentences = sentences.drop(1)
            }
        }
        ttsSentences = sentences
        ttsSentenceIndex = 0
        speakCurrentTtsSentence()
        startTtsService()
    }

    private fun startTtsService() {
        val ctx = appContext ?: return
        com.shuli.reader.core.tts.TtsService.onPlay = { resumeTts() }
        com.shuli.reader.core.tts.TtsService.onPause = { pauseTts() }
        com.shuli.reader.core.tts.TtsService.onStop = { stopTts() }
        com.shuli.reader.core.tts.TtsService.isPlaying = { _uiState.value.ttsState == TtsState.PLAYING }
        com.shuli.reader.core.tts.TtsService.currentTitle = { _uiState.value.bookTitle }
        com.shuli.reader.core.tts.TtsService.currentSubtitle = { _uiState.value.chapterTitle }
        val intent = android.content.Intent(ctx, com.shuli.reader.core.tts.TtsService::class.java)
        ctx.startForegroundService(intent)
    }

    fun pauseTts() {
        val controller = ttsController ?: return
        controller.pause()
        _uiState.value = _uiState.value.copy(ttsState = controller.state)
        updateTtsServiceNotification()
    }

    fun resumeTts() {
        val controller = ttsController ?: return
        controller.resume()
        _uiState.value = _uiState.value.copy(ttsState = controller.state)
        updateTtsServiceNotification()
    }

    private fun updateTtsServiceNotification() {
        val ctx = appContext ?: return
        val intent = android.content.Intent(ctx, com.shuli.reader.core.tts.TtsService::class.java)
        ctx.startForegroundService(intent)
    }

    fun stopTts() {
        cancelSleepTimer()
        val controller = ttsController ?: return
        controller.stop()
        ttsSentences = emptyList()
        ttsSentenceIndex = -1
        ttsPendingResume = false
        _uiState.value = _uiState.value.copy(
            ttsState = controller.state,
            ttsActiveRange = null,
        )
        stopTtsService()
    }

    private fun stopTtsService() {
        val ctx = appContext ?: return
        val intent = android.content.Intent(ctx, com.shuli.reader.core.tts.TtsService::class.java).apply {
            action = com.shuli.reader.core.tts.TtsService.ACTION_STOP
        }
        ctx.startService(intent)
        com.shuli.reader.core.tts.TtsService.onPlay = null
        com.shuli.reader.core.tts.TtsService.onPause = null
        com.shuli.reader.core.tts.TtsService.onStop = null
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        var remaining = minutes * 60
        _uiState.value = _uiState.value.copy(sleepTimerRemainingSeconds = remaining)
        sleepTimerJob = viewModelScope.launch {
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.value = _uiState.value.copy(sleepTimerRemainingSeconds = remaining)
            }
            stopTts()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.value = _uiState.value.copy(sleepTimerRemainingSeconds = -1)
    }

    fun pauseTtsOnBackground() {
        if (_uiState.value.ttsState == TtsState.PLAYING) {
            pauseTts()
        }
    }

    /** R7: 暂停阅读会话（进入后台、锁屏） */
    fun pauseReadingSession() {
        readingStateManager.pauseSession()
    }

    /** R7: 恢复阅读会话（回到前台） */
    fun resumeReadingSession() {
        readingStateManager.resumeSession()
    }

    fun releaseReaderResources() {
        // R7: 立即保存进度并结束阅读会话
        saveReadingProgress(immediate = true)
        val sessionElapsed = readingStateManager.endSession()
        persistReadingTime(_uiState.value.bookId, sessionElapsed)
        readingStateManager.cancel()
        chapterProvider.cancel()
        // 缓存保留在 BookCacheStore 中，短时间返回可复用

        toolbarAutoHideJob?.cancel()
        toolbarAutoHideJob = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        ttsPendingResume = false
        ttsController?.release()
        stopTtsService()
        ttsSentences = emptyList()
        ttsSentenceIndex = -1
        _uiState.value = _uiState.value.copy(
            ttsState = TtsState.IDLE,
            ttsActiveRange = null,
        )
    }

    // ── 正文搜索（委托给 TextSearchManager）──────────────────────

    fun searchInCurrentBook(query: String) = textSearchManager.searchInCurrentBook(query)

    fun setSearchResults(query: String, results: List<com.shuli.reader.core.repository.SearchResult>) =
        textSearchManager.setSearchResults(query, results)

    fun goToNextSearchResult() = textSearchManager.goToNextSearchResult()

    fun goToPreviousSearchResult() = textSearchManager.goToPreviousSearchResult()

    fun clearSearchResults(query: String = "") = textSearchManager.clearSearchResults(query)

    // ── 书签管理 ──────────────────────────────────────────────

    /**
     * 在当前位置添加书签（v4：使用字节偏移）
     */
    fun addBookmark(selectedText: String? = null) {
        val dao = bookmarkDao ?: return
        val state = _uiState.value
        if (state.bookId == 0L) return

        viewModelScope.launch {
            val page = state.currentPage
            val chapters = loadedBookContent?.chapters
            val chapterByteStart = chapters?.getOrNull(state.chapterIndex)?.byteStart ?: 0L
            val charOffset = page?.startCharOffset ?: 0
            val byteOffset = chapterByteStart + charToByteOffset(charOffset)

            val bookmark = BookmarkEntity(
                bookId = state.bookId,
                createdTime = System.currentTimeMillis(),
                byteOffset = byteOffset,
                selectedText = selectedText,
                chapterIndex = state.chapterIndex,
                chapterTitle = state.chapterTitle,
            )
            dao.insertBookmark(bookmark)
            loadBookmarks()
        }
    }

    /**
     * 使用选区添加书签
     */
    fun addBookmark(range: SelectionRange) {
        addBookmark(range.selectedText)
    }

    /**
     * 删除书签
     */
    fun deleteBookmark(bookmark: BookmarkEntity) {
        val dao = bookmarkDao ?: return
        viewModelScope.launch {
            dao.deleteBookmark(bookmark)
            loadBookmarks()
        }
    }

    /**
     * 跳转到书签位置（v4：字节偏移）
     */
    fun goToBookmark(bookmark: BookmarkEntity) {
        val chapters = loadedBookContent?.chapters
        if (chapters.isNullOrEmpty()) return
        // 通过 byteOffset 查找所在章节
        val chapterIndex = chapters.indexOfLast { it.byteStart <= bookmark.byteOffset }
            .coerceIn(0, chapters.lastIndex)
        jumpToChapterPosition(chapterIndex, bookmark.byteOffset)
    }

    /**
     * 加载当前书籍的书签列表
     */
    fun loadBookmarks() {
        val dao = bookmarkDao ?: return
        val state = _uiState.value
        if (state.bookId == 0L) return

        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            dao.getBookmarksByBookId(state.bookId).collect { bookmarks ->
                _uiState.value = _uiState.value.copy(bookmarks = bookmarks)
            }
        }
    }

    // ── 笔记管理 ──────────────────────────────────────────────

    /**
     * 添加笔记（v4：使用字节偏移）
     */
    fun addNote(startPos: Int, endPos: Int, content: String, color: String? = null) {
        val dao = noteDao ?: return
        val state = _uiState.value
        if (state.bookId == 0L) return

        viewModelScope.launch {
            val chapters = loadedBookContent?.chapters
            val chapterByteStart = chapters?.getOrNull(state.chapterIndex)?.byteStart ?: 0L
            val byteStart = chapterByteStart + charToByteOffset(startPos)
            val byteEnd = chapterByteStart + charToByteOffset(endPos)

            val note = NoteEntity(
                bookId = state.bookId,
                createdTime = System.currentTimeMillis(),
                byteStart = byteStart.toLong(),
                byteEnd = byteEnd.toLong(),
                noteText = content,
                color = color,
            )
            dao.insertNote(note)
            loadNotes()
        }
    }

    /**
     * 使用选区添加笔记
     */
    fun addNote(range: SelectionRange, content: String, color: String? = null) {
        addNote(range.startPos, range.endPos, content, color)
    }

    /**
     * 删除笔记
     */
    fun deleteNote(note: NoteEntity) {
        val dao = noteDao ?: return
        viewModelScope.launch {
            dao.deleteNote(note)
            loadNotes()
        }
    }

    fun updateNote(note: NoteEntity, newText: String, newColor: String? = note.color) {
        val dao = noteDao ?: return
        if (newText.isBlank()) return
        viewModelScope.launch {
            dao.updateNote(note.copy(noteText = newText, color = newColor))
            loadNotes()
        }
    }

    /**
     * 跳转到笔记位置（v4：字节偏移）
     */
    fun goToNote(note: NoteEntity) {
        val chapters = loadedBookContent?.chapters
        if (chapters.isNullOrEmpty()) return
        val chapterIndex = chapters.indexOfLast { it.byteStart <= note.byteStart }
            .coerceIn(0, chapters.lastIndex)
        jumpToChapterPosition(chapterIndex, note.byteStart)
    }

    /**
     * 加载当前书籍的笔记列表
     */
    fun loadNotes() {
        val dao = noteDao ?: return
        val state = _uiState.value
        if (state.bookId == 0L) return

        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            dao.getNotesByBookId(state.bookId).collect { notes ->
                _uiState.value = _uiState.value.copy(notes = notes)
            }
        }
    }

    fun getVisibleNoteRanges(): List<Pair<SelectionRange, String?>> {
        val state = _uiState.value
        val chapter = state.currentChapter ?: return emptyList()
        val chapters = loadedBookContent?.chapters ?: return emptyList()
        val chapterByteStart = chapters.getOrNull(state.chapterIndex)?.byteStart ?: 0L
        val chapterByteEnd = chapters.getOrNull(state.chapterIndex + 1)?.byteStart ?: Long.MAX_VALUE

        return state.notes
            .filter { it.byteStart >= chapterByteStart && it.byteStart < chapterByteEnd && it.color != null }
            .mapNotNull { note ->
                val relativeStart = (note.byteStart - chapterByteStart).toInt().coerceAtLeast(0)
                val relativeEnd = (note.byteEnd - chapterByteStart).toInt().coerceAtLeast(0)
                val charStart = byteToCharOffset(relativeStart)
                val charEnd = byteToCharOffset(relativeEnd)
                if (charStart < charEnd) {
                    SelectionRange(
                        chapterIndex = state.chapterIndex,
                        startPos = charStart,
                        endPos = charEnd,
                    ) to note.color
                } else null
            }
    }

    fun exportNotesAsMarkdown(): String? {
        val state = _uiState.value
        if (state.notes.isEmpty() || state.bookTitle.isBlank()) return null
        val sb = StringBuilder()
        sb.append("# ${state.bookTitle} — Notes\n\n")
        for (note in state.notes.sortedBy { it.byteStart }) {
            sb.append("## ${note.noteText.take(40)}\n")
            sb.append("- Position: ${note.byteStart}-${note.byteEnd}\n")
            if (note.color != null) sb.append("- Color: ${note.color}\n")
            sb.append("- Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(note.createdTime))}\n")
            sb.append("\n${note.noteText}\n\n---\n\n")
        }
        return sb.toString()
    }


    private fun handleTtsUtteranceCompleted() {
        val nextIndex = ttsSentenceIndex + 1
        if (nextIndex < ttsSentences.size) {
            ttsSentenceIndex = nextIndex
            speakCurrentTtsSentence()
            return
        }

        val state = _uiState.value
        val chapter = state.currentChapter

        if (activeTtsConfig.autoPage && chapter != null) {
            if (state.pageIndex < chapter.lastIndex) {
                nextPage()
                ttsSentences = sentenceRangesForCurrentPage()
                ttsSentenceIndex = 0
                speakCurrentTtsSentence()
                return
            }

            val totalChapters = loadedBookContent?.normalizedChapters()?.size ?: 0
            if (state.chapterIndex < totalChapters - 1) {
                ttsPendingResume = true
                openChapter(state.chapterIndex + 1)
                return
            }
        }

        val controller = ttsController
        _uiState.value = _uiState.value.copy(
            ttsState = controller?.state ?: TtsState.READY,
            ttsActiveRange = null,
        )
        ttsSentences = emptyList()
        ttsSentenceIndex = -1
    }

    private fun speakCurrentTtsSentence() {
        val controller = ttsController ?: return
        val range = ttsSentences.getOrNull(ttsSentenceIndex)
        val text = range?.selectedText.orEmpty()
        if (range == null || text.isBlank()) {
            _uiState.value = _uiState.value.copy(
                ttsState = TtsState.ERROR,
                ttsActiveRange = null,
            )
            return
        }

        controller.play(text)
        val newRange = if (activeTtsConfig.highlightSentence) range else null
        _uiState.value = _uiState.value.copy(
            ttsState = controller.state,
            ttsActiveRange = newRange,
        )
        // TTS 高亮变化只需重绘整页（高亮矩形在 page canvas 上绘制）
        _uiState.value.currentPage?.invalidate()
    }

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

    /**
     * 异步计算所有章节字数，不阻塞首屏加载。
     * v4: 直接使用 BookChapterEntity.wordCount（解析时已计算）。
     */
    private fun computeChapterWordCounts(chapterEntities: List<com.shuli.reader.core.database.entity.BookChapterEntity>) {
        viewModelScope.launch(Dispatchers.Default) {
            val counts = chapterEntities.map { it.wordCount.coerceAtLeast(0) }
            _uiState.value = _uiState.value.copy(chapterWordCounts = counts)
        }
    }

    private suspend fun paginateChapter(content: BookContent, index: Int): TextChapter? {
        val chapters = content.normalizedChapters()
        val chapter = chapters.getOrNull(index) ?: return null

        // R7: 查询章节缓存
        val config = layoutConfigFor(_uiState.value.readerPreferences)
        val prefs = _uiState.value.readerPreferences
        val cacheKey = CacheManager.ChapterCacheKey(
            bookId = _uiState.value.bookId.toString(),
            chapterIndex = index,
            textSize = config.textSize,
            lineHeight = config.lineHeight,
            pageWidth = config.pageSize.width,
            pageHeight = config.pageSize.height,
            letterSpacingPx = config.letterSpacingPx,
            marginHorizontal = config.marginHorizontal,
            marginVertical = config.marginVertical,
            indent = config.indent,
            showHeader = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE,
            showFooter = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE,
            chineseConvert = prefs.chineseConvert.ordinal,
            usePanguSpacing = prefs.usePanguSpacing,
            titleAlignOrdinal = prefs.titleStyle.align.ordinal,
            titleSizeOffsetSp = prefs.titleStyle.sizeOffsetSp,
            titleMarginTopDp = prefs.titleStyle.marginTopDp,
            titleMarginBottomDp = prefs.titleStyle.marginBottomDp,
        )
        cacheManager.getChapter(cacheKey)?.let { return it }

        val repository = bookRepository
        val filePath = currentBookFilePath
        val chapterText = if (repository != null && filePath != null) {
            withContext(Dispatchers.IO) {
                repository.getChapterText(File(filePath), index, content)
            }
        } else {
            content.chapterText(chapter)
        }

        // 应用简繁转换
        val convertedText = when (prefs.chineseConvert) {
            ChineseConvert.NONE -> chapterText
            ChineseConvert.SIMPLIFIED -> ChineseConverter.toSimplified(chapterText)
            ChineseConvert.TRADITIONAL -> ChineseConverter.toTraditional(chapterText)
        }

        // 应用盘古之白
        val finalText = if (prefs.usePanguSpacing) PanguSpacing.insert(convertedText) else convertedText

        val showHeader = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE
        val showFooter = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE

        return withContext(Dispatchers.Default) {
            paginator.paginateChapter(
                chapterIndex = index,
                title = chapter.title,
                content = finalText,
                config = config,
                showHeader = showHeader,
                showFooter = showFooter,
            ).also { result ->
                // R7: 存入章节缓存
                cacheManager.putChapter(cacheKey, result)
            }
        }
    }

    /**
     * 流式分页：首页先显示，其余后台继续
     */
    private fun paginateChapterStreaming(
        content: BookContent,
        index: Int,
        targetCharOffset: Int = 0,
        onDone: (() -> Unit)? = null,
        /** reflow 场景注入缩放比投影，非 reflow 传 null 走简单 merge */
        onMergePageCounts: ((oldPageCounts: Map<Int, Int>, newPageSize: Int) -> Map<Int, Int>)? = null,
    ): Job {
        val chapters = content.normalizedChapters()
        val chapterMeta = chapters.getOrNull(index) ?: return Job().apply { complete() }

        val config = layoutConfigFor(_uiState.value.readerPreferences)
        val repository = bookRepository
        val filePath = currentBookFilePath

        // R1: 缓存命中快路径——跳过 IO + 分页，直接恢复 UI 状态
        val prefs = _uiState.value.readerPreferences
        val cacheKey = CacheManager.ChapterCacheKey(
            bookId = _uiState.value.bookId.toString(),
            chapterIndex = index,
            textSize = config.textSize,
            lineHeight = config.lineHeight,
            pageWidth = config.pageSize.width,
            pageHeight = config.pageSize.height,
            letterSpacingPx = config.letterSpacingPx,
            marginHorizontal = config.marginHorizontal,
            marginVertical = config.marginVertical,
            indent = config.indent,
            showHeader = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE,
            showFooter = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE,
            chineseConvert = prefs.chineseConvert.ordinal,
            usePanguSpacing = prefs.usePanguSpacing,
            titleAlignOrdinal = prefs.titleStyle.align.ordinal,
            titleSizeOffsetSp = prefs.titleStyle.sizeOffsetSp,
            titleMarginTopDp = prefs.titleStyle.marginTopDp,
            titleMarginBottomDp = prefs.titleStyle.marginBottomDp,
        )
        cacheManager.getChapter(cacheKey)?.let { cached ->
            android.util.Log.d(TAG, "openChapter[$index]: 缓存命中, pages=${cached.pageSize}")
            return viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    currentChapter = cached,
                    chapterIndex = index,
                    chapterTitle = chapterMeta.title,
                    totalPages = cached.pageSize,
                    chapterPageCounts = _uiState.value.chapterPageCounts + (index to cached.pageSize),
                    isLoading = false,
                    isReflowing = false,
                )
                // P6: 按 targetCharOffset 跳转或显示首页
                val startPage = if (targetCharOffset > 0) {
                    cached.getPageIndexByCharIndex(targetCharOffset)
                } else {
                    0
                }
                _uiState.value = _uiState.value.copy(
                    pageIndex = startPage,
                    currentPage = cached.getPage(startPage),
                )
                // 持久化页数到文件
                progressTracker.schedulePersist()
                onDone?.invoke()
            }
        }

        android.util.Log.d(TAG, "openChapter[$index]: 缓存未命中, 开始加载章节文本")

        return viewModelScope.launch {
            // reflow 时不显示 loading，保留旧页面内容
            val isReflow = _uiState.value.currentPage != null
            if (!isReflow) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            try {
                val textLoadStart = System.currentTimeMillis()
                // 优先使用缓存文本（openBook 已预加载），避免重复读文件
                val cached = cachedChapterText
                val chapterText = if (cached != null) {
                    cachedChapterText = null // 一次性缓存，用完即清
                    cached
                } else if (repository != null && filePath != null) {
                    withContext(Dispatchers.IO) {
                        repository.getChapterText(File(filePath), index, content)
                    }
                } else {
                    content.chapterText(chapterMeta)
                }
                logPerf("getChapterText[$index]", textLoadStart)

                // 应用简繁转换
                val convertedText = when (_uiState.value.readerPreferences.chineseConvert) {
                    ChineseConvert.NONE -> chapterText
                    ChineseConvert.SIMPLIFIED -> ChineseConverter.toSimplified(chapterText)
                    ChineseConvert.TRADITIONAL -> ChineseConverter.toTraditional(chapterText)
                }

                // 应用盘古之白
                val panguPrefs = _uiState.value.readerPreferences
                val finalText = if (panguPrefs.usePanguSpacing) PanguSpacing.insert(convertedText) else convertedText

                val chapter = TextChapter(
                    chapterIndex = index,
                    title = chapterMeta.title,
                    content = finalText,
                )

                // 设置监听器：首页就绪时立即显示
                chapter.layoutListener = object : TextChapter.LayoutListener {
                    override fun onPageReady(pageIndex: Int, page: TextPage) {
                        val currentState = _uiState.value
                        if (currentState.chapterIndex != index) return

                        // 首页就绪：以下任一条件满足时立即显示
                        // 1. currentPage 为 null（首次加载）
                        // 2. isReflowing（排版参数变化）
                        // 3. currentPage 属于旧章节（章节切换，isReflow=true 保留旧页面场景）
                        val isChapterSwitch = currentState.currentPage?.chapterIndex?.let { it != index } ?: false
                        if (pageIndex == 0 && (currentState.currentPage == null || currentState.isReflowing || isChapterSwitch)) {
                            _uiState.value = currentState.copy(
                                currentPage = page,
                                pageIndex = 0,
                            )
                        } else if (targetCharOffset > 0 &&
                            page.startCharOffset <= targetCharOffset &&
                            targetCharOffset < page.endCharOffset
                        ) {
                            // 目标页就绪：跳转到目标位置
                            _uiState.value = _uiState.value.copy(
                                currentPage = page,
                                pageIndex = pageIndex,
                            )
                        }
                    }

                    override fun onLayoutCompleted() {
                        val currentState = _uiState.value
                        if (currentState.chapterIndex != index) return
                        // 缩放比投影（reflow）或简单 merge（普通加载）
                        val mergedPageCounts = onMergePageCounts?.invoke(currentState.chapterPageCounts, chapter.pageSize)
                            ?: currentState.chapterPageCounts
                        _uiState.value = currentState.copy(
                            totalPages = chapter.pageSize,
                            chapterPageCounts = mergedPageCounts + (index to chapter.pageSize),
                            isLoading = false,
                            isReflowing = false,
                            layoutVersion = currentState.layoutVersion + 1,
                        )
                        // 存入缓存
                        cacheManager.putChapter(cacheKey, chapter)
                        // 持久化页数到文件
                        progressTracker.schedulePersist()
                        if (com.shuli.reader.BuildConfig.DEBUG) {
                            android.util.Log.d(TAG, "layoutCompleted[$index]: ${chapter.pageSize} pages")
                        }
                    }
                }

                // reflow 时保留旧页面，避免 currentPage=null 导致闪烁
                // 首次加载时清空页面显示 loading
                val isReflow = _uiState.value.currentPage != null
                _uiState.value = _uiState.value.copy(
                    currentChapter = chapter,
                    chapterIndex = index,
                    chapterTitle = chapterMeta.title,
                    currentPage = if (isReflow) _uiState.value.currentPage else null,
                    pageIndex = if (isReflow) _uiState.value.pageIndex else 0,
                    totalPages = if (isReflow) _uiState.value.totalPages else 0,
                )

                // 流式分页
                val showHeader = _uiState.value.readerPreferences.header.visibility != HeaderVisibility.ALWAYS_HIDE
                val showFooter = _uiState.value.readerPreferences.footer.visibility != HeaderVisibility.ALWAYS_HIDE
                withContext(Dispatchers.Default) {
                    paginator.paginateStreaming(chapter, finalText, config, showHeader = showHeader, showFooter = showFooter).collect()
                    chapter.markCompleted()
                }

                onDone?.invoke()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message,
                )
            }
        }
    }

    /**
     * R7: 预加载相邻章节（异步，不阻塞当前流程）
     */
    private fun preloadAdjacentChapters(content: BookContent, currentIndex: Int) {
        val chapters = content.normalizedChapters()
        val config = layoutConfigFor(_uiState.value.readerPreferences)
        val bookId = _uiState.value.bookId.toString()

        val indicesToPreload = listOfNotNull(
            (currentIndex - 1).takeIf { it >= 0 },
            (currentIndex + 1).takeIf { it < chapters.size },
        )

        val prefs = _uiState.value.readerPreferences
        for (index in indicesToPreload) {
            val cacheKey = CacheManager.ChapterCacheKey(
                bookId = bookId,
                chapterIndex = index,
                textSize = config.textSize,
                lineHeight = config.lineHeight,
                pageWidth = config.pageSize.width,
                pageHeight = config.pageSize.height,
                letterSpacingPx = config.letterSpacingPx,
                marginHorizontal = config.marginHorizontal,
                marginVertical = config.marginVertical,
                indent = config.indent,
                showHeader = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE,
                showFooter = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE,
                chineseConvert = prefs.chineseConvert.ordinal,
                usePanguSpacing = prefs.usePanguSpacing,
                titleAlignOrdinal = prefs.titleStyle.align.ordinal,
                titleSizeOffsetSp = prefs.titleStyle.sizeOffsetSp,
                titleMarginTopDp = prefs.titleStyle.marginTopDp,
                titleMarginBottomDp = prefs.titleStyle.marginBottomDp,
            )
            if (cacheManager.getChapter(cacheKey) != null) continue

            viewModelScope.launch(Dispatchers.Default) {
                val chapter = chapters[index]
                val repository = bookRepository
                val filePath = currentBookFilePath
                val chapterText = if (repository != null && filePath != null) {
                    withContext(Dispatchers.IO) {
                        repository.getChapterText(File(filePath), index, content)
                    }
                } else {
                    content.chapterText(chapter)
                }

                // 应用简繁转换（与 paginateChapterStreaming 保持一致）
                val convertedText = when (prefs.chineseConvert) {
                    ChineseConvert.NONE -> chapterText
                    ChineseConvert.SIMPLIFIED -> ChineseConverter.toSimplified(chapterText)
                    ChineseConvert.TRADITIONAL -> ChineseConverter.toTraditional(chapterText)
                }
                val finalText = if (prefs.usePanguSpacing) PanguSpacing.insert(convertedText) else convertedText

                val result = paginator.paginateChapter(
                    chapterIndex = index,
                    title = chapter.title,
                    content = finalText,
                    config = config,
                )
                cacheManager.putChapter(cacheKey, result)
            }
        }
    }

    private fun reflowCurrentChapter(preferences: ReaderPreferences) {
        val state = _uiState.value
        val chapter = state.currentChapter ?: return
        val charOffset = state.currentPage?.startCharOffset ?: 0
        val content = loadedBookContent ?: return
        // reflow 前记录当前章节旧页数，用于缩放比投影（coerceAtLeast(1) 防止首屏 totalPages=0 时除零）
        val oldCurrentPages = (state.chapterPageCounts[chapter.chapterIndex] ?: state.totalPages).coerceAtLeast(1)
        // 布局参数已变，更新哈希（新哈希用于持久化，旧文件自然失效）
        progressTracker.updateLayoutHash(preferences)
        reflowJob?.cancel()
        reflowJob = viewModelScope.launch {
            // 无防抖：滑块拖动时实时 reflow，旧协程通过 cancel 自动取消

            // R7: 布局参数变化时清理旧缓存（key 中的 textSize/lineHeight/pageSize 已变）
            cacheManager.clearBook(state.bookId.toString())

            // 缩放比投影：reflow 完成后按比例修正所有旧页数，不清空 map
            val mergeFn: (Map<Int, Int>, Int) -> Map<Int, Int> = { oldMap, newPageSize ->
                if (oldCurrentPages > 0 && newPageSize > 0) {
                    val scale = newPageSize.toDouble() / oldCurrentPages
                    oldMap.mapValues { (_, p) -> (p * scale).toInt().coerceAtLeast(1) }
                } else {
                    oldMap
                }
            }
            // 流式分页：首页秒开，自动跳转到之前的阅读位置
            paginateChapterStreaming(
                content = content,
                index = chapter.chapterIndex,
                targetCharOffset = charOffset,
                onMergePageCounts = mergeFn,
            )
        }
    }

    // ── 预设管理 ──────────────────────────────────────────────

    /**
     * 加载预设列表
     */
    fun loadPresets() {
        val dao = presetDao ?: return
        viewModelScope.launch {
            dao.observeAll().collect { presets ->
                _uiState.value = _uiState.value.copy(presets = presets)
            }
        }
    }

    /**
     * 保存当前设置为预设
     */
    fun saveCurrentAsPreset(name: String) {
        val dao = presetDao ?: return
        viewModelScope.launch {
            val currentPrefs = _uiState.value.readerPreferences
            val configJson = kotlinx.serialization.json.Json.encodeToString(
                ReaderPreferences.serializer(),
                currentPrefs,
            )
            val entity = com.shuli.reader.core.database.entity.ReaderPresetEntity(
                name = name,
                createdAt = System.currentTimeMillis(),
                configJson = configJson,
            )
            dao.insert(entity)
        }
    }

    /**
     * 应用预设
     */
    fun applyPreset(presetId: Long) {
        val dao = presetDao ?: return
        viewModelScope.launch {
            val entity = dao.getById(presetId) ?: return@launch
            try {
                val prefs = kotlinx.serialization.json.Json.decodeFromString(
                    ReaderPreferences.serializer(),
                    entity.configJson,
                )
                // 依次调用 setter 应用所有设置
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to apply preset: ${e.message}")
            }
        }
    }

    /**
     * 重命名预设
     */
    fun renamePreset(presetId: Long, newName: String) {
        val dao = presetDao ?: return
        viewModelScope.launch {
            val entity = dao.getById(presetId) ?: return@launch
            dao.update(entity.copy(name = newName))
        }
    }

    /**
     * 删除预设
     */
    fun deletePreset(presetId: Long) {
        val dao = presetDao ?: return
        viewModelScope.launch {
            dao.deleteById(presetId)
        }
    }

    /**
     * 恢复默认设置
     */
    fun resetToDefault() {
        val defaults = ReaderPreferences()
        setFontSize(defaults.fontSize)
        setLineSpacing(defaults.lineSpacing)
        setParagraphSpacing(defaults.paragraphSpacing)
        setIndent(defaults.indent)
        setMarginHorizontal(defaults.marginHorizontal)
        setMarginVertical(defaults.marginVertical)
        setReadingFont(defaults.readingFont)
        setPageAnimType(defaults.pageAnimType.toFactoryType())
        setReaderTheme(defaults.backgroundColor)
        setLetterSpacing(defaults.letterSpacing)
        setFontWeight(defaults.fontWeight)
        setTextAlign(defaults.textAlign)
        setChineseConvert(defaults.chineseConvert)
        setBrightness(defaults.brightness)
        setHeaderVisibility(defaults.header.visibility)
        setHeaderLeft(defaults.header.left)
        setHeaderCenter(defaults.header.center)
        setHeaderRight(defaults.header.right)
        setFooterVisibility(defaults.footer.visibility)
        setFooterLeft(defaults.footer.left)
        setFooterCenter(defaults.footer.center)
        setFooterRight(defaults.footer.right)
        setHeaderFooterAlpha(defaults.headerFooterAlpha)
        setShowProgress(defaults.showProgress)
        setTitleAlign(defaults.titleStyle.align)
        setTitleSizeOffset(defaults.titleStyle.sizeOffsetSp)
        setTitleMarginTop(defaults.titleStyle.marginTopDp)
        setTitleMarginBottom(defaults.titleStyle.marginBottomDp)
        setKeepScreenOn(defaults.keepScreenOn)
        setVolumeKeyTurnPage(defaults.volumeKeyTurnPage)
        setEdgeTurnPage(defaults.edgeTurnPage)
        setEdgeWidthPercent(defaults.edgeWidthPercent)
        setShowHeaderLine(defaults.showHeaderLine)
        setShowFooterLine(defaults.showFooterLine)
        setHeaderFontSizeRatio(defaults.headerFontSizeRatio)
        setFooterFontSizeRatio(defaults.footerFontSizeRatio)
        setBottomJustify(defaults.bottomJustify)
    }

    /** 统一解析页眉和页脚槽位为 SlotResolution，避免重复计算进度 */
    fun resolveHeaderAndFooterSlots(): Pair<SlotResolution, SlotResolution> =
        progressTracker.resolveHeaderAndFooterSlots()

    private fun layoutConfigFor(preferences: ReaderPreferences): ReaderLayoutConfig =
        progressTracker.buildLayoutConfig(preferences)

    private fun BookContent.chapterText(chapter: Chapter): String {
        // v4: 对于 EPUB，content 已是完整文本，直接返回
        // 对于 TXT，此方法不再使用（改用 loadChapterText）
        return content
    }

    override fun onCleared() {
        releaseReaderResources()
        super.onCleared()
    }
}
