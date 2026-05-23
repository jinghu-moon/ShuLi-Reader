package com.shuli.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.shuli.reader.feature.settings.SettingsEvent
import com.shuli.reader.feature.settings.SettingsScreen
import com.shuli.reader.feature.settings.SettingsViewModel
import com.shuli.reader.ui.theme.ReadingFont
import com.shuli.reader.ui.theme.ShuLiTheme
import com.shuli.reader.ui.theme.Typography
import com.shuli.reader.ui.theme.withFontFamily

enum class ActiveScreen {
    BOOKSHELF,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as ShuLiApplication).appContainer
        val bookshelfViewModel = BookshelfViewModel(appContainer.bookRepository)
        val settingsViewModel = SettingsViewModel(appContainer.userPreferences)

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()
            var currentScreen by remember { mutableStateOf(ActiveScreen.BOOKSHELF) }

            // 监听 Activity 重建事件
            LaunchedEffect(Unit) {
                settingsViewModel.events.collect { event ->
                    if (event is SettingsEvent.Recreate) {
                        recreate()
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
                        when (currentScreen) {
                            ActiveScreen.BOOKSHELF -> {
                                BookshelfScreen(
                                    viewModel = bookshelfViewModel,
                                    onNavigateToSettings = { currentScreen = ActiveScreen.SETTINGS },
                                    onNavigateToReader = { bookId ->
                                        // TODO: 导航到阅读器
                                    }
                                )
                            }
                            ActiveScreen.SETTINGS -> {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBackClick = { currentScreen = ActiveScreen.BOOKSHELF }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
