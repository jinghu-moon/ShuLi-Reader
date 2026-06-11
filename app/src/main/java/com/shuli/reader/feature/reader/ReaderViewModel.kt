package com.shuli.reader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.data.toFactoryType
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.core.reader.TitleAlign
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
    var pageDelegate: PageDelegate = PageDelegateFactory.create(_uiState.value.pageAnimType)
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
            preloadAdjacentChapters = { content, index -> preloadAdjacentChapters(content, index) },
            loadBookmarks = { bookmarkNotesManager.loadBookmarks() },
            loadNotes = { bookmarkNotesManager.loadNotes() },
            onChapterLoaded = { },
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
            is ReaderIntent.TurnPage -> when (intent.direction) {
                PageDirection.NEXT -> nextPage()
                PageDirection.PREV -> prevPage()
            }
            is ReaderIntent.NextPage -> nextPage()
            is ReaderIntent.PrevPage -> prevPage()
            is ReaderIntent.JumpToPosition -> jumpToChapterPosition(intent.chapterIndex, intent.byteOffset)

            // ── UI 开关 ──
            is ReaderIntent.ToggleToolbar -> navigationCoordinator.toggleToolbar()
            is ReaderIntent.ToggleDirectory -> navigationCoordinator.toggleDirectory()
            is ReaderIntent.ToggleQuickSettings -> navigationCoordinator.toggleQuickSettings()
            is ReaderIntent.ToggleSearch -> navigationCoordinator.toggleSearch()
            is ReaderIntent.ClearSelection -> navigationCoordinator.clearTextSelection()

            // ── 选区操作 ──
            is ReaderIntent.AddBookmarkFromSelection -> addBookmarkFromSelection()
            is ReaderIntent.AddNoteFromSelection -> addNoteFromSelection()
            is ReaderIntent.AddBookmark -> bookmarkNotesManager.addBookmark()

            // ── 设置 ──
            is ReaderIntent.UpdateSetting -> dispatchSetting(intent.key, intent.value)
            is ReaderIntent.CycleTheme -> readerSettingsManager.cycleTheme()
            is ReaderIntent.ResetSettingsToDefault -> readerPresetManager.resetToDefault()

            // ── 设置作用域 ──
            is ReaderIntent.SetSettingsScope -> {
                if (intent.scope == com.shuli.reader.feature.reader.SettingsScope.BOOK &&
                    uiState.value.settingsScope == com.shuli.reader.feature.reader.SettingsScope.GLOBAL
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
            is ReaderIntent.SetPageAnimType -> navigationCoordinator.setPageAnimType(
                intent.type.toFactoryType(),
            ) { pageDelegate = it }

            // ── 字体 ──
            is ReaderIntent.ImportFont -> fontImportManager.importFont(intent.uri)
            is ReaderIntent.DeleteFont -> fontImportManager.deleteFont(intent.fontKey)
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
            ReaderSettingKey.MARGIN_HORIZONTAL -> s.setMarginHorizontal((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.MARGIN_VERTICAL -> s.setMarginVertical((value as ReaderSettingValue.Float).value)
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
            ReaderSettingKey.HEADER_MARGIN_TOP -> s.setHeaderMarginTop((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.FOOTER_VISIBILITY -> s.setFooterVisibility((value as ReaderSettingValue.HeaderVisibility).value)
            ReaderSettingKey.FOOTER_LEFT -> s.setFooterLeft((value as ReaderSettingValue.SlotContent).value)
            ReaderSettingKey.FOOTER_CENTER -> s.setFooterCenter((value as ReaderSettingValue.SlotContent).value)
            ReaderSettingKey.FOOTER_RIGHT -> s.setFooterRight((value as ReaderSettingValue.SlotContent).value)
            ReaderSettingKey.FOOTER_MARGIN_BOTTOM -> s.setFooterMarginBottom((value as ReaderSettingValue.Float).value)
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
            ReaderSettingKey.KEEP_SCREEN_ON -> s.setKeepScreenOn((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.VOLUME_KEY_TURN_PAGE -> s.setVolumeKeyTurnPage((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.EDGE_TURN_PAGE -> s.setEdgeTurnPage((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.EDGE_WIDTH_PERCENT -> s.setEdgeWidthPercent((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.IMMERSIVE_MODE -> s.setImmersiveMode((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.MAX_PAGE_WIDTH -> s.setMaxPageWidth((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.REMOVE_EMPTY_LINES -> s.setRemoveEmptyLines((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.CLEAN_CHAPTER_TITLE -> s.setCleanChapterTitle((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.PROGRESS_STYLE -> s.setProgressStyle((value as ReaderSettingValue.ProgressStyle).value)
            ReaderSettingKey.AUTO_NIGHT_MODE -> s.setAutoNightMode((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.AUTO_PAGE_TURN -> s.setAutoPageTurn((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.AUTO_PAGE_TURN_INTERVAL -> s.setAutoPageTurnInterval((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.EPUB_OVERRIDE_STYLE -> s.setEpubOverrideStyle((value as ReaderSettingValue.Bool).value)
            ReaderSettingKey.LEFT_ZONE_RATIO -> s.setLeftZoneRatio((value as ReaderSettingValue.Float).value)
            ReaderSettingKey.CUSTOM_THEME_COLOR -> {
                val v = value as ReaderSettingValue.CustomThemeColor
                s.setCustomThemeColor(v.backgroundColor, v.textColor, v.accentColor)
            }
            // v5.1 Phase 1-4 新增设置（setter 待各 Phase 实现，暂用通用更新）
            ReaderSettingKey.COLOR_TEMPERATURE -> s.updatePrefsGeneric(
                { it.copy(colorTemperature = (value as ReaderSettingValue.Float).value) },
                reflow = false,
            )
            ReaderSettingKey.FOCUS_LINE -> s.updatePrefsGeneric(
                { it.copy(focusLine = (value as ReaderSettingValue.Bool).value) },
                reflow = false,
            )
            ReaderSettingKey.WORD_SPACING -> s.updatePrefsGeneric(
                { it.copy(wordSpacing = (value as ReaderSettingValue.Float).value) },
                reflow = true,
            )
            ReaderSettingKey.PARAGRAPH_DIVIDER -> s.updatePrefsGeneric(
                { it.copy(paragraphDivider = (value as ReaderSettingValue.Bool).value) },
                reflow = true,
            )
            ReaderSettingKey.MARGIN_TOP -> s.updatePrefsGeneric(
                { it.copy(marginTop = (value as ReaderSettingValue.Float).value) },
                reflow = true,
            )
            ReaderSettingKey.MARGIN_BOTTOM -> s.updatePrefsGeneric(
                { it.copy(marginBottom = (value as ReaderSettingValue.Float).value) },
                reflow = true,
            )
            ReaderSettingKey.MARGIN_LEFT -> s.updatePrefsGeneric(
                { it.copy(marginLeft = (value as ReaderSettingValue.Float).value) },
                reflow = true,
            )
            ReaderSettingKey.MARGIN_RIGHT -> s.updatePrefsGeneric(
                { it.copy(marginRight = (value as ReaderSettingValue.Float).value) },
                reflow = true,
            )
            ReaderSettingKey.BIONIC_READING -> s.updatePrefsGeneric(
                { it.copy(bionicReading = (value as ReaderSettingValue.Bool).value) },
                reflow = true,
            )
            ReaderSettingKey.VERTICAL_TEXT -> s.updatePrefsGeneric(
                { it.copy(verticalText = (value as ReaderSettingValue.Bool).value) },
                reflow = true,
            )
            ReaderSettingKey.DUAL_PAGE_MODE -> s.updatePrefsGeneric(
                { it.copy(dualPageMode = (value as ReaderSettingValue.DualPageMode).value) },
                reflow = true,
            )
            ReaderSettingKey.HAPTIC_FEEDBACK -> s.updatePrefsGeneric(
                { it.copy(hapticFeedback = (value as ReaderSettingValue.Bool).value) },
                reflow = false,
            )
            ReaderSettingKey.ORIENTATION_LOCK -> s.updatePrefsGeneric(
                { it.copy(orientationLock = (value as ReaderSettingValue.OrientationLock).value) },
                reflow = false,
            )
            ReaderSettingKey.PAGE_ANIM_SPEED -> s.updatePrefsGeneric(
                { it.copy(pageAnimSpeed = (value as ReaderSettingValue.PageAnimSpeed).value) },
                reflow = false,
            )
            ReaderSettingKey.AD_FILTERING -> s.updatePrefsGeneric(
                { it.copy(adFiltering = (value as ReaderSettingValue.Bool).value) },
                reflow = true,
            )
            ReaderSettingKey.TTS_VOICE -> s.updatePrefsGeneric(
                { it.copy(ttsVoice = (value as ReaderSettingValue.Str).value) },
                reflow = false,
            )
            ReaderSettingKey.TTS_AUTO_PAGE -> s.updatePrefsGeneric(
                { it.copy(ttsAutoPage = (value as ReaderSettingValue.Bool).value) },
                reflow = false,
            )
            ReaderSettingKey.TTS_TIMER -> s.updatePrefsGeneric(
                { it.copy(ttsTimer = (value as ReaderSettingValue.Int).value) },
                reflow = false,
            )
            ReaderSettingKey.EYE_CARE_REMINDER_INTERVAL -> s.updatePrefsGeneric(
                { it.copy(eyeCareReminderInterval = (value as ReaderSettingValue.Int).value) },
                reflow = false,
            )
            ReaderSettingKey.BACKGROUND_TEXTURE -> s.updatePrefsGeneric(
                { it.copy(backgroundTexture = (value as ReaderSettingValue.Str).value) },
                reflow = false,
            )
        }
    }

    fun nextPage() = navigationCoordinator.nextPage()

    fun prevPage() = navigationCoordinator.prevPage()

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
        readerSettingsManager.setMarginHorizontal(prefs.marginHorizontal)
        readerSettingsManager.setMarginVertical(prefs.marginVertical)
        readerSettingsManager.setReadingFont(prefs.readingFont)
        navigationCoordinator.setPageAnimType(prefs.pageAnimType.toFactoryType()) { pageDelegate = it }
        readerSettingsManager.setReaderTheme(prefs.backgroundColor)
        readerSettingsManager.setLetterSpacing(prefs.letterSpacing)
        readerSettingsManager.setFontWeight(prefs.fontWeight)
        readerSettingsManager.setTextAlign(prefs.textAlign)
        readerSettingsManager.setChineseConvert(prefs.chineseConvert)
        readerSettingsManager.setBrightness(prefs.brightness)
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
        val textSizePx = preferences.fontSize * density
        val mt = (preferences.marginTop ?: preferences.marginVertical) * density
        val mb = (preferences.marginBottom ?: preferences.marginVertical) * density
        val ml = (preferences.marginLeft ?: preferences.marginHorizontal) * density
        val mr = (preferences.marginRight ?: preferences.marginHorizontal) * density
        return ReaderLayoutConfig(
            pageSize = PageSize(screenWidthPx, screenHeightPx),
            textSize = textSizePx,
            lineHeight = preferences.lineSpacing,
            paragraphSpacing = preferences.paragraphSpacing * textSizePx,
            marginTop = mt,
            marginBottom = mb,
            marginLeft = ml,
            marginRight = mr,
            indent = preferences.indent,
            density = this.density,
            letterSpacingPx = preferences.letterSpacing * textSizePx,
            titleStyle = preferences.titleStyle,
            useZhLayout = preferences.useZhLayout,
            bottomJustify = preferences.bottomJustify,
            headerMarginTop = preferences.header.marginTop * density,
            footerMarginBottom = preferences.footer.marginBottom * density,
        )
    }

    private fun computeLayoutHash(preferences: ReaderPreferences): String {
        val config = layoutConfigFor(preferences)
        return com.shuli.reader.core.reader.cache.PageCountPersistence.computeLayoutHash(
            config = config,
            showHeader = preferences.header.visibility != HeaderVisibility.ALWAYS_HIDE,
            showFooter = preferences.footer.visibility != HeaderVisibility.ALWAYS_HIDE,
            chineseConvert = preferences.chineseConvert.ordinal,
            usePanguSpacing = preferences.usePanguSpacing,
        )
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
                com.shuli.reader.core.reader.cache.PageCountPersistence.save(
                    ctx, state.bookId.toString(), currentLayoutHash, counts,
                )
                lastPersistedCounts = counts
            } catch (_: Exception) { }
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
        chapterPaginationCoordinator.preloadAdjacentChapters(content, currentIndex)

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
