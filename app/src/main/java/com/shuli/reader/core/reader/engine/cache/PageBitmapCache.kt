package com.shuli.reader.core.reader.engine.cache

import android.graphics.Paint
import com.shuli.reader.core.recorder.recordIfNeeded
import com.shuli.reader.core.reader.engine.PaintSnapshot
import com.shuli.reader.core.reader.engine.PageRenderContext
import com.shuli.reader.core.reader.engine.ReaderPageRenderer
import com.shuli.reader.core.reader.engine.RenderContext
import com.shuli.reader.core.reader.engine.RenderContextSnapshot
import com.shuli.reader.core.reader.engine.StatelessReaderPageRenderer
import com.shuli.reader.core.reader.engine.createPaintSnapshot
import com.shuli.reader.core.reader.engine.freeze
import com.shuli.reader.core.reader.model.TextPage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 页面录制与缓存管理。
 *
 * Phase 2a: recorder 从 TextPage 移到 PageRenderStateStore。
 * Phase 3: submitRenderTask 在主线程冻结 RenderContext 和 Paint 快照，
 *          后台线程只使用不可变数据 + StatelessReaderPageRenderer。
 *
 * PR-4: submitRenderTask() 接入 generation token，丢弃过期后台任务。
 */
class PageBitmapCache(
    private val pageRenderer: ReaderPageRenderer,
) {
    /** 后台预渲染线程，单线程，优先级略低于主线程 */
    private val renderThread: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ShuLi-PageRender").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    /**
     * 主线程同步录制页面。使用可变的 RenderContext 和 Paint（安全，因为主线程串行执行）。
     */
    fun doRecordPage(
        page: TextPage,
        renderState: PageRenderState,
        w: Int,
        h: Int,
        content: CharSequence,
        contentChapterIndex: Int,
        renderContext: RenderContext,
        backgroundPaint: Paint,
        textPaint: Paint,
        selectionPaint: Paint,
        renderStateStore: PageRenderStateStore,
    ): Boolean {
        if (page.chapterIndex != contentChapterIndex) {
            if (com.shuli.reader.BuildConfig.DEBUG) {
                android.util.Log.w(
                    "PageBitmapCache",
                    "skip record: page chapter=${page.chapterIndex} pi=${page.pageIndex} " +
                        "contentChapter=$contentChapterIndex",
                )
            }
            renderState.invalidateAll()
            return false
        }

        if (page.startCharOffset < 0 || page.endCharOffset > content.length) {
            if (com.shuli.reader.BuildConfig.DEBUG) {
                android.util.Log.w(
                    "PageBitmapCache",
                    "skip record: page chapter=${page.chapterIndex} pi=${page.pageIndex} " +
                        "range=[${page.startCharOffset},${page.endCharOffset}) contentLen=${content.length}",
                )
            }
            renderState.invalidateAll()
            return false
        }

        val shellDirty = renderState.shell.recordIfNeeded(w, h) {
            pageRenderer.renderShell(
                canvas = this,
                page = page,
                headerSlots = renderContext.headerSlots,
                footerSlots = renderContext.footerSlots,
                showProgress = renderContext.showProgress,
                headerAlpha = renderContext.headerAlpha,
                footerAlpha = renderContext.footerAlpha,
                batteryLevel = renderContext.batteryLevel,
                backgroundPaint = backgroundPaint,
                showHeaderLine = renderContext.showHeaderLine,
                showFooterLine = renderContext.showFooterLine,
            )
        }
        val contentDirty = renderState.content.recordIfNeeded(w, h) {
            val ctx = PageRenderContext(
                content = content,
                page = page,
                textPaint = textPaint,
                letterSpacingPx = textPaint.letterSpacing * textPaint.textSize,
                availableWidth = page.layout.body.width,
                renderStateStore = renderStateStore,
            )
            pageRenderer.renderContent(canvas = this, ctx = ctx)
        }
        val overlayDirty = renderState.overlay.recordIfNeeded(w, h) {
            pageRenderer.renderOverlay(
                canvas = this,
                page = page,
                selectedRange = renderContext.selectedRange,
                selectionPaint = selectionPaint,
                noteRanges = renderContext.noteRanges,
            )
        }
        return shellDirty || contentDirty || overlayDirty
    }

    /** 主线程同步录制单页（onDraw 兜底路径）。 */
    fun recordPage(
        page: TextPage,
        renderState: PageRenderState,
        width: Int,
        height: Int,
        content: CharSequence,
        contentChapterIndex: Int,
        renderContext: RenderContext,
        backgroundPaint: Paint,
        textPaint: Paint,
        selectionPaint: Paint,
        renderStateStore: PageRenderStateStore,
    ) {
        if (width <= 0 || height <= 0) return
        doRecordPage(page, renderState, width, height, content, contentChapterIndex, renderContext, backgroundPaint, textPaint, selectionPaint, renderStateStore)
    }

    /**
     * 提交后台预渲染任务。
     *
     * Phase 3: 在主线程冻结 RenderContext 和 Paint 快照，
     * 后台线程使用 StatelessReaderPageRenderer + 不可变数据，
     * 彻底消除跨线程共享可变状态。
     */
    fun submitRenderTask(
        width: Int,
        height: Int,
        currentPage: TextPage?,
        nextPage: TextPage?,
        prevPage: TextPage?,
        renderStateStore: PageRenderStateStore,
        chapterContents: Map<Int, CharSequence>,
        renderContext: RenderContext,
        backgroundPaint: Paint,
        textPaint: Paint,
        selectionPaint: Paint,
        headerPaint: Paint,
        footerPaint: Paint,
        progressPaint: Paint,
        postInvalidate: () -> Unit,
        generation: Long = -1L,
    ) {
        if (width <= 0 || height <= 0) return

        // ── 主线程：冻结所有数据 ──────────────────────────────
        val contextSnapshot = renderContext.freeze()
        val paintSnapshot = createPaintSnapshot(
            textPaint = textPaint,
            backgroundPaint = backgroundPaint,
            selectionPaint = selectionPaint,
            headerPaint = headerPaint,
            footerPaint = footerPaint,
            progressPaint = progressPaint,
        )
        val statelessRenderer = StatelessReaderPageRenderer(paintSnapshot)

        // 在主线程解析 PageRenderState（后台线程不访问 store）
        val tasks = listOfNotNull(currentPage, nextPage, prevPage).mapNotNull { page ->
            val content = chapterContents[page.chapterIndex] ?: return@mapNotNull null
            val key = PageKey(page.chapterIndex, page.pageIndex, page.startCharOffset, page.endCharOffset)
            val state = renderStateStore.getPageState(key)
            Triple(page, content, state)
        }

        // ── 后台线程：只使用不可变数据 ────────────────────────
        renderThread.execute {
            if (generation >= 0 && generation != contextSnapshot.generation) {
                return@execute
            }
            var dirty = false
            tasks.forEach { (page, content, state) ->
                if (doRecordPageStateless(
                        page, state, width, height, content, page.chapterIndex,
                        contextSnapshot, paintSnapshot, statelessRenderer, renderStateStore,
                    )
                ) {
                    dirty = true
                }
            }
            if (dirty) postInvalidate()
        }
    }

    /**
     * 后台线程录制页面。只使用不可变快照，不接触任何主线程可变状态。
     */
    private fun doRecordPageStateless(
        page: TextPage,
        renderState: PageRenderState,
        w: Int,
        h: Int,
        content: CharSequence,
        contentChapterIndex: Int,
        ctxSnapshot: RenderContextSnapshot,
        paintSnapshot: PaintSnapshot,
        renderer: StatelessReaderPageRenderer,
        renderStateStore: PageRenderStateStore,
    ): Boolean {
        if (page.chapterIndex != contentChapterIndex) {
            renderState.invalidateAll()
            return false
        }

        if (page.startCharOffset < 0 || page.endCharOffset > content.length) {
            renderState.invalidateAll()
            return false
        }

        val shellDirty = renderState.shell.recordIfNeeded(w, h) {
            renderer.renderShell(
                canvas = this,
                page = page,
                ctx = ctxSnapshot,
                backgroundPaint = paintSnapshot.background,
            )
        }
        val contentDirty = renderState.content.recordIfNeeded(w, h) {
            val ctx = PageRenderContext(
                content = content,
                page = page,
                textPaint = paintSnapshot.text,
                letterSpacingPx = paintSnapshot.text.letterSpacing * paintSnapshot.text.textSize,
                availableWidth = page.layout.body.width,
                renderStateStore = renderStateStore,
            )
            renderer.renderContent(canvas = this, ctx = ctx)
        }
        val overlayDirty = renderState.overlay.recordIfNeeded(w, h) {
            renderer.renderOverlay(
                canvas = this,
                page = page,
                selectedRange = ctxSnapshot.selectedRange,
                selectionPaint = paintSnapshot.selection,
                noteRanges = ctxSnapshot.noteRanges,
            )
        }
        return shellDirty || contentDirty || overlayDirty
    }
}
