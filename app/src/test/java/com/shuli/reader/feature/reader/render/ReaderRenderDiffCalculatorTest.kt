package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.reader.engine.animation.PageDelegateFactory
import com.shuli.reader.core.reader.model.TextPage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRenderDiffCalculatorTest {

    @Test
    fun diff_oldIsNull_returnsPage() {
        val new = createDefaultSnapshot()
        val diff = ReaderRenderDiffCalculator.diff(null, new)
        assertTrue(diff.scopes.contains(InvalidationScope.PAGE))
    }

    @Test
    fun diff_currentPageNullToNonNull_returnsPage() {
        val old = createDefaultSnapshot(page = createDefaultPageSnapshot(currentPage = null))
        val new = createDefaultSnapshot(page = createDefaultPageSnapshot(currentPage = TextPage.EMPTY))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.PAGE))
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
        val old = createDefaultSnapshot(layout = createLayoutSnapshot(marginLeftDp = 24f))
        val new = createDefaultSnapshot(layout = createLayoutSnapshot(marginLeftDp = 32f))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertTrue(diff.scopes.contains(InvalidationScope.REFLOW))
    }

    @Test
    fun diff_textAlignChanged_returnsEmptyScopes() {
        // textAlign 变化由 key-diff 驱动，不再通过 scope 失效
        val old = createDefaultSnapshot(visual = createVisualSnapshot(textAlign = ReaderTextAlign.LEFT))
        val new = createDefaultSnapshot(visual = createVisualSnapshot(textAlign = ReaderTextAlign.JUSTIFY))
        val diff = ReaderRenderDiffCalculator.diff(old, new)
        assertFalse("textAlign 不应触发 scope-based 失效", diff.scopes.contains(InvalidationScope.REFLOW))
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
