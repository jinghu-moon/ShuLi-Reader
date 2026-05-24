package com.shuli.reader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.shuli.reader.core.data.ReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSchemeTest {

    @Test
    fun appLightTokens_followMoTuWarmWhitePalette() {
        assertEquals(0xFFF6F4F0.toInt(), AppBackground.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), AppSurface.toArgb())
        assertEquals(0xFF453B2E.toInt(), AppPrimary.toArgb())
        assertTrue(contrastRatio(AppTextPrimary, AppBackground) >= 4.5)
        assertTrue(contrastRatio(AppTextSecondary, AppBackground) >= 4.5)
    }

    @Test
    fun appDarkTokens_avoidPureBlackAndWhiteContrast() {
        assertEquals(0xFF0C0804.toInt(), AppDarkBackground.toArgb())
        assertEquals(0xFFEAE5DC.toInt(), AppDarkTextPrimary.toArgb())
        assertNotEquals(0xFF000000.toInt(), AppDarkBackground.toArgb())
        assertNotEquals(0xFFFFFFFF.toInt(), AppDarkTextPrimary.toArgb())
        assertTrue(contrastRatio(AppDarkTextPrimary, AppDarkBackground) >= 4.5)
    }

    @Test
    fun readerPaperTheme_isIndependentFromAppWarmWhiteBackground() {
        assertEquals(0xFFEAE5DC.toInt(), ReaderPaperColorScheme.background.toArgb())
        assertEquals(0xFF2C231A.toInt(), ReaderPaperColorScheme.textPrimary.toArgb())
        assertNotEquals(AppBackground.toArgb(), ReaderPaperColorScheme.background.toArgb())
        assertTrue(contrastRatio(ReaderPaperColorScheme.textPrimary, ReaderPaperColorScheme.background) >= 4.5)
    }

    @Test
    fun readerTheme_bridgesThroughReaderColorSchemeToCanvasColors() {
        ReaderTheme.entries.forEach { theme ->
            val readerColors = theme.toReaderColorScheme()
            val canvasColors = readerColors.toCanvasThemeColors()

            assertEquals(readerColors.background.toArgb(), canvasColors.backgroundColor)
            assertEquals(readerColors.textPrimary.toArgb(), canvasColors.textColor)
            assertEquals(readerColors.textSecondary.toArgb(), canvasColors.headerColor)
            assertEquals(readerColors.textSecondary.toArgb(), canvasColors.footerColor)
            assertEquals(readerColors.accent.toArgb(), canvasColors.progressColor)
            assertEquals(readerColors.accent.toArgb(), canvasColors.accentColor)
        }
    }

    @Test
    fun defaultReaderTheme_avoidsPureWhiteAndPureBlackTextSurface() {
        val paper = ReaderTheme.PAPER.toReaderColorScheme()
        assertNotEquals(0xFFFFFFFF.toInt(), paper.background.toArgb())
        assertNotEquals(0xFF000000.toInt(), paper.textPrimary.toArgb())
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val foregroundLuminance = relativeLuminance(foreground)
        val backgroundLuminance = relativeLuminance(background)
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val normalized = value.toDouble()
            return if (normalized <= 0.03928) {
                normalized / 12.92
            } else {
                Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
        }

        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
