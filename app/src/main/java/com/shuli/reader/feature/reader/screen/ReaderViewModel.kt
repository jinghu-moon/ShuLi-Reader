package com.shuli.reader.feature.reader.screen
import com.shuli.reader.feature.reader.session.BookSessionManager
import com.shuli.reader.feature.reader.session.ChapterPaginationCoordinator
import com.shuli.reader.feature.reader.session.ReaderNavigationCoordinator
import com.shuli.reader.feature.reader.session.ReaderProgressResolver
import com.shuli.reader.feature.reader.session.ReaderSearchManager
import com.shuli.reader.feature.reader.session.BookmarkNotesManager
import com.shuli.reader.feature.reader.session.ReaderPresetManager
import com.shuli.reader.feature.reader.session.FontImportManager
import com.shuli.reader.feature.reader.settings.ReaderPreferenceMonitor
import com.shuli.reader.feature.reader.settings.ReaderSettingsManager
import com.shuli.reader.feature.reader.settings.SettingsScope

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.data.toFactoryType
import com.shuli.reader.core.data.toLayoutConfig
import com.shuli.reader.core.reader.model.HeaderVisibility
import com.shuli.reader.core.reader.model.SlotContent
import com.shuli.reader.core.reader.model.TitleAlign
import com.shuli.reader.core.database.dao.BookmarkDao
import com.shuli.reader.core.database.dao.NoteDao
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import com.shuli.reader.feature.reader.session.ChapterProvider
import com.shuli.reader.core.reader.engine.Paginator
import com.shuli.reader.feature.reader.session.ReadingStateManager
import com.shuli.reader.core.reader.text.SimpleTextMeasurer
import com.shuli.reader.core.reader.engine.animation.PageDelegate
import com.shuli.reader.core.reader.engine.animation.PageDelegateFactory
import com.shuli.reader.core.reader.engine.cache.CacheManager
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.core.repository.BookContentRepository
import com.shuli.reader.core.repository.BookQueryRepository
import com.shuli.reader.core.repository.ReadingProgressRepository
import com.shuli.reader.core.repository.SearchIndexRepository
import com.shuli.reader.core.repository.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import com.shuli.reader.core.reading.ReadingStatus
import com.shuli.reader.core.repository.ReadingStatusUpdateResult
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.toBookItem
import com.shuli.reader.feature.reader.settings.toChromePrefs
import com.shuli.reader.feature.reader.settings.toLayoutPrefs
import com.shuli.reader.feature.reader.settings.toOverlayPrefs
import com.shuli.reader.feature.reader.settings.toStylePrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * 阅读器 ViewModel — 聚合子模块，暴露公共 API 给 UI 层。
 *
 * 子模块（均 internal）：
 * - [bookSessionManager] 书籍会话与章节加载
 * - [chapterPaginationCoordinator] 分页与 reflow
 * - [navigationCoordinator] 翻页、工具栏、目录
 * - [readerSettingsManager] 偏好读写
 * - [bookmarkNotesManager] 书签/笔记 CRUD
 * - [readerSearchManager] 正文搜索
 * - [readerPresetManager] 预设管理
 * - [readerProgressResolver] 进度/页眉页脚解析
 * - [preferenceMonitor] 偏好变化监听
 * - [fontImportManager] 字体导入
 */
class ReaderViewModel(
    private val bookId: Long = 0L,
    private val userPreferences: UserPreferences? = null,
    private val bookContentRepository: BookContentRepository? = null,
    private val bookQueryRepository: BookQueryRepository? = null,
    private val readingProgressRepository: ReadingProgressRepository? = null,
    private val searchIndexRepository: SearchIndexRepository? = null,
    private val tagRepository: com.shuli.reader.core.repository.TagRepository? = null,
    private val bookmarkDao: BookmarkDao? = null,
    private val noteDao: NoteDao? = null,
    private val presetDao: com.shuli.reader.core.database.dao.ReaderPresetDao? = null,
    private val bookReaderPrefsDao: com.shuli.reader.core.database.dao.BookReaderPrefsDao? = null,
    private val readingProgressDao: com.shuli.reader.core.database.dao.ReadingProgressDao? = null,
    private val chapterReadingStatsDao: com.shuli.reader.core.database.dao.ChapterReadingStatsDao? = null,
    private val readingSessionDao: com.shuli.reader.core.database.dao.ReadingSessionDao? = null,
    private val dictMetaDao: com.shuli.reader.core.database.dao.DictMetaDao? = null,
    private val dictHistoryDao: com.shuli.reader.core.database.dao.DictHistoryDao? = null,
    private val wordBookDao: com.shuli.reader.core.database.dao.WordBookDao? = null,
    private val paginator: Paginator = Paginator(SimpleTextMeasurer()),
    private val fontManager: com.shuli.reader.core.font.FontManager? = null,
    private val stringResolver: () -> com.shuli.reader.core.i18n.AppStrings = { com.shuli.reader.core.i18n.AppStrings.ZhHans },
    private val appContext: android.content.Context? = null,
) : ViewModel() {

    companion object {
        private const val TAG = "ReaderPerf"
    }

    /** 性能诊断：记录各阶段耗时 */
    private fun logPerf(label: String, startMs: Long) {
        if (com.shuli.reader.BuildConfig.DEBUG) {
            android.util.Log.d(TAG, "$label: ${System.currentTimeMillis() - startMs}ms")
        }
    }

    private val _uiState = MutableStateFlow(ReaderUiState(bookId = bookId))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /**
     * 页面渲染状态（中频变化）。
     *
     * 供 AndroidView.update 观察，避免 toolbar/搜索/预设列表等 UI 变化触发 recomposition。
     * 当前由 uiState 派生；后续 ViewModel 迁移完成后将独立更新。
     */
    val pageState: StateFlow<ReaderPageState> = _uiState
        .map {
            ReaderPageState(
                bookId = it.bookId,
                bookTitle = it.bookTitle,
                chapterTitle = it.chapterTitle,
                currentPage = it.currentPage,
                currentChapter = it.currentChapter,
                chapterIndex = it.chapterIndex,
                pageIndex = it.pageIndex,
                totalPages = it.totalPages,
                totalChapters = it.totalChapters,
                pageAnimType = it.pageAnimType,
                pageRenderMode = it.pageRenderMode,
                chapterTitles = it.chapterTitles,
                chapterWordCounts = it.chapterWordCounts,
                chapterPageCounts = it.chapterPageCounts,
                layoutVersion = it.layoutVersion,
                isReflowing = it.isReflowing,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ReaderPageState(bookId = bookId),
        )

    /**
     * 覆盖层状态（高频变化：选区/睡眠计时）。
     */
    val overlayState: StateFlow<ReaderOverlayState> = _uiState
        .map {
            ReaderOverlayState(
                selectedRange = it.selectedRange,
                selectionScreenX = it.selectionScreenX,
                selectionScreenY = it.selectionScreenY,
                sleepTimerRemainingSeconds = it.sleepTimerRemainingSeconds,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ReaderOverlayState(),
        )

    // ── 四层 StateFlow 拆分（v5.1 §0a.5）──────────────────
    // 每层独立 map + distinctUntilChanged + stateIn，避免高频层变化触发低频层 recomposition。

    val overlayPrefs: StateFlow<com.shuli.reader.feature.reader.settings.OverlayPrefs> = _uiState
        .map { state -> state.readerPreferences.toOverlayPrefs() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderPreferences().toOverlayPrefs())

    val chromePrefs: StateFlow<com.shuli.reader.feature.reader.settings.ChromePrefs> = _uiState
        .map { state -> state.readerPreferences.toChromePrefs() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderPreferences().toChromePrefs())

    val stylePrefs: StateFlow<com.shuli.reader.feature.reader.settings.StylePrefs> = _uiState
        .map { state -> state.readerPreferences.toStylePrefs() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderPreferences().toStylePrefs())

    val layoutPrefs: StateFlow<com.shuli.reader.feature.reader.settings.LayoutPrefs> = _uiState
        .map { state -> state.readerPreferences.toLayoutPrefs() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderPreferences().toLayoutPrefs())

    // ── 护眼提醒可见性（v5.1 §1.3）──────────────────────
    private val _eyeCareReminderVisible = MutableStateFlow(false)
    val eyeCareReminderVisible: StateFlow<Boolean> = _eyeCareReminderVisible.asStateFlow()

    fun dismissEyeCareReminder() {
        _eyeCareReminderVisible.value = false
    }

    // ── 系统 WindowInsets（v5.1 §0a.14）──────────────────
    private val _systemBottomInset = MutableStateFlow(0)
    val systemBottomInset: StateFlow<Int> = _systemBottomInset.asStateFlow()

    fun updateSystemBottomInset(insetPx: Int) {
        _systemBottomInset.value = insetPx
    }

    // reflow 防抖 Job
    private var reflowJob: Job? = null

    // ── 子模块（internal，UI 层可直接访问） ──────────────────

    /** 书签/笔记管理器 */
    internal val bookmarkNotesManager: BookmarkNotesManager by lazy {
        BookmarkNotesManager(
            bookmarkDao = bookmarkDao,
            noteDao = noteDao,
            uiState = _uiState,
            scope = viewModelScope,
            loadedBookContent = { loadedBookContent },
            charToByteOffset = { charToByteOffset(it) },
            byteToCharOffset = { byteToCharOffset(it) },
            jumpToChapterPosition = { chapterIndex, byteOffset -> jumpToChapterPosition(chapterIndex, byteOffset) },
        )
    }

    /** 章节分页协调器 */
    internal val chapterPaginationCoordinator: ChapterPaginationCoordinator by lazy {
        ChapterPaginationCoordinator(
            cacheManager = cacheManager,
            paginator = paginator,
            uiState = _uiState,
            scope = viewModelScope,
            bookContentRepository = bookContentRepository,
            currentBookFilePath = { currentBookFilePath },
            layoutConfigFor = { layoutConfigFor(it) },
            persistPageCounts = { persistPageCounts() },
            computeLayoutHash = { computeLayoutHash(it) },
            normalizeChapters = { normalizedChapters() },
            getChapterText = { chapterText(it) },
            loadedBookContentProvider = { loadedBookContent },
            cachedChapterTextProvider = { cachedChapterText },
            clearCachedChapterText = { cachedChapterText = null },
            onReflowStart = { hash -> currentLayoutHash = hash },
            logPerf = { label, startMs -> logPerf(label, startMs) },
            editStoreProvider = { editStore },
        )
    }

    // M1: 章节加载 Job
    private var chapterJob: Job? = null

    // T-41: 当前排版参数的哈希值
    private var currentLayoutHash: String = ""
    private var lastPersistedCounts: Map<Int, Int> = emptyMap()
    private var persistJob: Job? = null

    // R7: 章节缓存管理器
    private var cacheManager: CacheManager = CacheManager.forMemoryClass(256)

    // R7: 章节提供器
    private val chapterProvider = ChapterProvider(paginator)

    // R7: 阅读状态管理器
    private lateinit var readingStateManager: ReadingStateManager

    /** 字体导入管理器 */
    internal val fontImportManager: FontImportManager by lazy {
        FontImportManager(
            fontManager = fontManager,
            uiState = _uiState,
            scope = viewModelScope,
            stringResolver = stringResolver,
        )
    }

    /** 预设管理器 */
    internal val readerPresetManager: ReaderPresetManager by lazy {
        ReaderPresetManager(
            presetDao = presetDao,
            uiState = _uiState,
            scope = viewModelScope,
            applyPreferences = { prefs -> applyPreferencesFromPreset(prefs) },
        )
    }

    init {
        readingStateManager = ReadingStateManager(
            scope = viewModelScope,
            saveAction = { state -> },
        )
        readerPresetManager.loadPresets()
        fontImportManager.loadCustomFonts()
    }

    /** 翻页动画委托 */
    var pageDelegate: PageDelegate = PageDelegateFactory.create(
        _uiState.value.pageAnimType,
        _uiState.value.readerPreferences.pageAnimSpeed,
    )
        internal set

    private var loadedBookContent: BookContent? = null
    private var currentBookFilePath: String? = null
    private var isCurrentBookEpub: Boolean = false
    private var currentChapterUtf16Map: IntArray = IntArray(0)
    private var cachedChapterText: String? = null

    /** 导航协调器 */
    internal val navigationCoordinator: ReaderNavigationCoordinator by lazy {
        ReaderNavigationCoordinator(
            uiState = _uiState,
            scope = viewModelScope,
            appContext = appContext,
            stringResolver = stringResolver,
            saveReadingProgress = { saveReadingProgress(it) },
            openChapter = { index, toLast, byteOffset -> openChapter(index, toLast, byteOffset) },
            clearSearchResults = { clearSearchResults() },
        )
    }

    /** 进度解析器 */
    internal val readerProgressResolver: ReaderProgressResolver by lazy {
        ReaderProgressResolver(uiState = _uiState)
    }

    /** 搜索管理器 */
    internal val readerSearchManager: ReaderSearchManager by lazy {
        ReaderSearchManager(
            uiState = _uiState,
            scope = viewModelScope,
            searchIndexRepository = searchIndexRepository,
            jumpToChapterPosition = { chapterIndex, byteOffset -> jumpToChapterPosition(chapterIndex, byteOffset) },
        )
    }

    /** 偏好设置管理器 */
    internal val readerSettingsManager: ReaderSettingsManager by lazy {
        ReaderSettingsManager(
            uiState = _uiState,
            scope = viewModelScope,
            userPreferences = userPreferences,
            bookReaderPrefsDao = bookReaderPrefsDao,
            reflowCurrentChapter = { prefs -> reflowCurrentChapter(prefs) },
            resetToolbarAutoHide = { navigationCoordinator.resetToolbarAutoHide() },
        )
    }

    /** 词典管理器 */
    internal val dictionaryManager: com.shuli.reader.core.dictionary.manager.DictionaryManager? by lazy {
        val ctx = appContext ?: return@lazy null
        val metaDao = dictMetaDao ?: return@lazy null
        val historyDao = dictHistoryDao ?: return@lazy null
        val bookDao = wordBookDao ?: return@lazy null
        com.shuli.reader.core.dictionary.manager.DictionaryManager(ctx, metaDao, historyDao, bookDao)
    }

    /** 书籍会话管理器 */
    internal val bookSessionManager: BookSessionManager by lazy {
        BookSessionManager(
            uiState = _uiState,
            scope = viewModelScope,
            bookQueryRepository = bookQueryRepository,
            searchIndexRepository = searchIndexRepository,
            bookContentRepository = bookContentRepository,
            readingProgressRepository = readingProgressRepository,
            readingProgressDao = readingProgressDao,
            chapterReadingStatsDao = chapterReadingStatsDao,
            readingSessionDao = readingSessionDao,
            cacheManager = { cacheManager },
            setCacheManager = { cacheManager = it },
            readingStateManager = { readingStateManager },
            stringResolver = stringResolver,
            appContext = appContext,
            loadedBookContentProvider = { loadedBookContent },
            setLoadedBookContent = { loadedBookContent = it },
            currentBookFilePathProvider = { currentBookFilePath },
            setCurrentBookFilePath = { currentBookFilePath = it },
            isCurrentBookEpubProvider = { isCurrentBookEpub },
            setIsCurrentBookEpub = { isCurrentBookEpub = it },
            currentChapterUtf16MapProvider = { currentChapterUtf16Map },
            setCurrentChapterUtf16Map = { currentChapterUtf16Map = it },
            cachedChapterTextProvider = { cachedChapterText },
            setCachedChapterText = { cachedChapterText = it },
            currentLayoutHashProvider = { currentLayoutHash },
            setCurrentLayoutHash = { currentLayoutHash = it },
            paginateChapterStreaming = { content, index, targetCharOffset, onDone ->
                paginateChapterStreaming(content, index, targetCharOffset, onDone)
            },
            preloadAdjacentChapters = { content, index ->
                preloadAdjacentChapters(content, index)
                // 异步预加载启动后，先同步查一次缓存（命中则立即填充；未命中由 onChapterLoaded 或下一次翻页兜底）
                refreshCrossChapterPages()
            },
            loadBookmarks = { bookmarkNotesManager.loadBookmarks() },
            loadNotes = { bookmarkNotesManager.loadNotes() },
            onChapterLoaded = { refreshCrossChapterPages() },
            logPerf = { label, startMs -> logPerf(label, startMs) },
            byteToCharOffset = { byteToCharOffset(it) },
            charToByteOffset = { charToByteOffset(it) },
            computeLayoutHash = { computeLayoutHash(it) },
        )
    }

    var density: Float = 3f
        private set

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

    /**
     * 设置选区滚动偏移（防遮挡用）
     *
     * 当选中词在下半屏时，BottomSheet 弹出前自动上滚 Canvas
     */
    fun setSelectionScrollOffset(offset: Float) {
        _uiState.value = _uiState.value.copy(selectionScrollOffset = offset)
    }

    /**
     * 清除选区滚动偏移
     */
    fun clearSelectionScrollOffset() {
        _uiState.value = _uiState.value.copy(selectionScrollOffset = 0f)
    }

    // ── 字节↔字符坐标桥接（v4） ──────────────────────────

    private fun charToByteOffset(charIndex: Int): Int {
        val map = currentChapterUtf16Map
        if (map.isEmpty()) return charIndex
        val idx = charIndex.coerceIn(0, map.size - 1)
        return map[idx]
    }

    private fun byteToCharOffset(byteOffset: Int): Int {
        val map = currentChapterUtf16Map
        if (map.isEmpty()) return byteOffset
        for (i in map.indices) {
            if (map[i] > byteOffset) return (i - 1).coerceAtLeast(0)
        }
        return map.size - 1
    }

    // 偏好监控
    private val preferenceMonitor: ReaderPreferenceMonitor? = userPreferences?.let {
        ReaderPreferenceMonitor(
            userPreferences = it,
            uiState = _uiState,
            scope = viewModelScope,
            reflowCurrentChapter = { prefs -> reflowCurrentChapter(prefs) },
            onPageAnimTypeChanged = { delegate -> pageDelegate = delegate },
        )
    }

    /**
     * 首次 syncFromDataStore 的 Deferred。
     *
     * - lazy：仅在 init 或 openBook 首次访问时启动，避免 preferenceMonitor 尚未初始化的风险。
     * - 启动后只执行一次；后续 openBook 仅 await，不重复 sync。
     */
    private val initialSync: Deferred<Unit> by lazy {
        viewModelScope.async { preferenceMonitor?.let { it.syncFromDataStore() } ?: Unit }
    }

    init {
        preferenceMonitor?.start()
        // 提前触发首次 syncFromDataStore：让 uiState 在首帧 / openBook 之前就拿到
        // DataStore 中保存的真实偏好（含 textAlign），避免首帧/dialog 使用 ReaderPreferences
        // 的 data class 默认值（LEFT、fontSize=16 等）与持久化值（可能是 JUSTIFY）不一致。
        initialSync
    }

    // ── 真实逻辑方法（非纯委托） ──────────────────────────

    fun openBook(bookId: Long) {
        // 等待首次 syncFromDataStore 完成，确保分页 / renderer 用持久化值，
        // 而不是 ReaderPreferences 的 data class 默认值。
        viewModelScope.launch {
            initialSync.await()
            bookSessionManager.openBook(bookId)
            // 加载本书级偏好覆盖（如有）
            readerSettingsManager.loadBookOverrides(bookId)
            // 预加载词典索引
            dictionaryManager?.initialize()
            // 设置编辑器的 bookId（用于崩溃恢复）
            editStore.setBookId(bookId)
        }
    }

    /**
     * 统一意图入口 —— 所有 UI / 快捷键 / 自动翻页操作通过此方法分发。
     *
     * 编译器强制穷举：新增 [ReaderIntent] 子类时必须在此处添加处理分支。
     */
    fun dispatch(intent: ReaderIntent) {
        when (intent) {
            // ── 导航 ──
            is ReaderIntent.OpenBook -> openBook(intent.bookId)
            is ReaderIntent.OpenChapter -> openChapter(intent.index, intent.targetToLastPage, intent.targetByteOffset)
            is ReaderIntent.TurnPage -> turnPageAnimated(intent.direction)
            is ReaderIntent.CommitPageTurn -> commitPageTurn(intent.direction)
            is ReaderIntent.NextPage -> nextPage()
            is ReaderIntent.PrevPage -> prevPage()
            is ReaderIntent.JumpToPosition -> jumpToChapterPosition(intent.chapterIndex, intent.byteOffset)

            // ── UI 开关 ──
            is ReaderIntent.ToggleToolbar -> navigationCoordinator.toggleToolbar()
            is ReaderIntent.ToggleDirectory -> navigationCoordinator.toggleDirectory()
            is ReaderIntent.ToggleQuickSettings -> navigationCoordinator.toggleQuickSettings()
            is ReaderIntent.OpenGestureZoneEditor -> navigationCoordinator.openGestureZoneEditor()
            is ReaderIntent.CloseGestureZoneEditor -> navigationCoordinator.closeGestureZoneEditor()
            is ReaderIntent.ToggleSearch -> navigationCoordinator.toggleSearch()
            is ReaderIntent.ClearSelection -> navigationCoordinator.clearTextSelection()

            // ── 选区操作 ──
            is ReaderIntent.AddBookmarkFromSelection -> addBookmarkFromSelection()
            is ReaderIntent.AddNoteFromSelection -> addNoteFromSelection()
            is ReaderIntent.AddBookmark -> bookmarkNotesManager.addBookmark()

            // ── 划词查词 ──
            is ReaderIntent.LookupWord -> lookupWord(intent.word, intent.contextSentence)
            is ReaderIntent.DismissDictionary -> dismissDictionary()
            is ReaderIntent.AddToWordBook -> addToWordBook(intent.word)

            // ── 文本编辑 ──
            is ReaderIntent.OpenTextEdit -> openTextEdit()
            is ReaderIntent.CloseTextEdit -> closeTextEdit()
            is ReaderIntent.InlineEdit -> enterInlineEdit(intent.text, intent.anchor)
            is ReaderIntent.CursorEdit -> enterCursorEdit(intent.anchor, intent.screenX, intent.screenY)
            is ReaderIntent.ConfirmInlineEdit -> confirmInlineEdit(intent.newText)
            is ReaderIntent.CancelInlineEdit -> cancelInlineEdit()
            is ReaderIntent.ConfirmCursorEdit -> confirmCursorEdit(intent.newText)
            is ReaderIntent.CancelCursorEdit -> cancelCursorEdit()
            is ReaderIntent.FindNext -> findNext()
            is ReaderIntent.FindPrev -> findPrev()
            is ReaderIntent.ReplaceCurrent -> replaceCurrent(intent.replacement)
            is ReaderIntent.ReplaceAll -> replaceAll(intent.find, intent.replace, intent.isRegex)
            is ReaderIntent.UndoEdit -> undoEdit()
            is ReaderIntent.RedoEdit -> redoEdit()
            is ReaderIntent.SaveEdits -> saveEdits()

            // ── 设置 ──
            is ReaderIntent.UpdateSetting -> dispatchSetting(intent.key, intent.value)
            is ReaderIntent.UpdateContinuousSetting -> dispatchContinuousSetting(
                intent.key,
                intent.value,
                intent.finished,
            )
            is ReaderIntent.CycleTheme -> readerSettingsManager.cycleTheme()
            is ReaderIntent.ResetSettingsToDefault -> readerPresetManager.resetToDefault()

            // ── 设置作用域 ──
            is ReaderIntent.SetSettingsScope -> {
                if (intent.scope == SettingsScope.BOOK &&
                    uiState.value.settingsScope == SettingsScope.GLOBAL
                ) {
                    // 从全局切换到本书：将当前全局值保存为本书覆盖
                    readerSettingsManager.copyGlobalToBook()
                } else {
                    readerSettingsManager.setSettingsScope(intent.scope)
                }
            }
            is ReaderIntent.ResetBookOverrides -> readerSettingsManager.resetBookOverrides()
            is ReaderIntent.CopyGlobalToBook -> readerSettingsManager.copyGlobalToBook()

            // ── 预设 ──
            is ReaderIntent.ApplyPreset -> readerPresetManager.applyPreset(intent.presetId)
            is ReaderIntent.SavePreset -> readerPresetManager.saveCurrentAsPreset(intent.name)
            is ReaderIntent.RenamePreset -> readerPresetManager.renamePreset(intent.id, intent.name)
            is ReaderIntent.DeletePreset -> readerPresetManager.deletePreset(intent.presetId)

            // ── 搜索 ──
            is ReaderIntent.Search -> readerSearchManager.searchInCurrentBook(intent.query)
            is ReaderIntent.NextSearchResult -> readerSearchManager.goToNextSearchResult()
            is ReaderIntent.PrevSearchResult -> readerSearchManager.goToPreviousSearchResult()

            // ── 页面拖动 ──
            is ReaderIntent.StartPageScrub -> navigationCoordinator.startPageScrub()
            is ReaderIntent.ScrubToPage -> navigationCoordinator.scrubToPage(intent.pageIndex)
            is ReaderIntent.CommitPageScrub -> navigationCoordinator.commitPageScrub()

            // ── 屏幕 / 排版 ──
            is ReaderIntent.SetScreenSize -> setScreenSize(intent.width, intent.height)
            is ReaderIntent.SetPageAnimType -> {
                // 用户手动切换翻页类型，清除自动保存（避免退出编辑时覆盖用户选择）
                savedPageAnimType = null
                navigationCoordinator.setPageAnimType(
                    intent.type.toFactoryType(),
                    _uiState.value.readerPreferences.pageAnimSpeed,
                ) { pageDelegate = it }
                // 持久化到 DataStore，确保下拉菜单显示正确、重启后生效
                readerSettingsManager.setPageAnimType(intent.type)
            }

            // ── 字体 ──
            is ReaderIntent.ImportFont -> fontImportManager.importFont(intent.uri)
            is ReaderIntent.DeleteFont -> fontImportManager.deleteFont(intent.fontKey)
        }
    }

    /**
     * 连续控件分发 —— 仅 BRIGHTNESS / COLOR_TEMPERATURE 支持 finished 标志，
     * 其他键回退到 [dispatchSetting]（默认始终持久化）。
     */
    private fun dispatchContinuousSetting(
        key: ReaderSettingKey,
        value: ReaderSettingValue,
        finished: Boolean,
    ) {
        val s = readerSettingsManager
        when (key) {
            ReaderSettingKey.BRIGHTNESS -> s.setBrightness(
                (value as ReaderSettingValue.Float).value,
                finished = finished,
            )
            ReaderSettingKey.COLOR_TEMPERATURE -> s.setColorTemperature(
                (value as ReaderSettingValue.Float).value,
                finished = finished,
            )
            else -> dispatchSetting(key, value)
        }
    }

    /**
     * 设置分发 —— 将 [ReaderSettingKey] + [ReaderSettingValue] 映射到对应 setter。
     */
    private fun dispatchSetting(key: ReaderSettingKey, value: ReaderSettingValue) {
        val s = readerSettingsManager
        when (key) {
            ReaderSettingKey.FONT_SIZE -> s.setFontSize((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.LINE_SPACING -> s.setLineSpacing((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.PARAGRAPH_SPACING -> s.setParagraphSpacing((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.INDENT -> s.setIndent((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.INDENT_UNIT -> s.setIndentUnit((value as ReaderSettingValue.IndentUnit).value)
            ReaderSettingKey.BODY_BOX -> s.setBodyBox((value as ReaderSettingValue.BoxInsetsDpVal).value)
            ReaderSettingKey.HEADER_BOX -> s.setHeaderBox((value as ReaderSettingValue.BoxInsetsDpVal).value)
            ReaderSettingKey.FOOTER_BOX -> s.setFooterBox((value as ReaderSettingValue.BoxInsetsDpVal).value)
            ReaderSettingKey.TITLE_BOX -> s.setTitleBox((value as ReaderSettingValue.BoxInsetsDpVal).value)
            ReaderSettingKey.LETTER_SPACING -> s.setLetterSpacing((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.READING_FONT -> s.setReadingFont((value as ReaderSettingValue.Str).value)
            ReaderSettingKey.FONT_WEIGHT -> s.setFontWeight((value as ReaderSettingValue.FontWeight).value)
            ReaderSettingKey.TEXT_ALIGN -> s.setTextAlign((value as ReaderSettingValue.TextAlign).value)
            ReaderSettingKey.THEME -> s.setReaderTheme((value as ReaderSettingValue.Theme).value)
            ReaderSettingKey.BRIGHTNESS -> s.setBrightness((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.CHINESE_CONVERT -> s.setChineseConvert((value as ReaderSettingValue.ChineseConvert).value)
            ReaderSettingKey.USE_ZH_LAYOUT -> s.setUseZhLayout((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.USE_PANGU_SPACING -> s.setPanguSpacing((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.BOTTOM_JUSTIFY -> s.setBottomJustify((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.HEADER_VISIBILITY -> s.setHeaderVisibility((value as ReaderSettingValue.HeaderVisibility).value)
            ReaderSettingKey.HEADER_LEFT -> s.setHeaderLeft((value as ReaderSettingValue.SlotContent).value)
            ReaderSettingKey.HEADER_CENTER -> s.setHeaderCenter((value as ReaderSettingValue.SlotContent).value)
            ReaderSettingKey.HEADER_RIGHT -> s.setHeaderRight((value as ReaderSettingValue.SlotContent).value)
            ReaderSettingKey.FOOTER_VISIBILITY -> s.setFooterVisibility((value as ReaderSettingValue.HeaderVisibility).value)
            ReaderSettingKey.FOOTER_LEFT -> s.setFooterLeft((value as ReaderSettingValue.SlotContent).value)
            ReaderSettingKey.FOOTER_CENTER -> s.setFooterCenter((value as ReaderSettingValue.SlotContent).value)
            ReaderSettingKey.FOOTER_RIGHT -> s.setFooterRight((value as ReaderSettingValue.SlotContent).value)
            ReaderSettingKey.HEADER_FOOTER_ALPHA -> s.setHeaderFooterAlpha((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.SHOW_PROGRESS -> s.setShowProgress((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.SHOW_HEADER_LINE -> s.setShowHeaderLine((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.SHOW_FOOTER_LINE -> s.setShowFooterLine((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.HEADER_FONT_SIZE_RATIO -> s.setHeaderFontSizeRatio((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.FOOTER_FONT_SIZE_RATIO -> s.setFooterFontSizeRatio((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.TITLE_ALIGN -> s.setTitleAlign((value as ReaderSettingValue.TitleAlign).value)
            ReaderSettingKey.TITLE_SIZE_OFFSET -> s.setTitleSizeOffset((value as ReaderSettingValue.Int).value)
            ReaderSettingKey.TITLE_MARGIN_TOP -> s.setTitleMarginTop((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.TITLE_MARGIN_BOTTOM -> s.setTitleMarginBottom((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.TITLE_FONT_SIZE -> s.setTitleFontSize((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.KEEP_SCREEN_ON -> s.setKeepScreenOn((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.VOLUME_KEY_TURN_PAGE -> s.setVolumeKeyTurnPage((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.EDGE_TURN_PAGE -> s.setEdgeTurnPage((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.EDGE_WIDTH_PERCENT -> s.setEdgeWidthPercent((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.IMMERSIVE_MODE -> s.setImmersiveMode((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.REMOVE_EMPTY_LINES -> s.setRemoveEmptyLines((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.CLEAN_CHAPTER_TITLE -> s.setCleanChapterTitle((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.PROGRESS_STYLE -> s.setProgressStyle((value as ReaderSettingValue.ProgressStyle).value)
            ReaderSettingKey.AUTO_PAGE_TURN -> s.setAutoPageTurn((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.AUTO_PAGE_TURN_INTERVAL -> s.setAutoPageTurnInterval((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.EPUB_OVERRIDE_STYLE -> s.setEpubOverrideStyle((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.PRESERVE_ORIGINAL_INDENT -> s.setPreserveOriginalIndent((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.LEFT_ZONE_RATIO -> s.setLeftZoneRatio((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.CUSTOM_THEME_COLOR -> {
                val v = value as ReaderSettingValue.CustomThemeColor
                s.setCustomThemeColor(v.backgroundColor, v.textColor, v.titleColor, v.headerFooterColor)
            }
            // v5.1 Phase 1-4 新增设置（setter 待各 Phase 实现，暂用通用更新）
            ReaderSettingKey.COLOR_TEMPERATURE -> s.setColorTemperature(
                (value as ReaderSettingValue.Float).value,
                finished = true,
            )
            ReaderSettingKey.PARAGRAPH_DIVIDER -> s.setParagraphDivider(
                (value as ReaderSettingValue.Bool).value,
                reflow = true,
            )
            ReaderSettingKey.BIONIC_READING -> s.setBionicReading(
                (value as ReaderSettingValue.Bool).value,
                reflow = true,
            )
            ReaderSettingKey.VERTICAL_TEXT -> s.setVerticalText(
                (value as ReaderSettingValue.Bool).value,
                reflow = true,
            )
            ReaderSettingKey.DUAL_PAGE_MODE -> s.updatePrefsGeneric(
                { it.copy(dualPageMode = (value as ReaderSettingValue.DualPageMode).value) },
                reflow = true,
            )
            ReaderSettingKey.HAPTIC_FEEDBACK -> s.setHapticFeedback((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.ORIENTATION_LOCK -> s.setOrientationLock((value as ReaderSettingValue.OrientationLock).value)
            ReaderSettingKey.PAGE_ANIM_TYPE -> {
                val type = (value as ReaderSettingValue.PageAnimType).value
                savedPageAnimType = null
                navigationCoordinator.setPageAnimType(
                    type.toFactoryType(),
                    _uiState.value.readerPreferences.pageAnimSpeed,
                ) { pageDelegate = it }
                s.setPageAnimType(type)
            }
            ReaderSettingKey.PAGE_ANIM_SPEED -> {
                val speed = (value as ReaderSettingValue.PageAnimSpeed).value
                s.setPageAnimSpeed(speed)
                navigationCoordinator.setPageAnimType(_uiState.value.pageAnimType, speed) { pageDelegate = it }
            }
            ReaderSettingKey.AD_FILTERING -> s.setAdFiltering(
                (value as ReaderSettingValue.Bool).value,
                reflow = true,
            )
            ReaderSettingKey.EYE_CARE_REMINDER_INTERVAL -> s.setEyeCareReminderInterval(
                (value as ReaderSettingValue.Int).value,
            )
            ReaderSettingKey.BACKGROUND_TEXTURE -> s.setBackgroundTexture((value as ReaderSettingValue.Str).value)
            ReaderSettingKey.GESTURE_CONFIG -> s.updatePrefsGeneric(
                { it.copy(gestureConfig = (value as ReaderSettingValue.GestureConfigValue).value) },
                reflow = false,
            )
        }
    }

    fun nextPage() = turnPageAnimated(PageDirection.NEXT)

    fun prevPage() = turnPageAnimated(PageDirection.PREV)

    private fun turnPageAnimated(direction: PageDirection) {
        if (pageDelegate.state != PageDelegate.State.IDLE) return

        if (!canTurnPage(direction)) {
            commitPageTurn(direction)
            return
        }

        when (direction) {
            PageDirection.NEXT -> pageDelegate.startNext()
            PageDirection.PREV -> pageDelegate.startPrev()
        }
    }

    private fun commitPageTurn(direction: PageDirection) {
        when (direction) {
            PageDirection.NEXT -> navigationCoordinator.handlePageDirection(PageDelegate.Direction.NEXT)
            PageDirection.PREV -> navigationCoordinator.handlePageDirection(PageDelegate.Direction.PREV)
        }
    }

    private fun canTurnPage(direction: PageDirection): Boolean {
        val state = _uiState.value
        val chapter = state.currentChapter ?: return false
        return when (direction) {
            PageDirection.NEXT -> state.pageIndex < chapter.lastIndex || state.chapterIndex < state.totalChapters - 1
            PageDirection.PREV -> state.pageIndex > 0 || state.chapterIndex > 0
        }
    }

    fun jumpToChapterPosition(chapterIndex: Int, byteOffset: Long) {
        val state = _uiState.value
        val chapter = state.currentChapter
        if (chapter?.chapterIndex == chapterIndex && chapter.pageSize > 0) {
            val chapters = loadedBookContent?.chapters
            val chapterByteStart = chapters?.getOrNull(chapterIndex)?.byteStart ?: 0L
            val relativeByte = (byteOffset - chapterByteStart).toInt().coerceAtLeast(0)
            val charOffset = byteToCharOffset(relativeByte)
            val pi = chapter.getPageIndexByCharIndex(charOffset)
            navigationCoordinator.jumpToPage(pi)
        } else {
            openChapter(chapterIndex, targetByteOffset = byteOffset)
        }
    }

    fun openChapter(
        index: Int,
        targetToLastPage: Boolean = false,
        targetByteOffset: Long = -1L,
    ) {
        navigationCoordinator.resetToolbarAutoHide()
        bookSessionManager.openChapter(index, targetToLastPage, targetByteOffset)
    }

    fun addBookmarkFromSelection() {
        val range = _uiState.value.selectedRange ?: return
        bookmarkNotesManager.addBookmark(range)
        navigationCoordinator.clearTextSelection()
    }

    fun addNoteFromSelection() {
        val range = _uiState.value.selectedRange ?: return
        val content = range.selectedText.orEmpty()
        if (content.isBlank()) return
        bookmarkNotesManager.addNote(range, content)
        navigationCoordinator.clearTextSelection()
    }

    /**
     * 查词
     */
    private fun lookupWord(word: String, contextSentence: String) {
        val manager = dictionaryManager ?: return
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    showDictionary = true,
                    currentLookupWord = word,
                    dictionaryContextSentence = contextSentence,
                    dictionaryResults = emptyList(),
                    dictionarySuggestions = emptyList(),
                )

                val results = manager.lookup(word, contextSentence)

                // 如果没有结果，获取前缀建议
                val suggestions = if (results.isEmpty()) {
                    manager.searchByPrefix(word.take(2), 5)
                } else {
                    emptyList()
                }

                _uiState.value = _uiState.value.copy(
                    dictionaryResults = results,
                    dictionarySuggestions = suggestions,
                )
            } catch (e: Exception) {
                android.util.Log.w("ReaderVM", "lookupWord failed", e)
                _uiState.value = _uiState.value.copy(
                    dictionaryResults = emptyList(),
                    dictionarySuggestions = emptyList(),
                )
            }
        }
    }

    /**
     * 关闭查词面板
     */
    private fun dismissDictionary() {
        _uiState.value = _uiState.value.copy(
            showDictionary = false,
            dictionaryResults = emptyList(),
            currentLookupWord = "",
            dictionaryContextSentence = "",
            dictionarySuggestions = emptyList(),
        )
    }

    /**
     * 添加到生词本
     *
     * 停用词会被过滤，不自动加入生词本
     */
    private fun addToWordBook(word: String) {
        // 停用词过滤
        if (!com.shuli.reader.core.dictionary.engine.StopWords.shouldAddToWordBook(word)) {
            android.util.Log.d("ReaderVM", "Skip stop word: $word")
            return
        }

        val manager = dictionaryManager ?: return
        val state = _uiState.value
        val definition = state.dictionaryResults.firstOrNull()?.definition ?: ""

        viewModelScope.launch {
            try {
                manager.addToWordBook(
                    word = word,
                    definition = definition,
                    contextSentence = state.dictionaryContextSentence,
                    bookId = state.bookId,
                    chapterIndex = state.chapterIndex,
                )
            } catch (e: Exception) {
                android.util.Log.w("ReaderVM", "addToWordBook failed", e)
            }
        }
    }

    // ── 文本编辑 ──────────────────────────────────────────

    /** 编辑存储 */
    private val editStore = com.shuli.reader.feature.reader.editor.EditStore()

    /** 编辑 ViewModel（延迟初始化，外部可访问） */
    internal val textEditViewModel by lazy {
        com.shuli.reader.feature.reader.editor.TextEditViewModel(editStore)
    }

    /**
     * 获取当前章节文本（用于本章查找）
     */
    fun getCurrentChapterText(): String {
        val state = _uiState.value
        val content = loadedBookContent ?: return ""
        val chapter = content.chapters.getOrNull(state.chapterIndex) ?: return ""
        return content.content.substring(
            chapter.byteStart.toInt().coerceAtMost(content.content.length),
            chapter.byteEnd.toInt().coerceAtMost(content.content.length)
        )
    }

    /**
     * 获取章节文本（用于全书查找）
     */
    suspend fun getChapterTextForSearch(chapterIndex: Int): String {
        val file = currentBookFilePath?.let { java.io.File(it) } ?: return ""
        val content = loadedBookContent ?: return ""
        return bookContentRepository?.getChapterText(file, chapterIndex, content) ?: ""
    }

    /** 编辑管理器 */
    private val textEditManager by lazy {
        com.shuli.reader.feature.reader.editor.TextEditManager(
            context = appContext!!,
            editStore = editStore,
        )
    }

    /** 打开查找/替换面板 */
    private fun openTextEdit() {
        switchToScrollPageMode()
        _uiState.value = _uiState.value.copy(showTextEdit = true)
    }

    /** 关闭查找/替换面板 */
    private fun closeTextEdit() {
        restorePageAnimType()
        _uiState.value = _uiState.value.copy(
            showTextEdit = false,
            inlineEditText = null,
            editAnchor = null,
            cursorEditAnchor = null,
            scrollToY = null,
        )
    }

    /** 进入内联编辑模式：自动切换 SCROLL 模式并将选区滚到屏幕上方 */
    private var savedPageAnimType: com.shuli.reader.core.reader.engine.animation.PageDelegateFactory.PageAnimType? = null

    private fun switchToScrollPageMode() {
        val state = _uiState.value
        if (savedPageAnimType == null) {
            savedPageAnimType = state.pageAnimType
        }
        val scrollType = com.shuli.reader.core.reader.engine.animation.PageDelegateFactory.PageAnimType.SCROLL
        if (state.pageAnimType != scrollType) {
            navigationCoordinator.setPageAnimType(
                scrollType,
                state.readerPreferences.pageAnimSpeed,
            ) { pageDelegate = it }
        }
    }

    private fun enterInlineEdit(
        originalText: String,
        anchor: com.shuli.reader.core.reader.model.SelectionRange? = null,
    ) {
        val state = _uiState.value
        val selY = state.selectionScreenY
        val screenHeight = state.currentPage?.layout?.pageHeight ?: 0f

        // 切换为 SCROLL（支持内容上下滚动），再基于最新状态写入编辑字段。
        switchToScrollPageMode()

        // 计算滚动偏移：将选区 Y 滚到屏幕上方 1/4 处
        val targetY = screenHeight * 0.25f
        val scrollDelta = if (screenHeight > 0f && selY > targetY) {
            -(selY - targetY)
        } else {
            0f
        }
        val adjustedSelectionY = selY + scrollDelta

        _uiState.value = _uiState.value.copy(
            // 注意：不设 showTextEdit=true，否则 InlineEditPopover 被隐藏、EditorOverlay 弹出
            inlineEditText = originalText,
            editAnchor = anchor,
            cursorEditAnchor = null,
            selectionScreenY = adjustedSelectionY,
            scrollToY = scrollDelta,
        )
    }

    /** 进入光标编辑模式：不显示修改框，只显示光标并唤起键盘 */
    private fun enterCursorEdit(
        anchor: com.shuli.reader.core.reader.model.SelectionRange,
        screenX: Float,
        screenY: Float,
    ) {
        _uiState.value = _uiState.value.copy(
            showTextEdit = true,
            selectedRange = null,
            inlineEditText = null,
            editAnchor = null,
            cursorEditAnchor = anchor,
            cursorScreenX = screenX,
            cursorScreenY = screenY,
        )
    }

    /** 确认内联编辑：应用替换 */
    private fun confirmInlineEdit(newText: String) {
        val range = _uiState.value.editAnchor ?: run {
            cancelInlineEdit()
            return
        }
        viewModelScope.launch {
            editStore.addSingle(com.shuli.reader.feature.reader.editor.EditDelta(
                chapterIndex = range.chapterIndex,
                charStart = range.startPos,
                charEnd = range.endPos,
                newText = newText,
                originalText = range.selectedText ?: "",
            ))

            restorePageAnimType()
            _uiState.value = _uiState.value.copy(
                hasUnsavedEdits = true,
                selectedRange = null,
                inlineEditText = null,
                editAnchor = null,
                cursorEditAnchor = null,
                scrollToY = null,
            )

            // 触发重新分页
            reflowCurrentChapter(_uiState.value.readerPreferences)
        }
    }

    /** 取消内联编辑 */
    private fun cancelInlineEdit() {
        restorePageAnimType()
        _uiState.value = _uiState.value.copy(inlineEditText = null, editAnchor = null, scrollToY = null)
    }

    /** 恢复进入编辑前的翻页模式 */
    private fun restorePageAnimType() {
        val saved = savedPageAnimType ?: return
        savedPageAnimType = null
        if (_uiState.value.pageAnimType != saved) {
            navigationCoordinator.setPageAnimType(
                saved,
                _uiState.value.readerPreferences.pageAnimSpeed,
            ) { pageDelegate = it }
        }
    }

    /** 确认光标输入：在光标位置插入文本 */
    private fun confirmCursorEdit(newText: String) {
        val range = _uiState.value.cursorEditAnchor ?: return
        if (newText.isEmpty()) {
            cancelCursorEdit()
            return
        }
        val state = _uiState.value

        viewModelScope.launch {
            editStore.addSingle(com.shuli.reader.feature.reader.editor.EditDelta(
                chapterIndex = state.chapterIndex,
                charStart = range.startPos,
                charEnd = range.endPos,
                newText = newText,
                originalText = "",
            ))

            _uiState.value = state.copy(
                hasUnsavedEdits = true,
                cursorEditAnchor = null,
            )

            reflowCurrentChapter(_uiState.value.readerPreferences)
        }
    }

    /** 取消光标输入 */
    private fun cancelCursorEdit() {
        _uiState.value = _uiState.value.copy(cursorEditAnchor = null)
    }

    /** 查找下一个 */
    private fun findNext() {
        textEditViewModel.nextMatch()
    }

    /** 查找上一个 */
    private fun findPrev() {
        textEditViewModel.prevMatch()
    }

    /** 替换当前匹配 */
    private fun replaceCurrent(replacement: String) {
        val state = _uiState.value
        viewModelScope.launch {
            textEditViewModel.replaceCurrent(state.chapterIndex)
            _uiState.value = state.copy(hasUnsavedEdits = true)
            reflowCurrentChapter(_uiState.value.readerPreferences)
        }
    }

    /** 全部替换 */
    private fun replaceAll(find: String, replace: String, isRegex: Boolean) {
        val state = _uiState.value
        viewModelScope.launch {
            textEditViewModel.replaceAllInChapter(state.chapterIndex)
            _uiState.value = state.copy(hasUnsavedEdits = true)
            reflowCurrentChapter(_uiState.value.readerPreferences)
        }
    }

    /** 撤销编辑 */
    private fun undoEdit() {
        viewModelScope.launch {
            editStore.undo()
            _uiState.value = _uiState.value.copy(hasUnsavedEdits = editStore.isDirty)
            reflowCurrentChapter(_uiState.value.readerPreferences)
        }
    }

    /** 重做编辑 */
    private fun redoEdit() {
        viewModelScope.launch {
            editStore.redo()
            _uiState.value = _uiState.value.copy(hasUnsavedEdits = editStore.isDirty)
            reflowCurrentChapter(_uiState.value.readerPreferences)
        }
    }

    /** 清空编辑（放弃修改） */
    fun clearEdits() {
        viewModelScope.launch {
            editStore.clear()
            _uiState.value = _uiState.value.copy(hasUnsavedEdits = false)
        }
    }

    /** 保存编辑并退出 */
    fun saveEditsAndExit(onExit: () -> Unit) {
        val file = currentBookFilePath?.let { java.io.File(it) } ?: run {
            onExit()
            return
        }
        val content = loadedBookContent ?: run {
            onExit()
            return
        }

        viewModelScope.launch {
            try {
                val appContainer = appContext?.let {
                    com.shuli.reader.core.ShuLiAppContainer(it)
                }
                val bookChapterDao = appContainer?.database?.bookChapterDao()

                if (bookChapterDao != null) {
                    val charset = runCatching { java.nio.charset.Charset.forName(content.encoding) }.getOrDefault(Charsets.UTF_8)
                    textEditManager.saveToFile(
                        file = file,
                        charset = charset,
                        bookContent = content,
                        bookChapterDao = bookChapterDao,
                        getChapterText = { chapterIndex ->
                            // 从原始文件读取章节文本（不叠加 Delta）
                            bookContentRepository?.getChapterText(file, chapterIndex, content.chapters, content.bookId)
                                ?: ""
                        },
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("ReaderVM", "saveEditsAndExit failed", e)
            } finally {
                // 无论成功失败，都执行退出
                withContext(Dispatchers.Main) {
                    onExit()
                }
            }
        }
    }

    /** 保存编辑到文件 */
    private fun saveEdits() {
        val file = currentBookFilePath?.let { java.io.File(it) } ?: return
        val state = _uiState.value
        val content = loadedBookContent ?: return

        viewModelScope.launch {
            try {
                // 获取 BookChapterDao（通过 appContainer）
                val appContainer = appContext?.let {
                    com.shuli.reader.core.ShuLiAppContainer(it)
                }
                val bookChapterDao = appContainer?.database?.bookChapterDao()

                if (bookChapterDao == null) {
                    android.util.Log.w("ReaderVM", "saveEdits: BookChapterDao not available")
                    return@launch
                }

                textEditManager.saveToFile(
                    file = file,
                    charset = runCatching { java.nio.charset.Charset.forName(content.encoding) }.getOrDefault(Charsets.UTF_8),
                    bookContent = content,
                    bookChapterDao = bookChapterDao,
                    getChapterText = { chapterIndex ->
                        // 从原始文件读取章节文本（不叠加 Delta）
                        bookContentRepository?.getChapterText(file, chapterIndex, content.chapters, content.bookId)
                            ?: ""
                    },
                )

                // 保存成功后，关闭编辑模式并刷新显示
                restorePageAnimType()
                _uiState.value = _uiState.value.copy(
                    hasUnsavedEdits = false,
                    showTextEdit = false,
                    inlineEditText = null,
                    editAnchor = null,
                    cursorEditAnchor = null,
                    scrollToY = null,
                )

                // 重新加载当前章节（文件已更新）
                openChapter(state.chapterIndex)

            } catch (e: Exception) {
                android.util.Log.w("ReaderVM", "saveEdits failed", e)
            }
        }
    }

    /** R7: 暂停阅读会话（flush 当前片段，排除暂停时段） */
    fun pauseReadingSession() {
        bookSessionManager.flushChapterTime()
        readingStateManager.pauseSession()
    }

    /** R7: 恢复阅读会话（重置计时起点，跳过暂停时段） */
    fun resumeReadingSession() {
        readingStateManager.resumeSession()
        bookSessionManager.resetChapterStartTimestamp()
    }

    fun releaseReaderResources() {
        bookSessionManager.releaseResources()
        chapterProvider.cancel()
        navigationCoordinator.release()
    }

    /**
     * 将 ReaderPreferences 全量应用到当前状态（由 ReaderPresetManager 回调）
     */
    private fun applyPreferencesFromPreset(prefs: ReaderPreferences) {
        readerSettingsManager.setFontSize(prefs.fontSize)
        readerSettingsManager.setLineSpacing(prefs.lineSpacing)
        readerSettingsManager.setParagraphSpacing(prefs.paragraphSpacing)
        readerSettingsManager.setIndent(prefs.indent)
        readerSettingsManager.setBodyBox(prefs.bodyBox)
        readerSettingsManager.setHeaderBox(prefs.headerBox)
        readerSettingsManager.setFooterBox(prefs.footerBox)
        readerSettingsManager.setTitleBox(prefs.titleBox)
        readerSettingsManager.setReadingFont(prefs.readingFont)
        navigationCoordinator.setPageAnimType(
            prefs.pageAnimType.toFactoryType(),
            _uiState.value.readerPreferences.pageAnimSpeed,
        ) { pageDelegate = it }
        readerSettingsManager.setReaderTheme(prefs.backgroundColor)
        readerSettingsManager.setLetterSpacing(prefs.letterSpacing)
        readerSettingsManager.setFontWeight(prefs.fontWeight)
        readerSettingsManager.setTextAlign(prefs.textAlign)
        readerSettingsManager.setChineseConvert(prefs.chineseConvert)
        readerSettingsManager.setBrightness(prefs.brightness)
        readerSettingsManager.setColorTemperature(prefs.colorTemperature)
        readerSettingsManager.setHeaderVisibility(prefs.header.visibility)
        readerSettingsManager.setHeaderLeft(prefs.header.left)
        readerSettingsManager.setHeaderCenter(prefs.header.center)
        readerSettingsManager.setHeaderRight(prefs.header.right)
        readerSettingsManager.setFooterVisibility(prefs.footer.visibility)
        readerSettingsManager.setFooterLeft(prefs.footer.left)
        readerSettingsManager.setFooterCenter(prefs.footer.center)
        readerSettingsManager.setFooterRight(prefs.footer.right)
        readerSettingsManager.setHeaderFooterAlpha(prefs.headerFooterAlpha)
        readerSettingsManager.setShowProgress(prefs.showProgress)
        readerSettingsManager.setTitleAlign(prefs.titleStyle.align)
        readerSettingsManager.setTitleSizeOffset(prefs.titleStyle.sizeOffsetSp)
        readerSettingsManager.setTitleMarginTop(prefs.titleStyle.marginTopDp)
        readerSettingsManager.setTitleMarginBottom(prefs.titleStyle.marginBottomDp)
        readerSettingsManager.setKeepScreenOn(prefs.keepScreenOn)
        readerSettingsManager.setVolumeKeyTurnPage(prefs.volumeKeyTurnPage)
        readerSettingsManager.setEdgeTurnPage(prefs.edgeTurnPage)
        readerSettingsManager.setEdgeWidthPercent(prefs.edgeWidthPercent)
        readerSettingsManager.setShowHeaderLine(prefs.showHeaderLine)
        readerSettingsManager.setShowFooterLine(prefs.showFooterLine)
        readerSettingsManager.setHeaderFontSizeRatio(prefs.headerFontSizeRatio)
        readerSettingsManager.setFooterFontSizeRatio(prefs.footerFontSizeRatio)
        readerSettingsManager.setBottomJustify(prefs.bottomJustify)
    }

    // ── 布局配置 ──────────────────────────────────────────

    private fun layoutConfigFor(preferences: ReaderPreferences): ReaderLayoutConfig {
        return preferences.toLayoutConfig(
            pageSize = PageSize(screenWidthPx, screenHeightPx),
            density = this.density,
        )
    }

    private fun computeLayoutHash(preferences: ReaderPreferences): String {
        val config = layoutConfigFor(preferences)
        return com.shuli.reader.core.reader.engine.cache.PageCountPersistence.computeLayoutHash(config)
    }

    private fun persistPageCounts() {
        val ctx = appContext ?: return
        val state = _uiState.value
        val counts = state.chapterPageCounts
        if (counts.isEmpty() || currentLayoutHash.isEmpty()) return
        if (counts == lastPersistedCounts) return
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            try {
                com.shuli.reader.core.reader.engine.cache.PageCountPersistence.save(
                    ctx, state.bookId.toString(), currentLayoutHash, counts,
                )
                lastPersistedCounts = counts
            } catch (e: Exception) {
                if (com.shuli.reader.BuildConfig.DEBUG) {
                    android.util.Log.w(TAG, "persistPageCounts failed", e)
                }
            }
        }
    }

    // ── 内部分页方法 ──────────────────────────────────────

    private suspend fun paginateChapter(content: BookContent, index: Int): TextChapter? =
        chapterPaginationCoordinator.paginateChapter(content, index)

    private fun paginateChapterStreaming(
        content: BookContent,
        index: Int,
        targetCharOffset: Int = 0,
        onDone: (() -> Unit)? = null,
        onMergePageCounts: ((oldPageCounts: Map<Int, Int>, newPageSize: Int) -> Map<Int, Int>)? = null,
    ): Job = chapterPaginationCoordinator.paginateChapterStreaming(content, index, targetCharOffset, onDone, onMergePageCounts)

    private fun preloadAdjacentChapters(content: BookContent, currentIndex: Int) =
        chapterPaginationCoordinator.preloadAdjacentChapters(
            content = content,
            currentIndex = currentIndex,
            onChapterPreloaded = { refreshCrossChapterPages() },
        )

    /**
     * A1: 从章节缓存同步查询跨章相邻页，填充 uiState.nextChapterFirstPage / prevChapterLastPage。
     *
     * 命中 → 跨章翻页动画可直接使用这两个字段作为 nextPage / prevPage，无空白帧。
     * 未命中 → 字段保持 null，由 ReaderCanvasView.fillPage 的 fallback（保留 currentPage）兜底。
     *
     * 调用时机：章节加载完成（onChapterLoaded）、预加载启动后（preloadAdjacentChapters）。
     *
     * 容错：缓存查询若抛异常（罕见，例如底层 Picture 资源被回收），吞掉并记日志，
     * 不让防御性预取成为闪退源头。
     */
    private fun refreshCrossChapterPages() {
        try {
            val state = _uiState.value
            val currentIndex = state.chapterIndex
            val totalChapters = state.totalChapters
            val nextChapter = if (currentIndex < totalChapters - 1) {
                chapterPaginationCoordinator.getCachedChapter(currentIndex + 1)
            } else null
            val prevChapter = if (currentIndex > 0) {
                chapterPaginationCoordinator.getCachedChapter(currentIndex - 1)
            } else null
            val nextPage = nextChapter?.getPage(0)
            val prevPage = prevChapter?.getPage(prevChapter.lastIndex)
            val nextContent = nextChapter?.content
            val prevContent = prevChapter?.content
            // 只在字段确实变化时才写 uiState，避免无谓的 recomposition
            if (
                state.nextChapterFirstPage !== nextPage ||
                state.prevChapterLastPage !== prevPage ||
                state.nextChapterContent !== nextContent ||
                state.prevChapterContent !== prevContent ||
                state.nextChapter !== nextChapter ||
                state.prevChapter !== prevChapter
            ) {
                _uiState.value = state.copy(
                    nextChapterFirstPage = nextPage,
                    nextChapterContent = nextContent,
                    prevChapterLastPage = prevPage,
                    prevChapterContent = prevContent,
                    // 连续滚动跨章页面序列需要相邻章节的完整分页对象
                    nextChapter = nextChapter,
                    prevChapter = prevChapter,
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("ReaderVM", "refreshCrossChapterPages failed", e)
        }
    }

    private fun reflowCurrentChapter(preferences: ReaderPreferences) =
        chapterPaginationCoordinator.reflowCurrentChapter(preferences)

    private fun BookContent.normalizedChapters(): List<Chapter> {
        if (chapters.isNotEmpty()) return chapters
        return if (content.isNotBlank()) {
            listOf(Chapter(title = "Full Text", byteStart = 0L, byteEnd = content.length.toLong()))
        } else {
            emptyList()
        }
    }

    private fun BookContent.chapterText(chapter: Chapter): String = content

    private fun saveReadingProgress(immediate: Boolean) = bookSessionManager.saveReadingProgress(immediate)

    private fun clearSearchResults() = readerSearchManager.clearSearchResults()

    // ── 书籍详情（P0）───────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentBookItem: StateFlow<BookItem?> = _uiState
        .map { it.bookId }
        .distinctUntilChanged()
        .flatMapLatest { bookId ->
            if (bookId > 0L) {
                bookQueryRepository?.getBookById(bookId) ?: flowOf(null)
            } else {
                flowOf(null)
            }
        }
        .map { entity -> entity?.toBookItem() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateStatus(newStatus: ReadingStatus) {
        val bookId = _uiState.value.bookId
        viewModelScope.launch {
            val repository = readingProgressRepository ?: return@launch
            repository.updateReadingStatus(bookId, newStatus)
        }
    }

    // ── 标签操作（P1）───────────────────────────────────────

    val allTags = tagRepository?.getAllTagsWithCount()
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        ?: flowOf(emptyList<com.shuli.reader.core.database.dao.TagWithCount>())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tagSuggestions = MutableStateFlow(emptyList<com.shuli.reader.core.database.dao.TagWithCount>())
    val tagSuggestions: StateFlow<List<com.shuli.reader.core.database.dao.TagWithCount>> = _tagSuggestions.asStateFlow()

    fun getBookTags() =
        tagRepository?.getTagsForBook(_uiState.value.bookId) ?: flowOf(emptyList())

    fun addTag(tagName: String) {
        val bookId = _uiState.value.bookId
        viewModelScope.launch {
            tagRepository?.addTagToBook(bookId, tagName)
        }
    }

    fun removeTag(tagId: Long) {
        val bookId = _uiState.value.bookId
        viewModelScope.launch {
            tagRepository?.removeTagFromBook(bookId, tagId)
        }
    }

    fun searchTagSuggestions(prefix: String) {
        viewModelScope.launch {
            _tagSuggestions.value = tagRepository?.searchTagsByPrefix(prefix) ?: emptyList()
        }
    }

    override fun onCleared() {
        releaseReaderResources()
        super.onCleared()
    }
}
