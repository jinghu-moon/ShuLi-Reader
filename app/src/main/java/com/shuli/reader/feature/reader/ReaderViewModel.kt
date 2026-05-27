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
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import com.shuli.reader.core.reader.ChapterProvider
import com.shuli.reader.core.reader.Paginator
import com.shuli.reader.core.reader.ReadingStateManager
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
)

/**
 * 行为参数（变化时仅更新标志位）
 */
private data class ReaderBehaviorPrefs(
    val brightness: Float,
    val keepScreenOn: Boolean,
    val volumeKeyTurnPage: Boolean,
    val edgeTurnPage: Boolean,
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
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val currentSearchResultIndex: Int = -1,
    val selectedRange: SelectionRange? = null,
    val ttsState: TtsState = TtsState.IDLE,
    val ttsActiveRange: SelectionRange? = null,
    val presets: List<com.shuli.reader.core.database.entity.ReaderPresetEntity> = emptyList(),
    /** 用户导入的自定义字体列表 */
    val customFonts: List<com.shuli.reader.core.font.FontManager.FontEntry> = emptyList(),
    /** 缓存的主题颜色，避免每次访问 themeColors 都创建中间对象 */
    val themeColors: ThemeColors = readerPreferences.backgroundColor
        .toReaderColorScheme().toCanvasThemeColors(),
    /** 排版版本号，每次 reflow 递增，用于 Canvas 层 crossfade 判断 */
    val layoutVersion: Int = 0,
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
    private val paginator: Paginator = Paginator(SimpleTextMeasurer()),
    ttsEngine: TtsEngine? = null,
    private val fontManager: com.shuli.reader.core.font.FontManager? = null,
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

    init {
        readingStateManager = ReadingStateManager(
            scope = viewModelScope,
            saveAction = { state ->
                bookRepository?.updateReadingPosition(
                    bookId = state.bookId,
                    chapterIndex = state.chapterIndex,
                    chapterPos = state.chapterPos,
                    chapterTitle = state.chapterTitle,
                    chapterTime = readingStateManager.getSessionElapsedMs(),
                    totalChapters = _uiState.value.totalChapters,
                )
            },
        )
        loadPresets()
        loadCustomFonts()
    }

    // ── 字体管理 ──────────────────────────────────────────────

    fun loadCustomFonts() {
        val fm = fontManager ?: return
        _uiState.value = _uiState.value.copy(customFonts = fm.listFonts())
    }

    fun importFont(uri: android.net.Uri, displayName: String? = null) {
        val fm = fontManager ?: return
        try {
            fm.importFont(uri, displayName)
            loadCustomFonts()
        } catch (_: Exception) {
            // 导入失败，静默处理
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
    private val ttsController = ttsEngine?.let { engine ->
        TtsController(
            engine = engine,
            onUtteranceCompleted = ::handleTtsUtteranceCompleted,
        )
    }
    private var activeTtsConfig = TtsConfig()
    private var ttsSentences: List<SelectionRange> = emptyList()
    private var ttsSentenceIndex: Int = -1

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
                        ),
                        footer = FooterConfig(
                            visibility = (flows[8] as String).toHeaderVisibility(),
                            left = (flows[9] as String).toSlotContent(),
                            center = (flows[10] as String).toSlotContent(),
                            right = (flows[11] as String).toSlotContent(),
                        ),
                        headerFooterAlpha = flows[12] as Float,
                        showProgress = flows[13] as Boolean,
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
                ) { flows: Array<Any> ->
                    ReaderBehaviorPrefs(
                        brightness = flows[0] as Float,
                        keepScreenOn = flows[1] as Boolean,
                        volumeKeyTurnPage = flows[2] as Boolean,
                        edgeTurnPage = flows[3] as Boolean,
                    )
                }.collectLatest { behavior ->
                    val current = _uiState.value.readerPreferences
                    _uiState.value = _uiState.value.copy(
                        readerPreferences = current.copy(
                            brightness = behavior.brightness,
                            keepScreenOn = behavior.keepScreenOn,
                            volumeKeyTurnPage = behavior.volumeKeyTurnPage,
                            edgeTurnPage = behavior.edgeTurnPage,
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
        readingStateManager.endSession()

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

                // 2. 解析书籍内容（EPUB 轻量模式：只读目录，不读正文）
                val parseStart = System.currentTimeMillis()
                val content = withContext(Dispatchers.IO) {
                    repository.parseBookContent(File(book.filePath))
                }
                logPerf("parseBookContent [${book.fileType}]", parseStart)

                loadedBookContent = content
                currentBookFilePath = book.filePath
                isCurrentBookEpub = book.fileType == "EPUB"

                val chapterCount = content.normalizedChapters().size
                val chapterIndex = book.durChapterIndex.coerceIn(0, (chapterCount - 1).coerceAtLeast(0))

                val chapters = content.normalizedChapters()
                _uiState.value = _uiState.value.copy(
                    bookTitle = book.title,
                    chapterTitle = book.durChapterTitle.orEmpty(),
                    chapterIndex = chapterIndex,
                    totalChapters = chapterCount,
                    chapterTitles = chapters.map { it.title },
                    chapterWordCounts = emptyList(), // 延迟计算，不阻塞首屏
                )

                withContext(Dispatchers.IO) {
                    repository.updateLastReadTime(bookId)
                }
                loadBookmarks()
                loadNotes()
                readingStateManager.startSession()

                logPerf("openBook.preparation", perfStart)

                // 3. 流式分页：首页秒开，目标位置自动跳转
                val paginateStart = System.currentTimeMillis()
                chapterJob = paginateChapterStreaming(
                    content = content,
                    index = chapterIndex,
                    targetCharOffset = book.durChapterPos,
                    onDone = {
                        logPerf("firstPageReady", perfStart)
                        preloadAdjacentChapters(content, chapterIndex)
                        // EPUB 轻量解析时 content 为空，跳过全量字数统计
                        if (!isCurrentBookEpub) {
                            computeChapterWordCounts(content)
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
     * 跳转到章节内指定字符偏移位置
     */
    fun jumpToChapterPosition(chapterIndex: Int, charOffset: Int) {
        val state = _uiState.value
        val chapter = state.currentChapter
        if (chapter?.chapterIndex == chapterIndex && chapter.pageSize > 0) {
            val pi = chapter.getPageIndexByCharIndex(charOffset)
            jumpToPage(pi)
        } else {
            openChapter(chapterIndex, targetCharOffset = charOffset)
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

        if (immediate) {
            readingStateManager.saveReadNow(bookId, state.chapterIndex, chapterPos, chapterTitle)
        } else {
            readingStateManager.saveReadDebounced(bookId, state.chapterIndex, chapterPos, chapterTitle)
        }

        // 同步更新 BookEntity 的阅读进度字段
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository?.let { repo ->
                val totalPages = state.totalPages.coerceAtLeast(1)
                val progress = (state.pageIndex.toFloat() / totalPages).coerceIn(0f, 1f)
                repo.updateReadingPosition(
                    bookId = bookId,
                    chapterIndex = state.chapterIndex,
                    chapterPos = chapterPos,
                    chapterTitle = chapterTitle,
                    chapterTime = readingStateManager.getSessionElapsedMs(),
                    totalChapters = state.totalChapters,
                )
                repo.updateReadingProgress(bookId, progress)
            }
        }
    }

    /**
     * 打开章节
     * @param targetToLastPage 是否跳转到末页（跨章前翻时使用）
     * @param targetCharOffset 目标字符偏移（书签/笔记跳转时使用）
     */
    fun openChapter(
        index: Int,
        targetToLastPage: Boolean = false,
        targetCharOffset: Int = -1,
    ) {
        resetToolbarAutoHide()
        val content = loadedBookContent
        if (content == null) {
            viewModelScope.launch { openFallbackChapter(index) }
            return
        }

        val safeIndex = index.coerceIn(0, (content.normalizedChapters().size - 1).coerceAtLeast(0))

        // M1: 取消上一次章节加载，防止快速连续调用导致并发冲突
        chapterJob?.cancel()

        // 流式分页：首页秒开
        // targetToLastPage 时传 -1，由 LayoutListener 的 onLayoutCompleted 处理跳末页
        val effectiveCharOffset = if (targetToLastPage) -1 else targetCharOffset
        chapterJob = paginateChapterStreaming(
            content = content,
            index = safeIndex,
            targetCharOffset = effectiveCharOffset,
            onDone = {
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
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(fontSize = size)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        reflowCurrentChapter(updatedPrefs)
        // 同步保存到 UserPreferences
        viewModelScope.launch {
            userPreferences?.setDefaultFontSize(size)
        }
    }

    /**
     * 更新行距
     */
    fun setLineSpacing(spacing: Float) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(lineSpacing = spacing)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setDefaultLineSpacing(spacing)
        }
    }

    /**
     * 更新亮度
     */
    fun setBrightness(brightness: Float) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(brightness = brightness)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        viewModelScope.launch {
            userPreferences?.setBrightness(brightness)
        }
    }

    /**
     * 更新段距
     */
    fun setParagraphSpacing(spacing: Float) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(paragraphSpacing = spacing)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setDefaultParagraphSpacing(spacing)
        }
    }

    /**
     * 更新首行缩进
     */
    fun setIndent(indent: Float) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(indent = indent)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setDefaultIndent(indent)
        }
    }

    /**
     * 更新左右边距
     */
    fun setMarginHorizontal(margin: Float) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(marginHorizontal = margin)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setMarginHorizontal(margin)
        }
    }

    /**
     * 更新上下边距
     */
    fun setMarginVertical(margin: Float) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(marginVertical = margin)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setMarginVertical(margin)
        }
    }

    /**
     * 更新阅读字体
     */
    fun setReadingFont(font: String) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(readingFont = font)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        viewModelScope.launch {
            userPreferences?.setReadingFont(font)
        }
    }

    /**
     * 更新字距
     */
    fun setLetterSpacing(spacing: Float) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(letterSpacing = spacing)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setLetterSpacing(spacing)
        }
    }

    /**
     * 更新字重（FakeBold 不改变字宽，无需 reflow）
     */
    fun setFontWeight(weight: ReaderFontWeight) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(fontWeight = weight)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        currentPageInvalidate()
        viewModelScope.launch {
            userPreferences?.setFontWeight(weight.toStorageString())
        }
    }

    fun setTtsSpeed(speed: Float) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(ttsSpeed = speed)
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setTtsSpeed(speed)
        }
    }

    fun setTtsPitch(pitch: Float) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(ttsPitch = pitch)
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setTtsPitch(pitch)
        }
    }

    /**
     * 更新对齐方式（对齐不改变每行字符数，无需 reflow）
     */
    fun setTextAlign(align: ReaderTextAlign) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(textAlign = align)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        currentPageInvalidate()
        viewModelScope.launch {
            userPreferences?.setTextAlign(align.toStorageString())
        }
    }

    /**
     * 更新简繁转换
     */
    fun setChineseConvert(convert: ChineseConvert) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(chineseConvert = convert)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setChineseConvert(convert.toStorageString())
        }
    }

    /**
     * 更新中文分行模式（改变断行位置，需要 reflow）
     */
    fun setUseZhLayout(enabled: Boolean) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(useZhLayout = enabled)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setUseZhLayout(enabled)
        }
    }

    /**
     * 更新盘古之白（中英文间加空格，需要 reflow）
     */
    fun setPanguSpacing(enabled: Boolean) {
        resetToolbarAutoHide()
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(usePanguSpacing = enabled)
        _uiState.value = _uiState.value.copy(
            readerPreferences = updatedPrefs,
        )
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setUsePanguSpacing(enabled)
        }
    }

    // ── 页眉脚设置 ──────────────────────────────────────────────

    fun setHeaderVisibility(visibility: HeaderVisibility) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(header = currentPrefs.header.copy(visibility = visibility))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setHeaderVisibility(visibility.toStorageString())
        }
    }

    fun setHeaderLeft(slot: SlotContent) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(header = currentPrefs.header.copy(left = slot))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        currentPageInvalidate()
        viewModelScope.launch {
            userPreferences?.setHeaderLeft(slot.toStorageString())
        }
    }

    fun setHeaderCenter(slot: SlotContent) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(header = currentPrefs.header.copy(center = slot))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        currentPageInvalidate()
        viewModelScope.launch {
            userPreferences?.setHeaderCenter(slot.toStorageString())
        }
    }

    fun setHeaderRight(slot: SlotContent) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(header = currentPrefs.header.copy(right = slot))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        currentPageInvalidate()
        viewModelScope.launch {
            userPreferences?.setHeaderRight(slot.toStorageString())
        }
    }

    fun setFooterVisibility(visibility: HeaderVisibility) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(footer = currentPrefs.footer.copy(visibility = visibility))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setFooterVisibility(visibility.toStorageString())
        }
    }

    fun setFooterLeft(slot: SlotContent) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(footer = currentPrefs.footer.copy(left = slot))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        currentPageInvalidate()
        viewModelScope.launch {
            userPreferences?.setFooterLeft(slot.toStorageString())
        }
    }

    fun setFooterCenter(slot: SlotContent) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(footer = currentPrefs.footer.copy(center = slot))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        currentPageInvalidate()
        viewModelScope.launch {
            userPreferences?.setFooterCenter(slot.toStorageString())
        }
    }

    fun setFooterRight(slot: SlotContent) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(footer = currentPrefs.footer.copy(right = slot))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        currentPageInvalidate()
        viewModelScope.launch {
            userPreferences?.setFooterRight(slot.toStorageString())
        }
    }

    fun setHeaderFooterAlpha(alpha: Float) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(headerFooterAlpha = alpha)
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        currentPageInvalidate()
        viewModelScope.launch {
            userPreferences?.setHeaderFooterAlpha(alpha)
        }
    }

    fun setShowProgress(show: Boolean) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(showProgress = show)
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        currentPageInvalidate()
        viewModelScope.launch {
            userPreferences?.setShowProgress(show)
        }
    }

    // ── 正文标题样式（章首页标题）──────────────────────────────

    /** 标题对齐：LEFT / CENTER / HIDDEN。改变后影响 titleAreaHeight，需重排 */
    fun setTitleAlign(align: TitleAlign) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(titleStyle = currentPrefs.titleStyle.copy(align = align))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setTitleAlign(align.toStorageString())
        }
    }

    /** 标题字号偏移（相对正文字号，sp）。改变后影响 titleAreaHeight，需重排 */
    fun setTitleSizeOffset(offsetSp: Int) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(titleStyle = currentPrefs.titleStyle.copy(sizeOffsetSp = offsetSp))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setTitleSizeOffset(offsetSp)
        }
    }

    /** 标题上距（dp）。影响 titleAreaHeight，需重排 */
    fun setTitleMarginTop(dp: Float) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(titleStyle = currentPrefs.titleStyle.copy(marginTopDp = dp))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setTitleMarginTop(dp)
        }
    }

    /** 标题下距（dp）。影响 titleAreaHeight，需重排 */
    fun setTitleMarginBottom(dp: Float) {
        val currentPrefs = _uiState.value.readerPreferences
        val updatedPrefs = currentPrefs.copy(titleStyle = currentPrefs.titleStyle.copy(marginBottomDp = dp))
        _uiState.value = _uiState.value.copy(readerPreferences = updatedPrefs)
        reflowCurrentChapter(updatedPrefs)
        viewModelScope.launch {
            userPreferences?.setTitleMarginBottom(dp)
        }
    }

    /** 使当前页 recorder 失效并触发重绘（页眉脚/进度条变化时使用） */
    private fun currentPageInvalidate() {
        _uiState.value.currentPage?.invalidate()
    }

    // ── 阶段六：杂项设置 ──────────────────────────────────────

    fun setKeepScreenOn(enabled: Boolean) {
        val currentPrefs = _uiState.value.readerPreferences
        _uiState.value = _uiState.value.copy(readerPreferences = currentPrefs.copy(keepScreenOn = enabled))
        viewModelScope.launch { userPreferences?.setKeepScreenOn(enabled) }
    }

    fun setVolumeKeyTurnPage(enabled: Boolean) {
        val currentPrefs = _uiState.value.readerPreferences
        _uiState.value = _uiState.value.copy(readerPreferences = currentPrefs.copy(volumeKeyTurnPage = enabled))
        viewModelScope.launch { userPreferences?.setVolumeKeyTurnPage(enabled) }
    }

    fun setEdgeTurnPage(enabled: Boolean) {
        val currentPrefs = _uiState.value.readerPreferences
        _uiState.value = _uiState.value.copy(readerPreferences = currentPrefs.copy(edgeTurnPage = enabled))
        viewModelScope.launch { userPreferences?.setEdgeTurnPage(enabled) }
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
        ttsSentences = sentenceRangesForCurrentPage()
        ttsSentenceIndex = 0
        speakCurrentTtsSentence()
    }

    fun pauseTts() {
        val controller = ttsController ?: return
        controller.pause()
        _uiState.value = _uiState.value.copy(ttsState = controller.state)
    }

    fun resumeTts() {
        val controller = ttsController ?: return
        controller.resume()
        _uiState.value = _uiState.value.copy(ttsState = controller.state)
    }

    fun stopTts() {
        val controller = ttsController ?: return
        controller.stop()
        ttsSentences = emptyList()
        ttsSentenceIndex = -1
        _uiState.value = _uiState.value.copy(
            ttsState = controller.state,
            ttsActiveRange = null,
        )
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
        readingStateManager.endSession()
        readingStateManager.cancel()
        chapterProvider.cancel()
        // 缓存保留在 BookCacheStore 中，短时间返回可复用

        toolbarAutoHideJob?.cancel()
        toolbarAutoHideJob = null
        ttsController?.release()
        ttsSentences = emptyList()
        ttsSentenceIndex = -1
        _uiState.value = _uiState.value.copy(
            ttsState = TtsState.IDLE,
            ttsActiveRange = null,
        )
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
        jumpToChapterPosition(result.chapterIndex, result.charOffset)
    }

    // ── 书签管理 ──────────────────────────────────────────────

    /**
     * 在当前位置添加书签
     */
    fun addBookmark(selectedText: String? = null) {
        val dao = bookmarkDao ?: return
        val state = _uiState.value
        if (state.bookId == 0L) return

        viewModelScope.launch {
            val bookmark = BookmarkEntity(
                bookId = state.bookId,
                pageIndex = state.pageIndex,
                position = state.currentPage?.startCharOffset ?: 0,
                title = state.chapterTitle,
                createdTime = System.currentTimeMillis(),
                chapterIndex = state.chapterIndex,
                chapterPos = state.currentPage?.startCharOffset ?: 0,
                chapterName = state.chapterTitle,
                selectedText = selectedText,
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
     * 跳转到书签位置
     */
    fun goToBookmark(bookmark: BookmarkEntity) {
        jumpToChapterPosition(bookmark.chapterIndex, bookmark.chapterPos)
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
     * 添加笔记
     */
    fun addNote(startPos: Int, endPos: Int, content: String, color: String? = null) {
        val dao = noteDao ?: return
        val state = _uiState.value
        if (state.bookId == 0L) return

        viewModelScope.launch {
            val note = NoteEntity(
                bookId = state.bookId,
                startPosition = startPos,
                endPosition = endPos,
                content = content,
                color = color,
                createdTime = System.currentTimeMillis(),
                chapterIndex = state.chapterIndex,
                chapterStartPos = startPos,
                chapterEndPos = endPos,
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

    /**
     * 跳转到笔记位置
     */
    fun goToNote(note: NoteEntity) {
        jumpToChapterPosition(note.chapterIndex, note.chapterStartPos)
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


    private fun handleTtsUtteranceCompleted() {
        val nextIndex = ttsSentenceIndex + 1
        if (nextIndex < ttsSentences.size) {
            ttsSentenceIndex = nextIndex
            speakCurrentTtsSentence()
            return
        }

        if (activeTtsConfig.autoPage) {
            val previousPageIndex = _uiState.value.pageIndex
            nextPage()
            if (_uiState.value.pageIndex != previousPageIndex) {
                ttsSentences = sentenceRangesForCurrentPage()
                ttsSentenceIndex = 0
                speakCurrentTtsSentence()
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
        if (page.lines.isEmpty()) return emptyList()

        // 合并所有行文本，用换行符连接，保持原始偏移
        val fullText = StringBuilder()
        val lineOffsets = mutableListOf<Int>() // 每行在 fullText 中的起始位置
        for (line in page.lines) {
            lineOffsets.add(fullText.length)
            fullText.append(line.text)
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
            this == ';' ||
            this == '\u3002' ||
            this == '\uff01' ||
            this == '\uff1f' ||
            this == '\uff1b'
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
     * 统计章节字数：中文每个字算1，英文每个空格分隔的单词算1，过滤空格和换行。
     */
    private fun countChapterWords(content: String, start: Int, end: Int): Int {
        var count = 0
        var inEnglish = false
        for (i in start until end.coerceAtMost(content.length)) {
            val c = content[i]
            if (c == ' ' || c == '\n' || c == '\r') {
                inEnglish = false
                continue
            }
            if (c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF) {
                // CJK 统一汉字：每个字算1
                count++
                inEnglish = false
            } else if (c in 'a'..'z' || c in 'A'..'Z') {
                if (!inEnglish) {
                    // 英文单词开始
                    count++
                    inEnglish = true
                }
            } else {
                // 数字、标点等：每个算1
                count++
                inEnglish = false
            }
        }
        return count
    }

    /**
     * 异步计算所有章节字数，不阻塞首屏加载
     */
    private fun computeChapterWordCounts(content: BookContent) {
        viewModelScope.launch(Dispatchers.Default) {
            val chapters = content.normalizedChapters()
            val counts = chapters.map { chapter ->
                countChapterWords(content.content, chapter.startIndex, chapter.endIndex)
            }
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
            return viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    currentChapter = cached,
                    chapterIndex = index,
                    chapterTitle = chapterMeta.title,
                    totalPages = cached.pageSize,
                    isLoading = false,
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
                onDone?.invoke()
            }
        }

        return viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val textLoadStart = System.currentTimeMillis()
                val chapterText = if (repository != null && filePath != null) {
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

                        if (pageIndex == 0 && currentState.currentPage == null) {
                            // 首页就绪：立即显示，无论是否有目标偏移
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
                        _uiState.value = currentState.copy(
                            totalPages = chapter.pageSize,
                            isLoading = false,
                        )
                        // 存入缓存
                        cacheManager.putChapter(cacheKey, chapter)
                        if (com.shuli.reader.BuildConfig.DEBUG) {
                            android.util.Log.d(TAG, "layoutCompleted[$index]: ${chapter.pageSize} pages")
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    currentChapter = chapter,
                    chapterIndex = index,
                    chapterTitle = chapterMeta.title,
                    currentPage = null,
                    pageIndex = 0,
                    totalPages = 0,
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
        reflowJob?.cancel()
        reflowJob = viewModelScope.launch {
            delay(100L) // 100ms 防抖，避免滑块连续拖动时频繁分页

            // R7: 布局参数变化时清理旧缓存（key 中的 textSize/lineHeight/pageSize 已变）
            cacheManager.clearBook(state.bookId.toString())

            // 递增排版版本号，触发 Canvas 层 crossfade
            _uiState.value = _uiState.value.copy(
                layoutVersion = _uiState.value.layoutVersion + 1,
            )

            // 流式分页：首页秒开，自动跳转到之前的阅读位置
            paginateChapterStreaming(
                content = content,
                index = chapter.chapterIndex,
                targetCharOffset = charOffset,
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
    }

    /** 解析页眉槽位为 SlotResolution */
    fun resolveHeaderSlots(): SlotResolution {
        val prefs = _uiState.value.readerPreferences
        if (prefs.header.visibility == HeaderVisibility.ALWAYS_HIDE) return SlotResolution()
        val state = _uiState.value
        return SlotResolver.resolveHeader(
            config = prefs.header,
            chapterTitle = state.chapterTitle,
            bookTitle = state.bookTitle,
            pageNumber = state.pageIndex + 1,
            totalPages = state.totalPages.coerceAtLeast(1),
            progress = if (state.totalPages > 0) state.pageIndex.toFloat() / state.totalPages else 0f,
            batteryLevel = 100, // 由 ReaderScreen 传入
        )
    }

    /** 解析页脚槽位为 SlotResolution */
    fun resolveFooterSlots(): SlotResolution {
        val prefs = _uiState.value.readerPreferences
        if (prefs.footer.visibility == HeaderVisibility.ALWAYS_HIDE) return SlotResolution()
        val state = _uiState.value
        return SlotResolver.resolveFooter(
            config = prefs.footer,
            chapterTitle = state.chapterTitle,
            bookTitle = state.bookTitle,
            pageNumber = state.pageIndex + 1,
            totalPages = state.totalPages.coerceAtLeast(1),
            progress = if (state.totalPages > 0) state.pageIndex.toFloat() / state.totalPages else 0f,
            batteryLevel = 100, // 由 ReaderScreen 传入
        )
    }

    private fun layoutConfigFor(preferences: ReaderPreferences): ReaderLayoutConfig {
        val textSizePx = preferences.fontSize * density
        return ReaderLayoutConfig(
            pageSize = PageSize(screenWidthPx, screenHeightPx),
            textSize = textSizePx,
            lineHeight = preferences.lineSpacing,
            paragraphSpacing = preferences.paragraphSpacing * textSizePx * preferences.lineSpacing,
            marginHorizontal = preferences.marginHorizontal * density,
            marginVertical = preferences.marginVertical * density,
            indent = preferences.indent,
            density = this.density,
            letterSpacingPx = preferences.letterSpacing * textSizePx,
            titleStyle = preferences.titleStyle,
            useZhLayout = preferences.useZhLayout,
        )
    }

    private fun BookContent.normalizedChapters(): List<Chapter> {
        if (chapters.isNotEmpty()) return chapters
        return if (content.isNotBlank()) {
            listOf(Chapter(title = "Full Text", startIndex = 0, endIndex = content.length))
        } else {
            emptyList()
        }
    }

    private fun BookContent.chapterText(chapter: Chapter): String {
        val start = chapter.startIndex.coerceIn(0, content.length)
        val end = chapter.endIndex.coerceIn(start, content.length)
        return content.substring(start, end)
    }

    override fun onCleared() {
        releaseReaderResources()
        super.onCleared()
    }
}
