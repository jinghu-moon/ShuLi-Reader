package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.reader.RenderApplierTarget
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
    fun apply_contentScope_invalidatesContent() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.CONTENT))
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.contentInvalidated)
        assertFalse("CONTENT 不应触发 SHELL", fakeCanvas.shellInvalidated)
    }

    @Test
    fun apply_shellScope_invalidatesShell() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.SHELL))
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.shellInvalidated)
        assertFalse(fakeCanvas.contentInvalidated)
    }

    @Test
    fun apply_overlayScope_invalidatesOverlay() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(setOf(InvalidationScope.OVERLAY))
        applier.apply(fakeCanvas, snapshot, diff)
        assertTrue(fakeCanvas.overlayInvalidated)
        assertFalse(fakeCanvas.contentInvalidated)
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
        // REFLOW 应展开为 PAGE + CONTENT + SHELL + OVERLAY
        assertTrue(fakeCanvas.reflowTriggered)
        assertTrue(fakeCanvas.contentInvalidated)
        assertTrue(fakeCanvas.shellInvalidated)
        assertTrue(fakeCanvas.overlayInvalidated)
    }

    @Test
    fun apply_scopesExecuteInOrder() {
        val snapshot = createDefaultSnapshot()
        val diff = ReaderRenderDiff(
            setOf(
                InvalidationScope.OVERLAY,
                InvalidationScope.PAGE_DELEGATE,
                InvalidationScope.CONTENT,
            )
        )
        applier.apply(fakeCanvas, snapshot, diff)
        // 执行顺序应为 PAGE_DELEGATE(0) → CONTENT(3) → OVERLAY(5)
        assertEquals(
            listOf("pageDelegate", "content", "overlay"),
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
    var contentInvalidated = false
    var shellInvalidated = false
    var overlayInvalidated = false
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

    override fun invalidateContentOnly() {
        contentInvalidated = true
        executionOrder.add("content")
    }

    override fun invalidateShellOnly() {
        shellInvalidated = true
        executionOrder.add("shell")
    }

    override fun invalidateOverlayOnly() {
        overlayInvalidated = true
        executionOrder.add("overlay")
    }

    override fun invalidateAllPages() {
        reflowTriggered = true
        contentInvalidated = true
        shellInvalidated = true
        overlayInvalidated = true
        executionOrder.add("reflow")
    }

    override fun rebuildPageDelegate() {
        delegateRebuilt = true
        executionOrder.add("pageDelegate")
    }

    override fun submitRenderTask() {
        // no-op in test
    }

    override fun applySnapshot(snapshot: Any, diff: Any, pageDelegate: com.shuli.reader.core.reader.animation.PageDelegate?) {
        applyCount++
    }
}
