package com.shuli.reader.core.reader.engine

import android.graphics.Canvas
import android.graphics.Paint
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.SlotResolution
import com.shuli.reader.core.reader.model.TextPage

/**
 * 无状态页面渲染器，后台线程专用。
 *
 * Phase 3: 所有 Paint 来自 [PaintSnapshot] 冻结副本，不持有任何可变状态。
 * 内部委托给 [ReaderPageRenderer]，但传入的 Paint 均为不可变副本，
 * 确保后台线程不接触主线程可变的 Paint 实例。
 *
 * 每次 submitRenderTask 创建新实例，不复用。
 */
class StatelessReaderPageRenderer(paints: PaintSnapshot) {
    private val delegate = ReaderPageRenderer(
        textPaint = paints.text,
        headerPaint = paints.header,
        footerPaint = paints.footer,
        progressPaint = paints.progress,
    )

    fun renderShell(
        canvas: Canvas,
        page: TextPage,
        ctx: RenderContextSnapshot,
        backgroundPaint: Paint,
    ) {
        delegate.renderShell(
            canvas = canvas,
            page = page,
            headerSlots = ctx.headerSlots,
            footerSlots = ctx.footerSlots,
            showProgress = ctx.showProgress,
            headerAlpha = ctx.headerAlpha,
            footerAlpha = ctx.footerAlpha,
            batteryLevel = ctx.batteryLevel,
            backgroundPaint = backgroundPaint,
            showHeaderLine = ctx.showHeaderLine,
            showFooterLine = ctx.showFooterLine,
        )
    }

    fun renderContent(canvas: Canvas, ctx: PageRenderContext) {
        delegate.renderContent(canvas = canvas, ctx = ctx)
    }

    fun renderOverlay(
        canvas: Canvas,
        page: TextPage,
        selectedRange: SelectionRange?,
        selectionPaint: Paint,
        noteRanges: List<Pair<SelectionRange, Int>>,
    ) {
        val notePaintMap = noteRanges.map { (range, colorInt) ->
            range to Paint().apply {
                this.color = colorInt
                alpha = 0x33
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        }
        delegate.renderOverlay(
            canvas = canvas,
            page = page,
            selectedRange = selectedRange,
            selectionPaint = selectionPaint,
            noteRanges = notePaintMap,
        )
    }
}
