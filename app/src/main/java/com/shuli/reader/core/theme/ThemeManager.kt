package com.shuli.reader.core.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ReaderTheme as ReaderThemeId
import com.shuli.reader.ui.theme.ReaderColorScheme
import com.shuli.reader.ui.theme.toReaderColorScheme
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
        private fun ReaderThemeId.toLegacyTheme(id: String, name: String): ReaderTheme {
            return toReaderColorScheme().toLegacyTheme(id = id, name = name)
        }

        private fun ReaderColorScheme.toLegacyTheme(id: String, name: String): ReaderTheme {
            return ReaderTheme(
                id = id,
                name = name,
                background = background,
                textColor = textPrimary,
                secondaryTextColor = textSecondary,
                accentColor = accent,
                borderColor = divider,
            )
        }

        val DarkTheme = ReaderThemeId.DARK.toLegacyTheme(
            id = "dark",
            name = "Dark",
        )

        val LightTheme = ReaderThemeId.LIGHT.toLegacyTheme(
            id = "light",
            name = "Light",
        )

        val PaperTheme = ReaderThemeId.PAPER.toLegacyTheme(
            id = "paper",
            name = "Paper",
        )

        val OledTheme = ReaderThemeId.OLED.toLegacyTheme(
            id = "oled",
            name = "OLED",
        )

        val BUILTIN_THEMES = listOf(PaperTheme, LightTheme, DarkTheme, OledTheme)
    }

    private fun getDefaultTheme(): ReaderTheme = PaperTheme

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
