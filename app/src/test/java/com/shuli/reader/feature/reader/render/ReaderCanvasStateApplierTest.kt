package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.reader.engine.RenderApplierTarget
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.TextPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReaderCanvasStateApplierTest {

    private lateinit var fakeCanvas: FakeReaderCanvasView
    private lateinit var applier: ReaderCanvasStateApplier

    @Before
    fun setup() {
        fakeCanvas = FakeReaderCanvasView()
        applier = ReaderCanvasStateApplier()
    }

    @Test
    fun apply_reflowScope_triggersReflow() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.REFLOW))
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.reflowTriggered)
    }

    @Test
    fun apply_pageDelegateScope_rebuildsDelegate() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.PAGE_DELEGATE))
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.delegateRebuilt)
    }

    @Test
    fun apply_reflowExpandsImpliedScopes() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.REFLOW))
        applier.apply(fakeCanvas, snapshot, diff)
        // REFLOW 应展开为 PAGE
        assertTrue(fakeCanvas.reflowTriggered)
    }

    @Test
    fun apply_scopesExecuteInOrder() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(
            setOf(
                InvalidationScope.PAGE_DELEGATE,
                InvalidationScope.PAGE,
            )
        )
        applier.apply(fakeCanvas, snapshot, diff)
        // 执行顺序应为 PAGE_DELEGATE(0) → PAGE(2)
        assertEquals(
            listOf("pageDelegate", "setPage"),
            fakeCanvas.executionOrder,
        )
    }

    @Test
    fun apply_emptyDiff_noOperations() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(emptySet())
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.executionOrder.isEmpty())
    }
}

// ── Test double ──

class FakeReaderCanvasView : RenderApplierTarget {
    var delegateRebuilt = false
    var reflowTriggered = false
    var applyCount = 0
    val executionOrder = mutableListOf<String>()

    override fun setPage(
        page: TextPage,
        next: TextPage?,
        prev: TextPage?,
        mode: PageRenderMode,
    ) {
        executionOrder.add("setPage")
    }

    override fun invalidateAllPages() {
        reflowTriggered = true
        executionOrder.add("reflow")
    }

    override fun rebuildPageDelegate() {
        delegateRebuilt = true
        executionOrder.add("pageDelegate")
    }

    override fun submitRenderTask() {
        // no-op in test
    }

    override fun applySnapshot(
        snapshot: Any,
        diff: Any,
        pageDelegate: com.shuli.reader.core.reader.engine.animation.PageDelegate?,
        chapterContent: CharSequence,
        chapterContents: Map<Int, CharSequence>,
    ) {
        applyCount++
    }
}
