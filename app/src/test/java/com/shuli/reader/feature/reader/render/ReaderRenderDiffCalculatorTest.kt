package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.reader.animation.PageDelegateFactory
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextPage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRenderDiffCalculatorTest {

    @Test
    fun diff_oldIsNull_returnsPageContentShellOverlay() {
        val new = createDefaultSnapshot()
        val diff = ReaderRenderDiffCalculator.diff(null, new)
        assertTrue(diff.scopes.contains(InvalidationScope.PAGE))
        assertTrue(diff.scopes.contains(InvalidationScope.CONTENT))
        assertTrue(diff.scopes.contains(InvalidationScope.SHELL))
        assertTrue(diff.scopes.contains(InvalidationScope.OVERLAY))
    }

    @Test
    fun diff_currentPageNullToNonNull_returnsPageContentShell() {
        val old = createDefaultSnapshot(page = createDefaultPageSnapshot(currentPage = null))
        val new = createDefaultSnapshot(page = createDefaultPageSnapshot(currentPage = TextPage.EMPTY))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.PAGE))
        assertTrue(diff.scopes.contains(InvalidationScope.CONTENT))
        assertTrue(diff.scopes.contains(InvalidationScope.SHELL))
    }

    @Test
    fun diff_currentPageNonNullToNull_returnsNoInvalidation() {
        val old = createDefaultSnapshot(page = createDefaultPageSnapshot(currentPage = TextPage.EMPTY))
        val new = createDefaultSnapshot(page = createDefaultPageSnapshot(currentPage = null))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue("瞬态不应触发 invalidation", diff.scopes.isEmpty())
    }

    @Test
    fun diff_chapterIndexChanged_returnsReflow() {
        val old = createDefaultSnapshot(page = createDefaultPageSnapshot(chapterIndex = 1))
        val new = createDefaultSnapshot(page = createDefaultPageSnapshot(chapterIndex = 2))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_fontSizeChanged_returnsReflow() {
        val old = createDefaultSnapshot(layout = createLayoutSnapshot(fontSizeSp = 16f))
        val new = createDefaultSnapshot(layout = createLayoutSnapshot(fontSizeSp = 18f))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_fontChanged_returnsReflow() {
        val old = createDefaultSnapshot(layout = createLayoutSnapshot(fontKey = "harmony"))
        val new = createDefaultSnapshot(layout = createLayoutSnapshot(fontKey = "system"))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_fontWeightChanged_returnsReflow() {
        val old = createDefaultSnapshot(layout = createLayoutSnapshot(fontWeight = com.shuli.reader.core.data.ReaderFontWeight.NORMAL))
        val new = createDefaultSnapshot(layout = createLayoutSnapshot(fontWeight = com.shuli.reader.core.data.ReaderFontWeight.BOLD))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_lineSpacingChanged_returnsReflow() {
        val old = createDefaultSnapshot(layout = createLayoutSnapshot(lineSpacing = 1.5f))
        val new = createDefaultSnapshot(layout = createLayoutSnapshot(lineSpacing = 2.0f))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_marginChanged_returnsReflow() {
        val old = createDefaultSnapshot(layout = createLayoutSnapshot(marginHorizontalDp = 24f))
        val new = createDefaultSnapshot(layout = createLayoutSnapshot(marginHorizontalDp = 32f))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_textAlignChanged_returnsContent() {
        val old = createDefaultSnapshot(visual = createVisualSnapshot(textAlign = ReaderTextAlign.LEFT))
        val new = createDefaultSnapshot(visual = createVisualSnapshot(textAlign = ReaderTextAlign.JUSTIFY))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.CONTENT))
        assertFalse("textAlign 不应触发 REFLOW", diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_themeChanged_returnsContentAndShell() {
        val themeA = ThemeColors(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF666666.toInt(), 0xFF666666.toInt(), 0xFF333333.toInt(), 0xFF1976D2.toInt())
        val themeB = ThemeColors(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFAAAAAA.toInt(), 0xFFAAAAAA.toInt(), 0xFFCCCCCC.toInt(), 0xFFBB86FC.toInt())
        val old = createDefaultSnapshot(visual = createVisualSnapshot(themeColors = themeA))
        val new = createDefaultSnapshot(visual = createVisualSnapshot(themeColors = themeB))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.CONTENT))
        assertTrue(diff.scopes.contains(InvalidationScope.SHELL))
    }

    @Test
    fun diff_batteryChanged_returnsShell() {
        val old = createDefaultSnapshot(shell = createShellSnapshot(batteryLevel = 80))
        val new = createDefaultSnapshot(shell = createShellSnapshot(batteryLevel = 79))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.SHELL))
        assertFalse(diff.scopes.contains(InvalidationScope.CONTENT))
    }

    @Test
    fun diff_selectionChanged_returnsOverlay() {
        val old = createDefaultSnapshot(overlay = createOverlaySnapshot(selection = null))
        val new = createDefaultSnapshot(overlay = createOverlaySnapshot(selection = SelectionRange(0, 5, 15, "x")))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.OVERLAY))
    }

    @Test
    fun diff_pageIndexChanged_returnsPage() {
        val old = createDefaultSnapshot(page = createDefaultPageSnapshot(pageIndex = 3))
        val new = createDefaultSnapshot(page = createDefaultPageSnapshot(pageIndex = 4))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.PAGE))
    }

    @Test
    fun diff_pageAnimTypeChanged_returnsPageDelegate() {
        val old = createDefaultSnapshot(page = createDefaultPageSnapshot(animType = PageDelegateFactory.PageAnimType.HORIZONTAL))
        val new = createDefaultSnapshot(page = createDefaultPageSnapshot(animType = PageDelegateFactory.PageAnimType.SIMULATION))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.PAGE_DELEGATE))
    }

    @Test
    fun diff_identicalSnapshots_returnsEmpty() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiffCalculator.diff(snapshot, snapshot)
        assertTrue(diff.scopes.isEmpty())
    }
}
