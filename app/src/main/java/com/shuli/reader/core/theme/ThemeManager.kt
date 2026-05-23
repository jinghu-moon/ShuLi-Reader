package com.shuli.reader.core.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ReaderTheme(
    val id: String,
    val name: String,
    val background: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
    val accentColor: Color,
    val borderColor: Color,
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.6f,
    val pageMargin: Dp = 16.dp,
)

class ThemeManager(
    private val context: Context,
) {
    private val _currentTheme = MutableStateFlow(getDefaultTheme())
    val currentTheme: StateFlow<ReaderTheme> = _currentTheme

    companion object {
        // Geist 风格暗色主题
        val DarkTheme = ReaderTheme(
            id = "dark",
            name = "暗色",
            background = Color(0xFF080808),
            textColor = Color(0xFFF5F5F5),
            secondaryTextColor = Color(0xFFA1A1AA),
            accentColor = Color(0xFF4F8CFF),
            borderColor = Color(0x14FFFFFF),
        )

        // Geist 风格亮色主题
        val LightTheme = ReaderTheme(
            id = "light",
            name = "亮色",
            background = Color(0xFFFAFAFA),
            textColor = Color(0xFF0A0A0A),
            secondaryTextColor = Color(0xFF71717A),
            accentColor = Color(0xFF3B82F6),
            borderColor = Color(0x14000000),
        )

        // 纸质阅读主题
        val PaperTheme = ReaderTheme(
            id = "paper",
            name = "纸质",
            background = Color(0xFFF5EDE0),
            textColor = Color(0xFF3A3028),
            secondaryTextColor = Color(0xFF8B7D6B),
            accentColor = Color(0xFFD97706),
            borderColor = Color(0x24000000),
        )

        // 沉浸式暗色主题 (适合阅读)
        val ImmersiveDarkTheme = ReaderTheme(
            id = "immersive_dark",
            name = "沉浸暗色",
            background = Color(0xFF000000),
            textColor = Color(0xFFE5E5E5),
            secondaryTextColor = Color(0xFF737373),
            accentColor = Color(0xFF4F8CFF),
            borderColor = Color(0x0FFFFFFF),
        )

        val BUILTIN_THEMES = listOf(DarkTheme, LightTheme, PaperTheme, ImmersiveDarkTheme)
    }

    private fun getDefaultTheme(): ReaderTheme = DarkTheme

    fun setTheme(theme: ReaderTheme) {
        _currentTheme.value = theme
    }

    fun setThemeById(id: String) {
        val theme = BUILTIN_THEMES.find { it.id == id } ?: getDefaultTheme()
        _currentTheme.value = theme
    }

    fun getThemeById(id: String): ReaderTheme? {
        return BUILTIN_THEMES.find { it.id == id }
    }

    fun getAllThemes(): List<ReaderTheme> = BUILTIN_THEMES
}
