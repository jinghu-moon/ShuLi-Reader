package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.reader.layout.createDefaultLayoutInput
import com.shuli.reader.core.reader.model.SelectionRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReaderRenderKeysTest {

    // ── LayoutKey ──

    @Test
    fun layoutKey_sameInput_sameKey() {
        val input = createDefaultLayoutInput()
        val a = ReaderLayoutHasher.hash(input)
        val b = ReaderLayoutHasher.hash(input)
        assertEquals(a, b)
    }

    @Test
    fun layoutKey_differentFontSize_differentKey() {
        val a = ReaderLayoutHasher.hash(createDefaultLayoutInput(fontSizeSp = 16f))
        val b = ReaderLayoutHasher.hash(createDefaultLayoutInput(fontSizeSp = 18f))
        assertNotEquals(a, b)
    }

    @Test
    fun layoutKey_differentLayoutVersion_differentKey() {
        val a = ReaderLayoutHasher.hash(createDefaultLayoutInput(layoutVersion = 1))
        val b = ReaderLayoutHasher.hash(createDefaultLayoutInput(layoutVersion = 2))
        assertNotEquals(a, b)
    }

    @Test
    fun layoutKey_containsLayoutVersion() {
        val key = ReaderLayoutHasher.hash(createDefaultLayoutInput(layoutVersion = 5))
        assertEquals(5, key.layoutVersion)
    }

    @Test
    fun layoutKey_differentFontWeight_differentKey() {
        val a = ReaderLayoutHasher.hash(createDefaultLayoutInput(fontWeight = ReaderFontWeight.NORMAL))
        val b = ReaderLayoutHasher.hash(createDefaultLayoutInput(fontWeight = ReaderFontWeight.BOLD))
        assertNotEquals(a, b)
    }

    @Test
    fun layoutKey_differentChineseConvert_differentKey() {
        val a = ReaderLayoutHasher.hash(createDefaultLayoutInput(chineseConvert = ChineseConvert.NONE))
        val b = ReaderLayoutHasher.hash(createDefaultLayoutInput(chineseConvert = ChineseConvert.SIMPLIFIED))
        assertNotEquals(a, b)
    }

    // ── RenderKey ──

    @Test
    fun renderKey_sameVisual_sameKey() {
        val a = RenderKeyFactory.from(createDefaultVisualSnapshot())
        val b = RenderKeyFactory.from(createDefaultVisualSnapshot())
        assertEquals(a, b)
    }

    @Test
    fun renderKey_differentTextAlign_differentKey() {
        val a = RenderKeyFactory.from(createDefaultVisualSnapshot(textAlign = ReaderTextAlign.LEFT))
        val b = RenderKeyFactory.from(createDefaultVisualSnapshot(textAlign = ReaderTextAlign.JUSTIFY))
        assertNotEquals(a, b)
    }

    // ── OverlayKey ──

    @Test
    fun overlayKey_sameOverlay_sameKey() {
        val a = OverlayKeyFactory.from(createDefaultOverlaySnapshot())
        val b = OverlayKeyFactory.from(createDefaultOverlaySnapshot())
        assertEquals(a, b)
    }

    @Test
    fun overlayKey_differentTtsRange_differentKey() {
        val a = OverlayKeyFactory.from(
            OverlaySnapshot(null, SelectionRange(0, 10, 20, "a"), emptyList(), OverlayKey(null, null, 0))
        )
        val b = OverlayKeyFactory.from(
            OverlaySnapshot(null, SelectionRange(0, 30, 40, "b"), emptyList(), OverlayKey(null, null, 0))
        )
        assertNotEquals(a, b)
    }

    @Test
    fun overlayKey_doesNotAffectLayoutKey() {
        val input = createDefaultLayoutInput()
        val layoutKey = ReaderLayoutHasher.hash(input)
        assertNotNull(layoutKey)
    }
}
