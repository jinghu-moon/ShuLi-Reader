package com.shuli.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
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
import com.shuli.reader.feature.reader.ReaderScreen
import com.shuli.reader.feature.reader.ReaderViewModel
import com.shuli.reader.feature.settings.SettingsEvent
import com.shuli.reader.feature.settings.SettingsScreen
import com.shuli.reader.feature.settings.SettingsViewModel
import com.shuli.reader.ui.theme.ReadingFont
import com.shuli.reader.ui.theme.ShuLiTheme
import com.shuli.reader.ui.theme.Typography
import com.shuli.reader.ui.theme.withFontFamily
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


sealed class ActiveScreen {
    data object Bookshelf : ActiveScreen()
    data object Settings : ActiveScreen()
    data class Reader(val bookId: Long) : ActiveScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as ShuLiApplication).appContainer
        val bookshelfViewModel = BookshelfViewModel(appContainer.bookRepository, appContainer.userPreferences)
        val settingsViewModel = SettingsViewModel(appContainer.userPreferences)

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
                            appContainer.bookRepository.importBook(java.io.File(testFilePath))
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
                else -> ReadingFont
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
                                    }
                                )
                            }
                            is ActiveScreen.Settings -> {
                                BackHandler { currentScreen = ActiveScreen.Bookshelf }
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBackClick = { currentScreen = ActiveScreen.Bookshelf }
                                )
                            }
                            is ActiveScreen.Reader -> {
                                BackHandler { currentScreen = ActiveScreen.Bookshelf }
                                val readerViewModel = remember(screen.bookId) {
                                    ReaderViewModel(
                                        userPreferences = appContainer.userPreferences,
                                        bookRepository = appContainer.bookRepository,
                                        bookmarkDao = appContainer.database.bookmarkDao(),
                                        noteDao = appContainer.database.noteDao(),
                                    )
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
