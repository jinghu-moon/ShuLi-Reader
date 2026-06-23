package com.shuli.reader.core.data

import com.shuli.reader.core.reader.engine.animation.PageDelegateFactory
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPreferencesTest {

    @Test
    fun defaultReaderPreferences_haveCorrectValues() {
        val prefs = ReaderPreferences()
        assertEquals(16f, prefs.fontSize, 0.01f)
        assertEquals(1.5f, prefs.lineSpacing, 0.01f)
        assertEquals(PageAnimType.HORIZONTAL, prefs.pageAnimType)
        assertEquals(ReaderTheme.PAPER, prefs.backgroundColor)
        assertEquals(24f, prefs.bodyBox.left, 0.01f)
        assertEquals(48f, prefs.bodyBox.top, 0.01f)
    }

    @Test
    fun copy_canModifySingleField() {
        val prefs = ReaderPreferences()
        val modified = prefs.copy(fontSize = 20f)
        assertEquals(20f, modified.fontSize, 0.01f)
        assertEquals(prefs.lineSpacing, modified.lineSpacing, 0.01f)
        assertEquals(prefs.pageAnimType, modified.pageAnimType)
        assertEquals(prefs.backgroundColor, modified.backgroundColor)
    }

    @Test
    fun lightTheme_returnsCorrectColors() {
        val colors = ReaderTheme.LIGHT.toReaderColorScheme().toCanvasThemeColors()
        assertEquals(0xFFF6F4F0.toInt(), colors.backgroundColor)
        assertEquals(0xFF2C231A.toInt(), colors.textColor)
        assertEquals(0xFF7D7162.toInt(), colors.headerColor)
        assertEquals(0xFF7D7162.toInt(), colors.footerColor)
        assertEquals(0xFF453B2E.toInt(), colors.progressColor)
        assertEquals(0xFF453B2E.toInt(), colors.accentColor)
    }

    @Test
    fun darkTheme_returnsCorrectColors() {
        val colors = ReaderTheme.DARK.toReaderColorScheme().toCanvasThemeColors()
        assertEquals(0xFF1A130B.toInt(), colors.backgroundColor)
        assertEquals(0xFFEAE5DC.toInt(), colors.textColor)
        assertEquals(0xFF9C9082.toInt(), colors.headerColor)
        assertEquals(0xFF9C9082.toInt(), colors.footerColor)
        assertEquals(0xFFD4CCC0.toInt(), colors.progressColor)
        assertEquals(0xFFD4CCC0.toInt(), colors.accentColor)
    }

    @Test
    fun paperTheme_returnsCorrectColors() {
        val colors = ReaderTheme.PAPER.toReaderColorScheme().toCanvasThemeColors()
        assertEquals(0xFFEAE5DC.toInt(), colors.backgroundColor)
        assertEquals(0xFF2C231A.toInt(), colors.textColor)
        assertEquals(0xFF7D7162.toInt(), colors.headerColor)
        assertEquals(0xFF7D7162.toInt(), colors.footerColor)
        assertEquals(0xFF453B2E.toInt(), colors.progressColor)
        assertEquals(0xFF453B2E.toInt(), colors.accentColor)
    }

    @Test
    fun oledTheme_returnsCorrectColors() {
        val colors = ReaderTheme.OLED.toReaderColorScheme().toCanvasThemeColors()
        assertEquals(0xFF0C0804.toInt(), colors.backgroundColor)
        assertEquals(0xFFEAE5DC.toInt(), colors.textColor)
        assertEquals(0xFF9C9082.toInt(), colors.headerColor)
        assertEquals(0xFF9C9082.toInt(), colors.footerColor)
        assertEquals(0xFFD4CCC0.toInt(), colors.progressColor)
        assertEquals(0xFFD4CCC0.toInt(), colors.accentColor)
    }

    @Test
    fun allThemeTextContrast_meetsAccessibilityRequirement() {
        // 正文对比度 >= 4.5:1
        for (theme in ReaderTheme.entries) {
            val colors = theme.toReaderColorScheme().toCanvasThemeColors()
            val contrast = calculateContrastRatio(colors.textColor, colors.backgroundColor)
            assertTrue("主题 $theme 正文对比度应不低于 4.5:1", contrast >= 4.5)
        }
    }

    @Test
    fun nonePageAnimation_mapsCorrectly() {
        assertEquals(PageDelegateFactory.PageAnimType.NONE, PageAnimType.NONE.toFactoryType())
    }

    @Test
    fun coverPageAnimation_mapsCorrectly() {
        assertEquals(PageDelegateFactory.PageAnimType.COVER, PageAnimType.COVER.toFactoryType())
    }

    @Test
    fun horizontalPageAnimation_mapsCorrectly() {
        assertEquals(PageDelegateFactory.PageAnimType.HORIZONTAL, PageAnimType.HORIZONTAL.toFactoryType())
    }

    @Test
    fun simulationPageAnimation_mapsCorrectly() {
        assertEquals(PageDelegateFactory.PageAnimType.SIMULATION, PageAnimType.SIMULATION.toFactoryType())
    }

    @Test
    fun scrollPageAnimation_mapsCorrectly() {
        assertEquals(PageDelegateFactory.PageAnimType.SCROLL, PageAnimType.SCROLL.toFactoryType())
    }

    @Test
    fun verticalSlidePageAnimation_mapsCorrectly() {
        assertEquals(PageDelegateFactory.PageAnimType.VERTICAL_SLIDE, PageAnimType.VERTICAL_SLIDE.toFactoryType())
    }

    @Test
    fun stringToPageAnimType_mapsKnownValuesCorrectly() {
        assertEquals(PageAnimType.NONE, PageAnimConst.NONE.toPageAnimType())
        assertEquals(PageAnimType.COVER, PageAnimConst.OVERLAY.toPageAnimType())
        assertEquals(PageAnimType.HORIZONTAL, PageAnimConst.SLIDE.toPageAnimType())
        assertEquals(PageAnimType.SIMULATION, PageAnimConst.SIMULATION.toPageAnimType())
        assertEquals(PageAnimType.VERTICAL_SLIDE, PageAnimConst.VERTICAL_SLIDE.toPageAnimType())
        assertEquals(PageAnimType.SCROLL, PageAnimConst.SCROLL.toPageAnimType())
    }

    @Test
    fun pageAnimTypeToStorageString_usesStablePreferenceValues() {
        assertEquals(PageAnimConst.NONE, PageAnimType.NONE.toStorageString())
        assertEquals(PageAnimConst.OVERLAY, PageAnimType.COVER.toStorageString())
        assertEquals(PageAnimConst.SLIDE, PageAnimType.HORIZONTAL.toStorageString())
        assertEquals(PageAnimConst.SIMULATION, PageAnimType.SIMULATION.toStorageString())
        assertEquals(PageAnimConst.VERTICAL_SLIDE, PageAnimType.VERTICAL_SLIDE.toStorageString())
        assertEquals(PageAnimConst.SCROLL, PageAnimType.SCROLL.toStorageString())
    }

    @Test
    fun stringToPageAnimType_acceptsLegacyEnumNames() {
        assertEquals(PageAnimType.NONE, "NONE".toPageAnimType())
        assertEquals(PageAnimType.COVER, "COVER".toPageAnimType())
        assertEquals(PageAnimType.HORIZONTAL, "HORIZONTAL".toPageAnimType())
        assertEquals(PageAnimType.SIMULATION, "SIMULATION".toPageAnimType())
        assertEquals(PageAnimType.VERTICAL_SLIDE, "VERTICAL_SLIDE".toPageAnimType())
        assertEquals(PageAnimType.SCROLL, "SCROLL".toPageAnimType())
    }

    @Test
    fun stringToPageAnimSpeed_acceptsEnumNamesAndDurations() {
        assertEquals(PageAnimSpeed.FAST, "FAST".toPageAnimSpeed())
        assertEquals(PageAnimSpeed.SLOW, "SLOW".toPageAnimSpeed())
        assertEquals(PageAnimSpeed.FAST, "100".toPageAnimSpeed())
        assertEquals(PageAnimSpeed.NORMAL, "250".toPageAnimSpeed())
        assertEquals(PageAnimSpeed.SLOW, "400".toPageAnimSpeed())
    }

    @Test
    fun unknownStringToPageAnimType_defaultsToHorizontal() {
        assertEquals(PageAnimType.HORIZONTAL, "unknown".toPageAnimType())
    }

    /**
     * 简化的 WCAG 对比度计算
     */
    private fun calculateContrastRatio(foreground: Int, background: Int): Double {
        val fgLuminance = getRelativeLuminance(foreground)
        val bgLuminance = getRelativeLuminance(background)
        val lighter = maxOf(fgLuminance, bgLuminance)
        val darker = minOf(fgLuminance, bgLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun getRelativeLuminance(color: Int): Double {
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
