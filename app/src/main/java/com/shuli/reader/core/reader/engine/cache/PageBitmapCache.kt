package com.shuli.reader.core.reader.engine.cache

import android.graphics.Canvas
import android.graphics.Paint
import com.shuli.reader.core.recorder.CanvasRecorder
import com.shuli.reader.core.recorder.recordIfNeeded
import com.shuli.reader.core.reader.engine.PageRenderContext
import com.shuli.reader.core.reader.engine.ReaderPageRenderer
import com.shuli.reader.core.reader.engine.RenderContext
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
        contentChapterIndex: Int,
        renderContext: RenderContext,
        backgroundPaint: Paint,
        textPaint: Paint,
        selectionPaint: Paint,
    ): Boolean {
        // page 与 content 必须来自同一章。仅靠字符区间无法识别 ch2[0] + ch1 content
        // 这种 offset 合法但语义错误的组合，会把下一章首页静默录成上一章首页。
        if (page.chapterIndex != contentChapterIndex) {
            if (com.shuli.reader.BuildConfig.DEBUG) {
                android.util.Log.w(
                    "PageBitmapCache",
                    "skip record: page chapter=${page.chapterIndex} pi=${page.pageIndex} " +
                        "contentChapter=$contentChapterIndex",
                )
            }
            page.invalidateAll()
            return false
        }

        // 跨章防御：page 的字符偏移必须落在 content 范围内。若 page 来自相邻章节而
        // content 是当前章节正文，则 endCharOffset 可能 > content.length，drawText 会 IOOB。
        // 这里不能 recycle：TextPage 会被章节缓存复用，recycle 后若没有立刻用正确 content
        // 复录，会让后续绘制进入资源生命周期竞态。保持 dirty，等正确章节 content 到位后重录。
        if (page.startCharOffset < 0 || page.endCharOffset > content.length) {
            if (com.shuli.reader.BuildConfig.DEBUG) {
                android.util.Log.w(
                    "PageBitmapCache",
                    "skip record: page chapter=${page.chapterIndex} pi=${page.pageIndex} " +
                        "range=[${page.startCharOffset},${page.endCharOffset}) contentLen=${content.length}",
                )
            }
            page.invalidateAll()
            return false
        }

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
                selectedRange = renderContext.selectedRange,
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
        contentChapterIndex: Int,
        renderContext: RenderContext,
        backgroundPaint: Paint,
        textPaint: Paint,
        selectionPaint: Paint,
    ) {
        if (width <= 0 || height <= 0) return
        doRecordPage(page, width, height, content, contentChapterIndex, renderContext, backgroundPaint, textPaint, selectionPaint)
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
        contentChapterIndex: Int,
        renderContext: RenderContext,
        backgroundPaint: Paint,
        textPaint: Paint,
        selectionPaint: Paint,
    ): Boolean {
        return doRecordPage(page, w, h, content, contentChapterIndex, renderContext, backgroundPaint, textPaint, selectionPaint)
    }

    /** 提交后台预渲染任务：录制 current/next/prev 三页，完成后触发重绘。 */
    fun submitRenderTask(
        width: Int,
        height: Int,
        currentPage: TextPage?,
        nextPage: TextPage?,
        prevPage: TextPage?,
        chapterContents: Map<Int, CharSequence>,
        renderContext: RenderContext,
        backgroundPaint: Paint,
        textPaint: Paint,
        selectionPaint: Paint,
        postInvalidate: () -> Unit,
    ) {
        if (width <= 0 || height <= 0) return
        renderThread.execute {
            var dirty = false
            listOfNotNull(currentPage, nextPage, prevPage).forEach { page ->
                val content = chapterContents[page.chapterIndex] ?: return@forEach
                if (recordPageOffMain(page, width, height, content, page.chapterIndex, renderContext, backgroundPaint, textPaint, selectionPaint)) {
                    dirty = true
                }
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
