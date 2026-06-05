package com.shuli.reader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.data.toFactoryType
import com.shuli.reader.core.data.toStorageString
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.core.reader.SlotResolver
import com.shuli.reader.core.reader.SlotResolution
import com.shuli.reader.core.reader.TitleAlign
import com.shuli.reader.core.database.dao.BookmarkDao
import com.shuli.reader.core.database.dao.NoteDao
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.reader.ChapterProvider
import com.shuli.reader.core.reader.Paginator
import com.shuli.reader.core.reader.AndroidTextMeasurer
import com.shuli.reader.core.reader.SimpleTextMeasurer
import com.shuli.reader.core.reader.animation.PageDelegate
import com.shuli.reader.core.reader.animation.PageDelegateFactory
import com.shuli.reader.core.reader.cache.CacheManager
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.repository.BookRepository
import com.shuli.reader.core.repository.SearchResult
import com.shuli.reader.feature.reader.book.BookLoadingCoordinator
import com.shuli.reader.feature.reader.book.BookSessionManager
import com.shuli.reader.feature.reader.notes.BookmarkNotesManager
import com.shuli.reader.feature.reader.navigation.ReaderNavigationCoordinator
import com.shuli.reader.feature.reader.presets.ReaderPresetManager
import com.shuli.reader.feature.reader.pagination.ChapterPaginationCoordinator
import com.shuli.reader.feature.reader.prefs.ReaderPreferencesManager
import com.shuli.reader.feature.reader.search.TextSearchCoordinator
import com.shuli.reader.feature.reader.tts.TtsPlaybackManager
import com.shuli.reader.core.tts.TtsConfig
import com.shuli.reader.core.tts.TtsEngine
import com.shuli.reader.core.tts.TtsState
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme
import com.shuli.reader.core.font.FontManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val currentPage: com.shuli.reader.core.reader.model.TextPage? = null,
    val currentChapter: com.shuli.reader.core.reader.model.TextChapter? = null,
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val totalPages: Int = 0,
    val totalChapters: Int = 0,
    val showToolbar: Boolean = false,
    val overlayPanel: OverlayPanel = OverlayPanel.NONE,
    val showMenu: Boolean = false,
    val showSearch: Boolean = false,
    val pageAnimType: PageDelegateFactory.PageAnimType = PageDelegateFactory.PageAnimType.HORIZONTAL,
    val pageRenderMode: com.shuli.reader.core.reader.model.PageRenderMode = com.shuli.reader.core.reader.model.PageRenderMode.SEQUENTIAL,
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
    val themeColors: com.shuli.reader.core.data.ThemeColors = readerPreferences.backgroundColor
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
        private const val TAG = "ReaderPerf"
    }

    private fun logPerf(label: String, startMs: Long) {
        if (com.shuli.reader.BuildConfig.DEBUG) {
            android.util.Log.d(TAG, "$label: ${System.currentTimeMillis() - startMs}ms")
        }
    }

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var _androidTextMeasurer: AndroidTextMeasurer? = null

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

    private var chapterJob: Job? = null
    private var currentLayoutHash: String = ""
    private var cacheManager: CacheManager = CacheManager.forMemoryClass(256)
    private val chapterProvider = ChapterProvider(paginator)

    // ── 子模块 ────────────────────────────────────────────────────

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
        onApplyPreferences = { prefs -> prefsManager.applyAllPreferences(prefs) }
    }

    private val navigationCoordinator = ReaderNavigationCoordinator(
        uiState = _uiState,
        scope = viewModelScope,
        appContext = appContext,
        stringResolver = stringResolver,
    ).apply {
        onGetLoadedBookContent = { loadedBookContent }
        onOpenChapter = { index, toLast, byteOffset -> openChapter(index, toLast, byteOffset) }
        onSaveReadingProgress = { immediate -> saveReadingProgress(immediate) }
        onByteToCharOffset = { this@ReaderViewModel.byteToCharOffset(it) }
        onGetNormalizedChapterCount = {
            val content = loadedBookContent
            if (content != null) paginationCoordinator.run { content.normalizedChapters().size } else 0
        }
    }

    private val prefsManager = ReaderPreferencesManager(
        uiState = _uiState,
        userPreferences = userPreferences,
        scope = viewModelScope,
    ).apply {
        onReflowCurrentChapter = { prefs -> reflowCurrentChapter(prefs) }
        onPageDelegateChanged = { delegate -> pageDelegate = delegate }
        onCurrentPageInvalidate = { _uiState.value.currentPage?.invalidate() }
    }

    private val bookLoadingCoordinator = BookLoadingCoordinator(
        uiState = _uiState,
        bookRepository = bookRepository,
        paginator = paginator,
        scope = viewModelScope,
    ).apply {
        onGetLoadedBookContent = { loadedBookContent }
        onSetLoadedBookContent = { loadedBookContent = it }
        onSetCurrentBookFilePath = { currentBookFilePath = it }
        onSetIsCurrentBookEpub = { isCurrentBookEpub = it }
        onIsCurrentBookEpub = { isCurrentBookEpub }
        onSetCurrentChapterUtf16Map = { currentChapterUtf16Map = it }
        onSetCachedChapterText = { cachedChapterText = it }
        onByteToCharOffset = { this@ReaderViewModel.byteToCharOffset(it) }
        onEndSession = { sessionManager.endSession() }
        onPersistReadingTime = { bookId, elapsed -> sessionManager.persistReadingTime(bookId, elapsed) }
        onStartSession = { sessionManager.startSession() }
        onLoadBookmarks = { loadBookmarks() }
        onLoadNotes = { loadNotes() }
        onSaveReadingProgress = { immediate -> saveReadingProgress(immediate) }
        onTtsResumeAfterChapterLoad = { ttsManager.onResumeAfterChapterLoad() }
        onLogPerf = { label, start -> logPerf(label, start) }
        paginationCoordinator = this@ReaderViewModel.paginationCoordinator
    }

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

    private val searchCoordinator = TextSearchCoordinator(
        uiState = _uiState,
        bookRepository = bookRepository,
        scope = viewModelScope,
    ).apply {
        onJumpToChapterPosition = { chapterIndex, byteOffset -> this@ReaderViewModel.jumpToChapterPosition(chapterIndex, byteOffset) }
    }

    init {
        sessionManager.initialize()
        prefsManager.startCollectingFlows()
        loadPresets()
        loadCustomFonts()
    }

    // ── 翻页动画委托 ──────────────────────────────────────────────

    var pageDelegate: PageDelegate = PageDelegateFactory.create(_uiState.value.pageAnimType)
        private set

    // ── 内部状态 ──────────────────────────────────────────────────

    private var loadedBookContent: BookContent? = null
    private var currentBookFilePath: String? = null
    private var isCurrentBookEpub: Boolean = false
    private var currentChapterUtf16Map: IntArray
        get() = paginationCoordinator.currentChapterUtf16Map
        set(value) { paginationCoordinator.updateUtf16Map(value) }
    private var cachedChapterText: String?
        get() = paginationCoordinator.cachedChapterText
        set(value) { paginationCoordinator.cachedChapterText = value }

    var density: Float = 3f
        private set
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

    // ── 字节↔字符坐标桥接 ────────────────────────────────────────

    private fun charToByteOffset(charIndex: Int): Int = paginationCoordinator.charToByteOffset(charIndex)
    private fun byteToCharOffset(byteOffset: Int): Int = paginationCoordinator.byteToCharOffset(byteOffset)

    // ── 字体管理 ──────────────────────────────────────────────────

    fun loadCustomFonts() {
        val fm = fontManager ?: return
        _uiState.value = _uiState.value.copy(customFonts = fm.listFonts())
    }

    fun importFont(uri: android.net.Uri, displayName: String? = null) {
        val fm = fontManager ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val entry = fm.importFont(uri, displayName)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    loadCustomFonts()
                    android.widget.Toast.makeText(
                        fm.context,
                        stringResolver().fontImportSuccess(entry.name, _uiState.value.customFonts.size),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
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
        fontManager?.deleteFontById(fontId)
        loadCustomFonts()
    }

    // ── 书籍加载（委托 bookLoadingCoordinator）────────────────────

    fun openBook(bookId: Long) {
        bookLoadingCoordinator.cacheManager = cacheManager
        bookLoadingCoordinator.openBook(bookId)
    }

    fun openChapter(index: Int, targetToLastPage: Boolean = false, targetByteOffset: Long = -1L) {
        resetToolbarAutoHide()
        bookLoadingCoordinator.chapterJob = chapterJob
        bookLoadingCoordinator.openChapter(index, targetToLastPage, targetByteOffset)
        chapterJob = bookLoadingCoordinator.chapterJob
    }

    // ── 翻页与导航（委托 navigationCoordinator）──────────────────

    fun nextPage() = navigationCoordinator.nextPage()
    fun prevPage() = navigationCoordinator.prevPage()
    fun jumpToPage(pageIndex: Int) = navigationCoordinator.jumpToPage(pageIndex)
    fun jumpToChapterPosition(chapterIndex: Int, byteOffset: Long) = navigationCoordinator.jumpToChapterPosition(chapterIndex, byteOffset)
    fun startPageScrub() = navigationCoordinator.startPageScrub()
    fun scrubToPage(pageIndex: Int) = navigationCoordinator.scrubToPage(pageIndex)
    fun commitPageScrub() = navigationCoordinator.commitPageScrub()

    private fun saveReadingProgress(immediate: Boolean) = sessionManager.saveReadingProgress(immediate)

    // ── 偏好设置（委托 prefsManager）──────────────────────────────

    fun setReaderTheme(theme: ReaderTheme) = prefsManager.setReaderTheme(theme)
    fun cycleTheme() = prefsManager.cycleTheme()

    fun setFontSize(size: Float) { resetToolbarAutoHide(); prefsManager.setFontSize(size) }
    fun setLineSpacing(spacing: Float) { resetToolbarAutoHide(); prefsManager.setLineSpacing(spacing) }
    fun setBrightness(brightness: Float, finished: Boolean = false) { resetToolbarAutoHide(); prefsManager.setBrightness(brightness, finished) }
    fun setParagraphSpacing(spacing: Float) { resetToolbarAutoHide(); prefsManager.setParagraphSpacing(spacing) }
    fun setIndent(indent: Float) { resetToolbarAutoHide(); prefsManager.setIndent(indent) }
    fun setMarginHorizontal(margin: Float) { resetToolbarAutoHide(); prefsManager.setMarginHorizontal(margin) }
    fun setMarginVertical(margin: Float) { resetToolbarAutoHide(); prefsManager.setMarginVertical(margin) }
    fun setHeaderMarginTop(margin: Float) { resetToolbarAutoHide(); prefsManager.setHeaderMarginTop(margin) }
    fun setFooterMarginBottom(margin: Float) { resetToolbarAutoHide(); prefsManager.setFooterMarginBottom(margin) }
    fun setReadingFont(font: String) { resetToolbarAutoHide(); prefsManager.setReadingFont(font) }
    fun setLetterSpacing(spacing: Float) { resetToolbarAutoHide(); prefsManager.setLetterSpacing(spacing) }
    fun setFontWeight(weight: ReaderFontWeight) { resetToolbarAutoHide(); prefsManager.setFontWeight(weight) }
    fun setTtsSpeed(speed: Float) = prefsManager.setTtsSpeed(speed)
    fun setTtsPitch(pitch: Float) = prefsManager.setTtsPitch(pitch)
    fun setTextAlign(align: ReaderTextAlign) { resetToolbarAutoHide(); prefsManager.setTextAlign(align) }
    fun setChineseConvert(convert: ChineseConvert) { resetToolbarAutoHide(); prefsManager.setChineseConvert(convert) }
    fun setUseZhLayout(enabled: Boolean) { resetToolbarAutoHide(); prefsManager.setUseZhLayout(enabled) }
    fun setPanguSpacing(enabled: Boolean) { resetToolbarAutoHide(); prefsManager.setPanguSpacing(enabled) }
    fun setHeaderVisibility(visibility: HeaderVisibility) = prefsManager.setHeaderVisibility(visibility)
    fun setHeaderLeft(slot: SlotContent) = prefsManager.setHeaderLeft(slot)
    fun setHeaderCenter(slot: SlotContent) = prefsManager.setHeaderCenter(slot)
    fun setHeaderRight(slot: SlotContent) = prefsManager.setHeaderRight(slot)
    fun setFooterVisibility(visibility: HeaderVisibility) = prefsManager.setFooterVisibility(visibility)
    fun setFooterLeft(slot: SlotContent) = prefsManager.setFooterLeft(slot)
    fun setFooterCenter(slot: SlotContent) = prefsManager.setFooterCenter(slot)
    fun setFooterRight(slot: SlotContent) = prefsManager.setFooterRight(slot)
    fun setHeaderFooterAlpha(alpha: Float) = prefsManager.setHeaderFooterAlpha(alpha)
    fun setShowProgress(show: Boolean) = prefsManager.setShowProgress(show)
    fun setTitleAlign(align: TitleAlign) = prefsManager.setTitleAlign(align)
    fun setTitleSizeOffset(offsetSp: Int) = prefsManager.setTitleSizeOffset(offsetSp)
    fun setTitleMarginTop(dp: Float) = prefsManager.setTitleMarginTop(dp)
    fun setTitleMarginBottom(dp: Float) = prefsManager.setTitleMarginBottom(dp)
    fun setKeepScreenOn(enabled: Boolean) = prefsManager.setKeepScreenOn(enabled)
    fun setVolumeKeyTurnPage(enabled: Boolean) = prefsManager.setVolumeKeyTurnPage(enabled)
    fun setEdgeTurnPage(enabled: Boolean) = prefsManager.setEdgeTurnPage(enabled)
    fun setEdgeWidthPercent(percent: Float) = prefsManager.setEdgeWidthPercent(percent)
    fun setShowHeaderLine(show: Boolean) = prefsManager.setShowHeaderLine(show)
    fun setShowFooterLine(show: Boolean) = prefsManager.setShowFooterLine(show)
    fun setHeaderFontSizeRatio(ratio: Float) = prefsManager.setHeaderFontSizeRatio(ratio)
    fun setFooterFontSizeRatio(ratio: Float) = prefsManager.setFooterFontSizeRatio(ratio)
    fun setBottomJustify(enabled: Boolean) { resetToolbarAutoHide(); prefsManager.setBottomJustify(enabled) }
    fun setPageAnimType(type: PageDelegateFactory.PageAnimType) = prefsManager.setPageAnimType(type)

    // ── 工具栏/目录/搜索（直接管理或委托 navigationCoordinator）───

    fun toggleToolbar() = navigationCoordinator.toggleToolbar()
    fun resetToolbarAutoHide() = navigationCoordinator.resetToolbarAutoHide()
    fun toggleDirectory() = navigationCoordinator.toggleDirectory()
    fun toggleQuickSettings() = navigationCoordinator.toggleQuickSettings()

    fun toggleMenu() {
        _uiState.value = _uiState.value.copy(showMenu = !_uiState.value.showMenu)
    }

    fun toggleSearch() {
        val showing = !_uiState.value.showSearch
        _uiState.value = _uiState.value.copy(
            showSearch = showing,
            overlayPanel = OverlayPanel.NONE,
        )
        if (!showing) searchCoordinator.clearSearchResults()
    }

    fun handlePageDirection(direction: PageDelegate.Direction) {
        when (direction) {
            PageDelegate.Direction.NEXT -> nextPage()
            PageDelegate.Direction.PREV -> prevPage()
            PageDelegate.Direction.NONE -> { /* 忽略 */ }
        }
    }

    fun selectText(range: SelectionRange) {
        _uiState.value = _uiState.value.copy(selectedRange = range)
        _uiState.value.currentPage?.invalidate()
    }

    fun clearTextSelection() {
        _uiState.value = _uiState.value.copy(selectedRange = null)
        _uiState.value.currentPage?.invalidate()
    }

    fun addBookmarkFromSelection() = notesManager.addBookmarkFromSelection()
    fun addNoteFromSelection() = notesManager.addNoteFromSelection()

    // ── TTS（委托 ttsManager）─────────────────────────────────────

    fun startTts(config: TtsConfig = TtsConfig()) = ttsManager.startTts(config)
    fun pauseTts() = ttsManager.pauseTts()
    fun resumeTts() = ttsManager.resumeTts()
    fun stopTts() = ttsManager.stopTts()
    fun startSleepTimer(minutes: Int) = ttsManager.startSleepTimer(minutes)
    fun cancelSleepTimer() = ttsManager.cancelSleepTimer()
    fun pauseTtsOnBackground() = ttsManager.pauseTtsOnBackground()
    fun pauseReadingSession() = sessionManager.pauseReadingSession()
    fun resumeReadingSession() = sessionManager.resumeReadingSession()

    fun releaseReaderResources() {
        sessionManager.saveReadingProgress(immediate = true)
        val sessionElapsed = sessionManager.endSession()
        sessionManager.persistReadingTime(_uiState.value.bookId, sessionElapsed)
        sessionManager.release()
        chapterProvider.cancel()
        navigationCoordinator.release()
        ttsManager.release()
        notesManager.release()
        presetManager.release()
    }

    // ── 搜索（委托 searchCoordinator）────────────────────────────

    fun searchInCurrentBook(query: String) = searchCoordinator.searchInCurrentBook(query)
    fun setSearchResults(query: String, results: List<SearchResult>) = searchCoordinator.setSearchResults(query, results)
    fun goToNextSearchResult() = searchCoordinator.goToNextSearchResult()
    fun goToPreviousSearchResult() = searchCoordinator.goToPreviousSearchResult()

    // ── 书签笔记（委托 notesManager）─────────────────────────────

    fun addBookmark(selectedText: String? = null) = notesManager.addBookmark(selectedText)
    fun addBookmark(range: SelectionRange) = notesManager.addBookmark(range)
    fun deleteBookmark(bookmark: BookmarkEntity) = notesManager.deleteBookmark(bookmark)
    fun goToBookmark(bookmark: BookmarkEntity) = notesManager.goToBookmark(bookmark)
    fun loadBookmarks() = notesManager.loadBookmarks()
    fun addNote(startPos: Int, endPos: Int, content: String, color: String? = null) = notesManager.addNote(startPos, endPos, content, color)
    fun addNote(range: SelectionRange, content: String, color: String? = null) = notesManager.addNote(range, content, color)
    fun deleteNote(note: NoteEntity) = notesManager.deleteNote(note)
    fun updateNote(note: NoteEntity, newText: String, newColor: String? = note.color) = notesManager.updateNote(note, newText, newColor)
    fun goToNote(note: NoteEntity) = notesManager.goToNote(note)
    fun loadNotes() = notesManager.loadNotes()
    fun getVisibleNoteRanges(): List<Pair<SelectionRange, String?>> = notesManager.getVisibleNoteRanges()
    fun exportNotesAsMarkdown(): String? = notesManager.exportNotesAsMarkdown()

    // ── 预设（委托 presetManager）─────────────────────────────────

    fun loadPresets() = presetManager.loadPresets()
    fun saveCurrentAsPreset(name: String) = presetManager.saveCurrentAsPreset(name)
    fun applyPreset(presetId: Long) = presetManager.applyPreset(presetId)
    fun renamePreset(presetId: Long, newName: String) = presetManager.renamePreset(presetId, newName)
    fun deletePreset(presetId: Long) = presetManager.deletePreset(presetId)
    fun resetToDefault() = presetManager.resetToDefault()

    // ── TTS 句子拆分 ─────────────────────────────────────────────

    private fun sentenceRangesForCurrentPage(): List<SelectionRange> {
        val page = _uiState.value.currentPage ?: return emptyList()
        val content = _uiState.value.currentChapter?.content ?: return emptyList()
        if (page.lines.isEmpty()) return emptyList()

        val fullText = StringBuilder()
        val lineOffsets = mutableListOf<Int>()
        for (line in page.lines) {
            lineOffsets.add(fullText.length)
            fullText.append(content, line.startCharOffset, line.endCharOffset)
            if (!line.isParagraphEnd) fullText.append('\n')
        }

        val ranges = mutableListOf<SelectionRange>()
        var start = 0
        val text = fullText.toString()
        text.forEachIndexed { index, char ->
            if (char.isSentenceTerminator()) {
                val sentenceText = text.substring(start, index + 1).trim()
                if (sentenceText.isNotBlank()) {
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
        return this == '.' || this == '!' || this == '?' ||
            this == '。' || this == '！' || this == '？' || this == '…'
    }

    // ── Reflow ────────────────────────────────────────────────────

    private fun reflowCurrentChapter(preferences: ReaderPreferences) {
        currentLayoutHash = paginationCoordinator.computeLayoutHash(preferences)
        paginationCoordinator.reflowCurrentChapter(preferences, currentLayoutHash)
    }

    // ── 进度计算与页眉脚解析 ─────────────────────────────────────

    private fun computeSynchronousBookProgress(): Triple<Long, Long, Float> {
        val state = _uiState.value
        val wordCounts = state.chapterWordCounts
        val pageCounts = state.chapterPageCounts
        val currentChapterIndex = state.chapterIndex
        val currentPages = state.totalPages.coerceAtLeast(1)
        val totalChapters = state.totalChapters.coerceAtLeast(1)

        var sampledWords = 0L
        var sampledPages = 0L
        for ((i, p) in pageCounts) {
            if (p > 0) {
                sampledWords += (wordCounts.getOrNull(i) ?: 0).toLong()
                sampledPages += p.toLong()
            }
        }
        val currentChapterWords = wordCounts.getOrNull(currentChapterIndex)?.coerceAtLeast(1) ?: 1
        if (sampledPages == 0L) sampledPages = currentPages.toLong()
        if (sampledWords == 0L) sampledWords = currentChapterWords.toLong()
        val wordsPerPage = sampledWords.toDouble() / sampledPages

        if (wordCounts.isEmpty() && pageCounts.isEmpty()) {
            val progress = ((currentChapterIndex + state.pageIndex.toFloat() / currentPages) / totalChapters).coerceIn(0f, 1f)
            return Triple((currentChapterIndex + 1).toLong(), totalChapters.toLong(), progress)
        }

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
                batteryLevel = 100,
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
                batteryLevel = 100,
            )
        }

        return Pair(header, footer)
    }

    override fun onCleared() {
        releaseReaderResources()
        super.onCleared()
    }
}
