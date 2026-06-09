package com.shuli.reader.core.reader.canvas

import android.graphics.Canvas
import android.graphics.Paint
import com.shuli.reader.core.canvasrecorder.CanvasRecorder
import com.shuli.reader.core.canvasrecorder.recordIfNeeded
import com.shuli.reader.core.reader.PageRenderContext
import com.shuli.reader.core.reader.ReaderPageRenderer
import com.shuli.reader.core.reader.RenderContext
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextPage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 页面录制与缓存管理。
 *
 * 封装 CanvasRecorder 的录制策略：主线程兜底录制、后台预渲染三页、
 * 壳层/内容层分离录制。从 ReaderCanvasView 拆出，独立演进缓存策略。
 */
class PageBitmapCache(
    private val pageRenderer: ReaderPageRenderer,
) {
    /** 后台预渲染线程，单线程，优先级略低于主线程 */
    private val renderThread: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ShuLi-PageRender").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    /**
     * 录制页面的公共实现。壳层和内容分别录制，返回 true 表示实际产生了录制。
     */
    fun doRecordPage(
        page: TextPage,
        w: Int,
        h: Int,
        content: CharSequence,
        renderContext: RenderContext,
        backgroundPaint: Paint,
        textPaint: Paint,
        ttsHighlightPaint: Paint,
        selectionPaint: Paint,
    ): Boolean {
        val shellDirty = page.shellRecorder.recordIfNeeded(w, h) {
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
        val contentDirty = page.canvasRecorder.recordIfNeeded(w, h) {
            val ctx = PageRenderContext(
                content = content,
                page = page,
                textPaint = textPaint,
                letterSpacingPx = textPaint.letterSpacing * textPaint.textSize,
                availableWidth = page.pageSize.width - page.marginHorizontal * 2,
            )
            pageRenderer.renderContent(canvas = this, ctx = ctx)
        }
        val overlayDirty = page.overlayRecorder.recordIfNeeded(w, h) {
            pageRenderer.renderOverlay(
                canvas = this,
                page = page,
                ttsActiveRange = renderContext.ttsActiveRange,
                selectedRange = renderContext.selectedRange,
                ttsHighlightPaint = ttsHighlightPaint,
                selectionPaint = selectionPaint,
                noteRanges = renderContext.noteRanges,
            )
        }
        return shellDirty || contentDirty || overlayDirty
    }

    /** 主线程同步录制单页（兜底用）。 */
    fun recordPage(
        page: TextPage,
        width: Int,
        height: Int,
        content: CharSequence,
        renderContext: RenderContext,
        backgroundPaint: Paint,
        textPaint: Paint,
        ttsHighlightPaint: Paint,
        selectionPaint: Paint,
    ) {
        if (width <= 0 || height <= 0) return
        doRecordPage(page, width, height, content, renderContext, backgroundPaint, textPaint, ttsHighlightPaint, selectionPaint)
    }

    /**
     * 后台线程录制页面。CanvasRecorderLocked 内部 ReentrantLock 保证线程安全。
     * 返回 true 表示实际产生了录制（即 recorder 之前是脏的）。
     */
    fun recordPageOffMain(
        page: TextPage,
        w: Int,
        h: Int,
        content: CharSequence,
        renderContext: RenderContext,
        backgroundPaint: Paint,
        textPaint: Paint,
        ttsHighlightPaint: Paint,
        selectionPaint: Paint,
    ): Boolean {
        return doRecordPage(page, w, h, content, renderContext, backgroundPaint, textPaint, ttsHighlightPaint, selectionPaint)
    }

    /** 提交后台预渲染任务：录制 current/next/prev 三页，完成后触发重绘。 */
    fun submitRenderTask(
        width: Int,
        height: Int,
        currentPage: TextPage?,
        nextPage: TextPage?,
        prevPage: TextPage?,
        content: CharSequence,
        renderContext: RenderContext,
        backgroundPaint: Paint,
        textPaint: Paint,
        ttsHighlightPaint: Paint,
        selectionPaint: Paint,
        postInvalidate: () -> Unit,
    ) {
        if (width <= 0 || height <= 0) return
        renderThread.execute {
            var dirty = false
            currentPage?.let {
                if (recordPageOffMain(it, width, height, content, renderContext, backgroundPaint, textPaint, ttsHighlightPaint, selectionPaint)) dirty = true
            }
            nextPage?.let {
                if (recordPageOffMain(it, width, height, content, renderContext, backgroundPaint, textPaint, ttsHighlightPaint, selectionPaint)) dirty = true
            }
            prevPage?.let {
                if (recordPageOffMain(it, width, height, content, renderContext, backgroundPaint, textPaint, ttsHighlightPaint, selectionPaint)) dirty = true
            }
            if (dirty) postInvalidate()
        }
    }

    /** 使所有页面 recorder 失效（字体/主题/尺寸等全局变化时使用） */
    fun invalidateAllRecorders(currentPage: TextPage?, nextPage: TextPage?, prevPage: TextPage?) {
        currentPage?.invalidateAll()
        nextPage?.invalidateAll()
        prevPage?.invalidateAll()
    }
}
