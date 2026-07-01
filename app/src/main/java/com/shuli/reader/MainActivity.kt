package com.shuli.reader

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.shuli.reader.core.i18n.AppStrings
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.bookshelf.BookshelfScreen
import com.shuli.reader.feature.bookshelf.BookshelfViewModel
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.BookshelfUiState
import com.shuli.reader.feature.bookshelf.model.BookshelfNode
import com.shuli.reader.feature.bookshelf.model.FilterType
import com.shuli.reader.feature.bookshelf.model.FolderItem
import androidx.compose.ui.platform.LocalContext
import com.shuli.reader.feature.reader.screen.ReaderScreen
import com.shuli.reader.feature.reader.screen.ReaderIntent
import com.shuli.reader.feature.reader.screen.ReaderViewModel
import com.shuli.reader.feature.search.GlobalSearchScreen
import com.shuli.reader.feature.search.GlobalSearchViewModel
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.feature.settings.SettingsEvent
import com.shuli.reader.feature.settings.SettingsScreen
import com.shuli.reader.feature.settings.SettingsViewModel
import com.shuli.reader.feature.stats.StatsScreen
import com.shuli.reader.feature.stats.StatsViewModel
import com.shuli.reader.ui.theme.ReadingFont
import com.shuli.reader.ui.theme.ShuLiTheme
import com.shuli.reader.ui.theme.Typography
import com.shuli.reader.ui.theme.withFontFamily
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


sealed class ActiveScreen {
    data object Bookshelf : ActiveScreen()
    data object Stats : ActiveScreen()
    data object Settings : ActiveScreen()
    data class GlobalSearch(
        val currentGroupBookIds: List<Long> = emptyList(),
        val currentGroupLabel: String = "当前书架",
    ) : ActiveScreen()
    data class Reader(val bookId: Long, val jumpTarget: ReaderJumpTarget? = null) : ActiveScreen()
}

data class ReaderJumpTarget(
    val chapterIndex: Int,
    val byteOffset: Long,
)

private fun List<BookshelfNode>.toSearchScopeBookIds(): List<Long> {
    return flatMap { node ->
        when (node) {
            is BookItem -> listOf(node.id)
            is FolderItem -> node.books.map { it.id }
        }
    }.distinct()
}

private fun BookshelfUiState.toSearchScopeLabel(): String {
    activeTagFilter?.takeIf { it.isNotBlank() }?.let { return "#$it" }
    if (isSearching && searchQuery.isNotBlank()) return "书架搜索"
    return when (filterType) {
        FilterType.ALL -> "当前书架"
        FilterType.WANT_TO_READ -> "想读"
        FilterType.READING -> "在读"
        FilterType.PAUSED -> "搁置"
        FilterType.FINISHED -> "读完"
        FilterType.ABANDONED -> "弃读"
        FilterType.FAVORITE -> "收藏"
    }
}

class MainActivity : ComponentActivity() {
    // 音量键翻页：持有当前 ReaderViewModel 引用
    private var currentReaderViewModel: ReaderViewModel? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val viewModel = currentReaderViewModel
        val state = viewModel?.uiState?.value
        if (viewModel != null && state != null
            && state.readerPreferences.volumeKeyTurnPage
            && !state.showQuickSettings
            && !state.showDirectory
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        viewModel.prevPage()
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        viewModel.nextPage()
                        return true
                    }
                }
            }
            // 音量键事件已被消费，不传递给系统（避免调节音量）
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as ShuLiApplication).appContainer
        val bookshelfViewModel = BookshelfViewModel(
            bookQueryRepository = appContainer.bookQueryRepository,
            folderRepository = appContainer.folderRepository,
            readingProgressRepository = appContainer.readingProgressRepository,
            bookImportRepository = appContainer.bookImportRepository,
            tagRepository = appContainer.tagRepository,
            userPreferences = appContainer.userPreferences,
            readingSessionDao = appContainer.database.readingSessionDao(),
        )
        val settingsViewModel = SettingsViewModel(appContainer.userPreferences)
        val statsViewModel = StatsViewModel(
            statsRepository = appContainer.statsRepository,
            userPreferences = appContainer.userPreferences,
        )
        val globalSearchViewModel = GlobalSearchViewModel(
            globalSearchRepository = appContainer.globalSearchRepository,
            searchIndexBackfillManager = appContainer.searchIndexBackfillManager,
            userPreferences = appContainer.userPreferences,
        )

        // Macrobenchmark 测试钩子：仅在 debug 构建生效，release 包不暴露入口，
        // 避免外部 App 通过 Intent 强制导入任意路径文件
        val testBookId = if (BuildConfig.DEBUG) intent.getLongExtra("EXTRA_TEST_BOOK_ID", -1L) else -1L
        val testFilePath = if (BuildConfig.DEBUG) intent.getStringExtra("EXTRA_TEST_FILE_PATH") else null

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()
            var currentScreen by remember {
                mutableStateOf<ActiveScreen>(
                    if (testBookId != -1L) ActiveScreen.Reader(testBookId) else ActiveScreen.Bookshelf
                )
            }

            // 监听 Activity 重建事件并延迟启动同步和预热
            LaunchedEffect(testFilePath) {
                if (!testFilePath.isNullOrEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            appContainer.bookImportRepository.importBook(java.io.File(testFilePath))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                launch {
                    settingsViewModel.events.collect { event ->
                        if (event is SettingsEvent.Recreate) {
                            recreate()
                        }
                    }
                }
                launch {
                    kotlinx.coroutines.delay(2000)
                    appContainer.coverPrewarmer.prewarm(this)
                    
                    val syncMethod = appContainer.userPreferences.syncMethod.first()
                    if (syncMethod == com.shuli.reader.core.data.SyncMethodConst.WEBDAV) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching {
                                appContainer.syncManager.flushPendingUploads()
                            }
                        }
                    }
                }
            }


            // 计算全局语言
            val currentStrings = when (settingsState.language) {
                "zh-TW" -> AppStrings.ZhHant
                "en" -> AppStrings.En
                else -> AppStrings.ZhHans
            }

            // 同步 EpubParser 的图片占位符文本
            LaunchedEffect(currentStrings) {
                appContainer.epubParser.imagePlaceholder = currentStrings.reader.imagePlaceholder
            }

            // 计算全局主题
            val darkTheme = when (settingsState.themeMode) {
                "light" -> false
                "dark" -> true
                "paper" -> false // 纸质护眼模式偏浅色底色
                else -> isSystemInDarkTheme()
            }

            // 计算全局字体
            val currentFontFamily = when (settingsState.appFont) {
                "system" -> FontFamily.Default
                else -> ReadingFont // "harmony" → HarmonyOS Sans SC
            }
            val customTypography = Typography.withFontFamily(currentFontFamily)

            CompositionLocalProvider(LocalAppStrings provides currentStrings) {
                ShuLiTheme(
                    darkTheme = darkTheme,
                    typography = customTypography
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        when (val screen = currentScreen) {
                            is ActiveScreen.Bookshelf -> {
                                BookshelfScreen(
                                    viewModel = bookshelfViewModel,
                                    onNavigateToSettings = { currentScreen = ActiveScreen.Settings },
                                    onNavigateToReader = { bookId ->
                                        currentScreen = ActiveScreen.Reader(bookId)
                                    },
                                    onNavigateToStats = { currentScreen = ActiveScreen.Stats },
                                    onNavigateToGlobalSearch = {
                                        val bookshelfState = bookshelfViewModel.uiState.value
                                        currentScreen = ActiveScreen.GlobalSearch(
                                            currentGroupBookIds = bookshelfState.nodes.toSearchScopeBookIds(),
                                            currentGroupLabel = bookshelfState.toSearchScopeLabel(),
                                        )
                                    },
                                )
                            }
                            is ActiveScreen.Stats -> {
                                BackHandler { currentScreen = ActiveScreen.Bookshelf }
                                StatsScreen(
                                    viewModel = statsViewModel,
                                    onBackClick = { currentScreen = ActiveScreen.Bookshelf },
                                    onBookClick = { bookId ->
                                        currentScreen = ActiveScreen.Reader(bookId)
                                    },
                                )
                            }
                            is ActiveScreen.Settings -> {
                                BackHandler { currentScreen = ActiveScreen.Bookshelf }
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBackClick = { currentScreen = ActiveScreen.Bookshelf },
                                    appContainer = appContainer,
                                )
                            }
                            is ActiveScreen.GlobalSearch -> {
                                BackHandler { currentScreen = ActiveScreen.Bookshelf }
                                LaunchedEffect(screen.currentGroupBookIds, screen.currentGroupLabel) {
                                    globalSearchViewModel.configureScopeContext(
                                        currentGroupBookIds = screen.currentGroupBookIds,
                                        currentGroupLabel = screen.currentGroupLabel,
                                    )
                                }
                                GlobalSearchScreen(
                                    viewModel = globalSearchViewModel,
                                    onNavigateBack = { currentScreen = ActiveScreen.Bookshelf },
                                    onResultClick = { bookId, chapterIndex, byteOffset ->
                                        currentScreen = ActiveScreen.Reader(
                                            bookId = bookId,
                                            jumpTarget = ReaderJumpTarget(
                                                chapterIndex = chapterIndex,
                                                byteOffset = byteOffset,
                                            ),
                                        )
                                    },
                                )
                            }
                            is ActiveScreen.Reader -> {
                                BackHandler { currentScreen = ActiveScreen.Bookshelf }
                                val context = LocalContext.current
                                val readerViewModel = remember(screen.bookId) {
                                    ReaderViewModel(
                                        bookId = screen.bookId,
                                        userPreferences = appContainer.userPreferences,
                                        bookContentRepository = appContainer.bookContentRepository,
                                        bookQueryRepository = appContainer.bookQueryRepository,
                                        readingProgressRepository = appContainer.readingProgressRepository,
                                        searchIndexRepository = appContainer.searchIndexRepository,
                                        tagRepository = appContainer.tagRepository,
                                        bookmarkDao = appContainer.database.bookmarkDao(),
                                        noteDao = appContainer.database.noteDao(),
                                        presetDao = appContainer.database.readerPresetDao(),
                                        bookReaderPrefsDao = appContainer.database.bookReaderPrefsDao(),
                                        readingProgressDao = appContainer.database.readingProgressDao(),
                                        chapterReadingStatsDao = appContainer.database.chapterReadingStatsDao(),
                                        readingSessionDao = appContainer.database.readingSessionDao(),
                                        dictMetaDao = appContainer.database.dictMetaDao(),
                                        dictHistoryDao = appContainer.database.dictHistoryDao(),
                                        wordBookDao = appContainer.database.wordBookDao(),
                                        fontManager = FontManager(context),
                                        stringResolver = { currentStrings },
                                        appContext = context.applicationContext,
                                    )
                                }
                                val readerUiState by readerViewModel.uiState.collectAsState()
                                LaunchedEffect(
                                    screen.jumpTarget,
                                    readerUiState.bookId,
                                    readerUiState.isLoading,
                                    readerUiState.currentChapter,
                                    readerUiState.totalChapters,
                                    readerUiState.error,
                                ) {
                                    val target = screen.jumpTarget ?: return@LaunchedEffect
                                    val ready = readerUiState.bookId == screen.bookId &&
                                        !readerUiState.isLoading &&
                                        readerUiState.error == null &&
                                        readerUiState.totalChapters > 0 &&
                                        readerUiState.currentChapter != null
                                    if (ready) {
                                        readerViewModel.dispatch(
                                            ReaderIntent.JumpToPosition(
                                                chapterIndex = target.chapterIndex,
                                                byteOffset = target.byteOffset,
                                            )
                                        )
                                        currentScreen = ActiveScreen.Reader(screen.bookId)
                                    }
                                }
                                // 音量键翻页：设置/清理 ViewModel 引用
                                LaunchedEffect(readerViewModel) {
                                    currentReaderViewModel = readerViewModel
                                }
                                DisposableEffect(Unit) {
                                    onDispose { currentReaderViewModel = null }
                                }
                                ReaderScreen(
                                    bookId = screen.bookId,
                                    onBackClick = { currentScreen = ActiveScreen.Bookshelf },
                                    viewModel = readerViewModel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
