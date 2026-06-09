package com.shuli.reader.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
        ReaderTheme.CUSTOM -> ReaderPaperColorScheme // fallback，实际由 resolveCustomColorScheme 处理
    }
}

/**
 * 从自定义 ARGB 颜色值构建 ReaderColorScheme。
 *
 * 只需指定 background、textPrimary、accent 三个核心色，
 * 其余派生色自动计算（降低用户配置负担）。
 */
fun resolveCustomColorScheme(
    backgroundColor: Int,
    textColor: Int,
    accentColor: Int,
): ReaderColorScheme {
    val bg = Color(backgroundColor)
    val text = Color(textColor)
    val accent = Color(accentColor)
    // 根据背景亮度判断深浅模式，自动派生辅助色
    val bgLuminance = bg.luminance()
    val isDark = bgLuminance < 0.5f
    return ReaderColorScheme(
        background = bg,
        surface = if (isDark) bg.lighten(0.08f) else bg.darken(0.04f),
        textPrimary = text,
        textSecondary = text.copy(alpha = if (isDark) 0.7f else 0.6f),
        textTertiary = text.copy(alpha = if (isDark) 0.5f else 0.4f),
        accent = accent,
        divider = text.copy(alpha = if (isDark) 0.15f else 0.12f),
        overlay = bg.copy(alpha = 0.9f),
        selection = accent.copy(alpha = 0.2f),
        highlight = accent.copy(alpha = 0.4f),
    )
}

/** 颜色亮度提升 */
private fun Color.lighten(factor: Float): Color {
    return Color(
        red = (red + (1f - red) * factor).coerceIn(0f, 1f),
        green = (green + (1f - green) * factor).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

/** 颜色亮度降低 */
private fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1f - factor)).coerceIn(0f, 1f),
        green = (green * (1f - factor)).coerceIn(0f, 1f),
        blue = (blue * (1f - factor)).coerceIn(0f, 1f),
        alpha = alpha,
    )
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
