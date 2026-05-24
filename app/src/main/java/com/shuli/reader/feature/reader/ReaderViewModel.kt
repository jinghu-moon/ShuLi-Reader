package com.shuli.reader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.data.toFactoryType
import com.shuli.reader.core.data.toPageAnimType
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
import java.io.File
import kotlinx.coroutines.Dispatchers
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
 * 阅读器 UI 状态
 */
enum class OverlayPanel {
    NONE, DIRECTORY, BRIGHTNESS, QUICK_SETTINGS
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
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val currentSearchResultIndex: Int = -1,
    val selectedRange: SelectionRange? = null,
    val ttsState: TtsState = TtsState.IDLE,
    val ttsActiveRange: SelectionRange? = null,
) {
    val showDirectory: Boolean get() = overlayPanel == OverlayPanel.DIRECTORY
    val showQuickSettings: Boolean get() = overlayPanel == OverlayPanel.QUICK_SETTINGS
    val showBrightness: Boolean get() = overlayPanel == OverlayPanel.BRIGHTNESS

    /** 当前主题颜色配置，派生自 readerPreferences.backgroundColor */
    val themeColors: ThemeColors
        get() = readerPreferences.backgroundColor
            .toReaderColorScheme()
            .toCanvasThemeColors()
}

/**
 * 阅读器 ViewModel
 */
class ReaderViewModel(
    private val userPreferences: UserPreferences? = null,
    private val bookRepository: BookRepository? = null,
    private val bookmarkDao: BookmarkDao? = null,
    private val noteDao: NoteDao? = null,
    private val paginator: Paginator = Paginator(SimpleTextMeasurer()),
    ttsEngine: TtsEngine? = null,
) : ViewModel() {

    companion object {
        private const val TOOLBAR_AUTO_HIDE_DELAY_MS = 5000L
        private const val PARAGRAPH_SPACING_PX = 12f
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

    // R7: 章节缓存管理器
    private val cacheManager = CacheManager.forMemoryClass(256)

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
    }

    /**
     * 翻页动画委托
     */
    var pageDelegate: PageDelegate = PageDelegateFactory.create(_uiState.value.pageAnimType)
        private set


    private var loadedBookContent: BookContent? = null
    /** 缓存当前书籍文件路径，避免 paginateChapter 重复查询 DB */
    private var currentBookFilePath: String? = null
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
            viewModelScope.launch {
                combine(
                    prefs.defaultFontSize,
                    prefs.defaultLineSpacing,
                    prefs.defaultParagraphSpacing,
                    prefs.defaultIndent,
                    prefs.defaultPageAnim,
                    prefs.brightness,
                    prefs.marginHorizontal,
                    prefs.marginVertical,
                    prefs.readingFont,
                ) { flows ->
                    ReaderPreferences(
                        fontSize = flows[0] as Float,
                        lineSpacing = flows[1] as Float,
                        paragraphSpacing = flows[2] as Float,
                        indent = flows[3] as Float,
                        pageAnimType = (flows[4] as String).toPageAnimType(),
                        brightness = flows[5] as Float,
                        marginHorizontal = flows[6] as Float,
                        marginVertical = flows[7] as Float,
                        readingFont = flows[8] as String,
                    )
                }.collectLatest { preferences ->
                    val factoryType = preferences.pageAnimType.toFactoryType()
                    _uiState.value = _uiState.value.copy(
                        readerPreferences = preferences,
                        pageAnimType = factoryType,
                    )
                    pageDelegate = PageDelegateFactory.create(factoryType)
                    reflowCurrentChapter(preferences)
                }
            }
        }
    }

    /**
     * 打开书籍
     */
    fun openBook(bookId: Long) {
        // R7: 切换书籍时清理缓存
        val oldBookId = _uiState.value.bookId
        if (oldBookId != 0L && oldBookId != bookId) {
            cacheManager.clearBook(oldBookId.toString())
        }

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

                val book = withContext(Dispatchers.IO) {
                    repository.getBookById(bookId).first()
                } ?: run {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Book not found: $bookId",
                    )
                    return@launch
                }

                val content = withContext(Dispatchers.IO) {
                    repository.parseBookContent(File(book.filePath))
                }
                loadedBookContent = content
                currentBookFilePath = book.filePath

                val chapterCount = content.normalizedChapters().size
                val chapterIndex = book.durChapterIndex.coerceIn(0, (chapterCount - 1).coerceAtLeast(0))

                _uiState.value = _uiState.value.copy(
                    bookTitle = book.title,
                    chapterTitle = book.durChapterTitle.orEmpty(),
                    chapterIndex = chapterIndex,
                    totalChapters = chapterCount,
                    chapterTitles = content.normalizedChapters().map { it.title },
                )

                withContext(Dispatchers.IO) {
                    repository.updateLastReadTime(bookId)
                }
                loadBookmarks()
                loadNotes()
                readingStateManager.startSession()

                // 流式分页：首页秒开，目标位置自动跳转
                chapterJob = paginateChapterStreaming(
                    content = content,
                    index = chapterIndex,
                    targetCharOffset = book.durChapterPos,
                    onDone = { preloadAdjacentChapters(content, chapterIndex) },
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
        _uiState.value = _uiState.value.copy(
            readerPreferences = currentPrefs.copy(backgroundColor = theme),
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

    /**
     * 显示/隐藏亮度调节
     */
    fun toggleBrightness() {
        toggleOverlay(OverlayPanel.BRIGHTNESS)
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

    private suspend fun paginateChapter(content: BookContent, index: Int): TextChapter? {
        val chapters = content.normalizedChapters()
        val chapter = chapters.getOrNull(index) ?: return null

        // R7: 查询章节缓存
        val config = layoutConfigFor(_uiState.value.readerPreferences)
        val cacheKey = CacheManager.ChapterCacheKey(
            bookId = _uiState.value.bookId.toString(),
            chapterIndex = index,
            textSize = config.textSize,
            lineHeight = config.lineHeight,
            pageWidth = config.pageSize.width,
            pageHeight = config.pageSize.height,
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

        return withContext(Dispatchers.Default) {
            paginator.paginateChapter(
                chapterIndex = index,
                title = chapter.title,
                content = chapterText,
                config = config,
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
        val cacheKey = CacheManager.ChapterCacheKey(
            bookId = _uiState.value.bookId.toString(),
            chapterIndex = index,
            textSize = config.textSize,
            lineHeight = config.lineHeight,
            pageWidth = config.pageSize.width,
            pageHeight = config.pageSize.height,
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
                val chapterText = if (repository != null && filePath != null) {
                    withContext(Dispatchers.IO) {
                        repository.getChapterText(File(filePath), index, content)
                    }
                } else {
                    content.chapterText(chapterMeta)
                }

                val chapter = TextChapter(
                    chapterIndex = index,
                    title = chapterMeta.title,
                    content = chapterText,
                )

                // 设置监听器：首页就绪时立即显示
                chapter.layoutListener = object : TextChapter.LayoutListener {
                    override fun onPageReady(pageIndex: Int, page: TextPage) {
                        val currentState = _uiState.value
                        if (currentState.chapterIndex != index) return

                        // P6: targetCharOffset > 0 时等待目标页，不显示第 0 页避免闪烁
                        if (targetCharOffset <= 0 && pageIndex == 0 && currentState.currentPage == null) {
                            _uiState.value = currentState.copy(
                                currentPage = page,
                                pageIndex = 0,
                            )
                        } else if (targetCharOffset > 0 &&
                            page.startCharOffset <= targetCharOffset &&
                            targetCharOffset < page.endCharOffset
                        ) {
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
                withContext(Dispatchers.Default) {
                    paginator.paginateStreaming(chapter, chapterText, config).collect()
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

        for (index in indicesToPreload) {
            val cacheKey = CacheManager.ChapterCacheKey(
                bookId = bookId,
                chapterIndex = index,
                textSize = config.textSize,
                lineHeight = config.lineHeight,
                pageWidth = config.pageSize.width,
                pageHeight = config.pageSize.height,
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
                val result = paginator.paginateChapter(
                    chapterIndex = index,
                    title = chapter.title,
                    content = chapterText,
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

            // 流式分页：首页秒开，自动跳转到之前的阅读位置
            paginateChapterStreaming(
                content = content,
                index = chapter.chapterIndex,
                targetCharOffset = charOffset,
            )
        }
    }

    private fun layoutConfigFor(preferences: ReaderPreferences): ReaderLayoutConfig {
        return ReaderLayoutConfig(
            pageSize = PageSize(screenWidthPx, screenHeightPx),
            textSize = preferences.fontSize * density,
            lineHeight = preferences.lineSpacing,
            paragraphSpacing = preferences.paragraphSpacing * PARAGRAPH_SPACING_PX,
            marginHorizontal = preferences.marginHorizontal * density,
            marginVertical = preferences.marginVertical * density,
            indent = preferences.indent,
            density = this.density,
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
