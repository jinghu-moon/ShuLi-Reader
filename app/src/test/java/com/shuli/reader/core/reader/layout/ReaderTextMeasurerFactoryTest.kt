package com.shuli.reader.core.reader.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextMeasurerFactoryTest {

    @Test
    fun create_withValidInput_returnsTextMeasurer() {
        val input = createDefaultLayoutInput(fontSizeSp = 18f, density = 3f)
        val measurer = ReaderTextMeasurerFactory.create(input)
        assertNotNull(measurer)
    }

    @Test
    fun toLayoutConfig_correctTextSizePx() {
        val input = createDefaultLayoutInput(fontSizeSp = 20f, density = 2f)
        val config = ReaderTextMeasurerFactory.toLayoutConfig(input)
        assertEquals(40f, config.textSize, 0.01f)
    }

    @Test
    fun toLayoutConfig_correctPageSize() {
        val input = createDefaultLayoutInput(viewportWidth = 1080, viewportHeight = 1920)
        val config = ReaderTextMeasurerFactory.toLayoutConfig(input)
        assertEquals(1080, config.pageSize.width)
        assertEquals(1920, config.pageSize.height)
    }

    @Test
    fun toLayoutConfig_correctMargins() {
        val input = createDefaultLayoutInput(
            marginHorizontalDp = 24f,
            marginVerticalDp = 48f,
            density = 3f,
        )
        val config = ReaderTextMeasurerFactory.toLayoutConfig(input)
        assertEquals(72f, config.marginHorizontal, 0.01f)
        assertEquals(144f, config.marginVertical, 0.01f)
    }

    @Test
    fun toLayoutConfig_correctLetterSpacingPx() {
        val input = createDefaultLayoutInput(
            fontSizeSp = 20f,
            density = 2f,
            letterSpacing = 0.05f,
        )
        val config = ReaderTextMeasurerFactory.toLayoutConfig(input)
        // 20 * 2 = 40px textSize, 0.05 * 40 = 2px letterSpacing
        assertEquals(2f, config.letterSpacingPx, 0.01f)
    }

    @Test
    fun toLayoutConfig_paragraphSpacingInPx() {
        val input = createDefaultLayoutInput(
            fontSizeSp = 16f,
            density = 3f,
            paragraphSpacing = 1.5f,
        )
        val config = ReaderTextMeasurerFactory.toLayoutConfig(input)
        // 16 * 3 = 48px textSize, 1.5 * 48 = 72px
        assertEquals(72f, config.paragraphSpacing, 0.01f)
    }

    @Test
    fun toLayoutConfig_preservesTitleStyle() {
        val input = createDefaultLayoutInput()
        val config = ReaderTextMeasurerFactory.toLayoutConfig(input)
        assertEquals(input.titleStyle, config.titleStyle)
    }

    @Test
    fun toLayoutConfig_preservesZhLayout() {
        val input = createDefaultLayoutInput(useZhLayout = true)
        val config = ReaderTextMeasurerFactory.toLayoutConfig(input)
        assertTrue(config.useZhLayout)
    }

    @Test
    fun toLayoutConfig_preservesBottomJustify() {
        val input = createDefaultLayoutInput(bottomJustify = true)
        val config = ReaderTextMeasurerFactory.toLayoutConfig(input)
        assertTrue(config.bottomJustify)
    }
}
