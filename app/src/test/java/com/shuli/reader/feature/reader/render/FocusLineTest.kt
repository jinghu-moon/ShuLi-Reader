package com.shuli.reader.feature.reader.render

import com.shuli.reader.feature.reader.settings.ReaderSettingRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusLineTest {

    // T-1.6.1: focusLine = false 时不绘制
    @Test
    fun focusLine_disabled_shouldNotDraw() {
        val config = FocusLineConfig.fromReadingLine(
            enabled = false,
            currentReadingLineY = 500f,
            marginLeft = 24f,
            pageWidth = 1080f,
            marginRight = 24f,
        )
        assertFalse("should not draw when disabled", config.shouldDraw)
    }

    // T-1.6.2: focusLine = true 时在当前阅读行绘制
    @Test
    fun focusLine_enabled_withValidY_shouldDraw() {
        val config = FocusLineConfig.fromReadingLine(
            enabled = true,
            currentReadingLineY = 500f,
            marginLeft = 24f,
            pageWidth = 1080f,
            marginRight = 24f,
        )
        assertTrue("should draw when enabled with valid Y", config.shouldDraw)
        assertEquals(500f, config.lineY, 0.01f)
    }

    @Test
    fun focusLine_enabled_drawsAtCorrectXRange() {
        val config = FocusLineConfig.fromReadingLine(
            enabled = true,
            currentReadingLineY = 500f,
            marginLeft = 24f,
            pageWidth = 1080f,
            marginRight = 24f,
        )
        assertEquals(24f, config.startX, 0.01f)
        assertEquals(1056f, config.endX, 0.01f)
    }

    // T-1.6.3: 无当前阅读行时不绘制
    @Test
    fun focusLine_nullReadingLine_shouldNotDraw() {
        val config = FocusLineConfig.fromReadingLine(
            enabled = true,
            currentReadingLineY = null,
            marginLeft = 24f,
            pageWidth = 1080f,
            marginRight = 24f,
        )
        assertFalse("should not draw when no reading line", config.shouldDraw)
    }

    // T-1.6.4: Registry 注册 focus_line 为 VIEW_INVALIDATE
    @Test
    fun registry_focusLine_hasViewInvalidateScope() {
        val def = ReaderSettingRegistry.all.first { it.key == "focus_line" }
        assertEquals(
            InvalidationScope.VIEW_INVALIDATE,
            def.scope,
        )
    }

    @Test
    fun registry_focusLine_defaultIsFalse() {
        val default = ReaderSettingRegistry.getDefault<Boolean>("focus_line")
        assertEquals(false, default)
    }
}
