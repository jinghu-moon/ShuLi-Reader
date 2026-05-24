package com.shuli.reader.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.ThemeColors

data class ReaderColorScheme(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val divider: Color,
    val overlay: Color,
    val selection: Color,
    val highlight: Color,
)

val ReaderPaperColorScheme = ReaderColorScheme(
    background = MoTuInk100,
    surface = MoTuInk50,
    textPrimary = MoTuInk800,
    textSecondary = MoTuInk500,
    textTertiary = MoTuInk400,
    accent = MoTuInk700,
    divider = MoTuInk200,
    overlay = Color(0xE6F6F4F0),
    selection = Color(0x33453B2E),
    highlight = Color(0x66D4CCC0),
)

val ReaderLightColorScheme = ReaderColorScheme(
    background = MoTuInk50,
    surface = Color(0xFFFFFFFF),
    textPrimary = MoTuInk800,
    textSecondary = MoTuInk500,
    textTertiary = MoTuInk400,
    accent = MoTuInk700,
    divider = MoTuInk200,
    overlay = Color(0xE6FFFFFF),
    selection = Color(0x33453B2E),
    highlight = Color(0x66D4CCC0),
)

val ReaderDarkColorScheme = ReaderColorScheme(
    background = MoTuInk900,
    surface = MoTuInk800,
    textPrimary = MoTuInk100,
    textSecondary = MoTuInk400,
    textTertiary = MoTuInk600,
    accent = MoTuInk200,
    divider = MoTuInk700,
    overlay = Color(0xE62C231A),
    selection = Color(0x33D4CCC0),
    highlight = Color(0x559C9082),
)

val ReaderOledColorScheme = ReaderColorScheme(
    background = MoTuInk950,
    surface = MoTuInk900,
    textPrimary = MoTuInk100,
    textSecondary = MoTuInk400,
    textTertiary = MoTuInk600,
    accent = MoTuInk200,
    divider = MoTuInk700,
    overlay = Color(0xE61A130B),
    selection = Color(0x33D4CCC0),
    highlight = Color(0x559C9082),
)

val LocalReaderColorScheme = staticCompositionLocalOf { ReaderPaperColorScheme }

fun ReaderTheme.toReaderColorScheme(): ReaderColorScheme {
    return when (this) {
        ReaderTheme.LIGHT -> ReaderLightColorScheme
        ReaderTheme.DARK -> ReaderDarkColorScheme
        ReaderTheme.PAPER -> ReaderPaperColorScheme
        ReaderTheme.OLED -> ReaderOledColorScheme
    }
}

fun ReaderColorScheme.toCanvasThemeColors(): ThemeColors {
    return ThemeColors(
        backgroundColor = background.toArgb(),
        textColor = textPrimary.toArgb(),
        headerColor = textSecondary.toArgb(),
        footerColor = textSecondary.toArgb(),
        progressColor = accent.toArgb(),
        accentColor = accent.toArgb(),
    )
}
