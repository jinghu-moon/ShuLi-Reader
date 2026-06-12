package com.shuli.reader.feature.reader

import com.shuli.reader.core.tts.TtsHighlightController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TtsHighlightTest {

    // T-3.2.1: 高亮范围与当前朗读句子一致
    @Test
    fun updateHighlight_setsCorrectRange() {
        val controller = TtsHighlightController()
        controller.updateHighlight(100, 150)

        val range = controller.currentRange
        assertNotNull(range)
        assertEquals(100, range!!.start)
        assertEquals(150, range.end)
    }

    @Test
    fun updateHighlight_updatesRange() {
        val controller = TtsHighlightController()
        controller.updateHighlight(100, 150)
        controller.updateHighlight(200, 250)

        val range = controller.currentRange
        assertNotNull(range)
        assertEquals(200, range!!.start)
        assertEquals(250, range.end)
    }

    @Test
    fun clearHighlight_removesRange() {
        val controller = TtsHighlightController()
        controller.updateHighlight(100, 150)
        controller.clearHighlight()

        assertNull(controller.currentRange)
    }

    // T-3.2.2: 高亮范围与行相交检查
    @Test
    fun intersectsLine_overlapping_returnsTrue() {
        val controller = TtsHighlightController()
        controller.updateHighlight(100, 150)

        assertEquals(true, controller.intersectsLine(80, 120))
    }

    @Test
    fun intersectsLine_contained_returnsTrue() {
        val controller = TtsHighlightController()
        controller.updateHighlight(100, 150)

        assertEquals(true, controller.intersectsLine(110, 140))
    }

    @Test
    fun intersectsLine_noOverlap_returnsFalse() {
        val controller = TtsHighlightController()
        controller.updateHighlight(100, 150)

        assertEquals(false, controller.intersectsLine(200, 250))
    }

    @Test
    fun intersectsLine_before_returnsFalse() {
        val controller = TtsHighlightController()
        controller.updateHighlight(100, 150)

        assertEquals(false, controller.intersectsLine(50, 90))
    }

    // T-3.2.3: 无高亮时 intersectsLine 返回 false
    @Test
    fun intersectsLine_noHighlight_returnsFalse() {
        val controller = TtsHighlightController()
        assertEquals(false, controller.intersectsLine(0, 100))
    }
}
