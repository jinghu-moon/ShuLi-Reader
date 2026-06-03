package com.shuli.reader.core.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.shuli.reader.core.canvasrecorder.CanvasRecorderFactory
import com.shuli.reader.core.canvasrecorder.recordIfNeeded
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextLine
import com.shuli.reader.core.reader.model.TextPage

/**
 * 阅读器页面渲染器，高内聚绘制所有文本、选区及页眉页脚
 */
class ReaderPageRenderer(
    private val textPaint: Paint,
    private val headerPaint: Paint,
    private val footerPaint: Paint,
    private val progressPaint: Paint,
    private var textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
) {
    private var titleStyle: TitleStyleConfig = TitleStyleConfig()

    /** 标题画笔，基于 textPaint 派生，避免污染正文画笔 */
    private val titlePaint = Paint().apply {
        isAntiAlias = true
        typeface = textPaint.typeface
    }

    /** 电池绘制画笔（预分配，避免每帧 new Paint） */
    private val batteryStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val batteryFillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /** 页眉页脚分割线画笔 */
    private val dividerPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    /**
     * 更新对齐方式
     */
    fun setTextAlign(align: ReaderTextAlign) {
        textAlign = align
    }

    /**
     * 更新标题样式
     */
    fun setTitleStyle(style: TitleStyleConfig) {
        titleStyle = style
    }

    /**
     * 渲染页面（兼容旧接口）
     */
    fun render(
        canvas: Canvas,
        ctx: PageRenderContext,
        headerText: String,
        footerText: String,
        showProgress: Boolean,
        batteryLevel: Int = 100,
        ttsActiveRange: SelectionRange? = null,
        selectedRange: SelectionRange? = null,
        ttsHighlightPaint: Paint? = null,
        selectionPaint: Paint? = null,
        backgroundPaint: Paint? = null,
    ) {
        render(
            canvas = canvas,
            ctx = ctx,
            headerSlots = SlotResolution(left = headerText),
            footerSlots = SlotResolution(left = footerText),
            showProgress = showProgress,
            headerAlpha = 0.4f,
            footerAlpha = 0.4f,
            batteryLevel = batteryLevel,
            ttsActiveRange = ttsActiveRange,
            selectedRange = selectedRange,
            ttsHighlightPaint = ttsHighlightPaint,
            selectionPaint = selectionPaint,
            backgroundPaint = backgroundPaint,
        )
    }

    /**
     * 渲染壳层：背景、页眉、页脚、电池、进度条。
     * 排版参数变化时不需要重录。
     */
    fun renderShell(
        canvas: Canvas,
        page: TextPage,
        headerSlots: SlotResolution,
        footerSlots: SlotResolution,
        showProgress: Boolean,
        headerAlpha: Float = 0.4f,
        footerAlpha: Float = 0.4f,
        batteryLevel: Int = 100,
        backgroundPaint: Paint? = null,
        showHeaderLine: Boolean = false,
        showFooterLine: Boolean = false,
    ) {
        // 1. 绘制背景
        if (backgroundPaint != null) {
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), backgroundPaint)
        }

        val density = page.density

        // 2. 绘制页眉
        val headerBaseline = page.headerMarginTop + 24f * density * 0.6f
        drawHeaderFooter(canvas, headerSlots, headerPaint, headerAlpha, headerBaseline, page)

        // 2.5 绘制页眉分割线
        if (showHeaderLine) {
            val lineY = headerBaseline + 4f * density
            dividerPaint.color = headerPaint.color
            dividerPaint.alpha = (headerAlpha * 255 * 0.5f).toInt()
            canvas.drawLine(page.marginHorizontal, lineY, canvas.width - page.marginHorizontal, lineY, dividerPaint)
        }

        // 3. 绘制页脚
        val footerBaseline = canvas.height - page.footerMarginBottom - 24f * density * 0.4f
        drawHeaderFooter(canvas, footerSlots, footerPaint, footerAlpha, footerBaseline, page)

        // 3.5 绘制页脚分割线
        if (showFooterLine) {
            val lineY = footerBaseline - footerPaint.textSize * 0.6f
            dividerPaint.color = footerPaint.color
            dividerPaint.alpha = (footerAlpha * 255 * 0.5f).toInt()
            canvas.drawLine(page.marginHorizontal, lineY, canvas.width - page.marginHorizontal, lineY, dividerPaint)
        }

        // 4. 绘制电池
        drawBattery(canvas, page.marginHorizontal, footerBaseline, batteryLevel, density)

        // 5. 绘制进度条
        if (showProgress) {
            val progress = if (page.chapterContentLength > 0) {
                (page.startCharOffset.toFloat() / page.chapterContentLength).coerceIn(0f, 1f)
            } else {
                0f
            }
            val progressWidth = canvas.width * progress
            val progressRect = RectF(0f, canvas.height - 3f * density, progressWidth, canvas.height.toFloat())
            canvas.drawRect(progressRect, progressPaint)
        }
    }

    /**
     * 渲染内容：TTS/选区高亮、正文文本、章节标题。
     * 排版参数变化时需要重录。
     *
     * @param ctx 页面渲染上下文，提供 content + page + paint + metrics
     */
    fun renderContent(
        canvas: Canvas,
        ctx: PageRenderContext,
        ttsActiveRange: SelectionRange? = null,
        selectedRange: SelectionRange? = null,
        ttsHighlightPaint: Paint? = null,
        selectionPaint: Paint? = null,
    ) {
        val page = ctx.page
        val density = page.density

        // 1. 绘制高亮背景（TTS高亮与用户选区）
        page.lines.forEach { line ->
            val startX = page.marginHorizontal + line.startXOffset
            val textWidth = line.measuredWidth
            val top = line.top
            val bottom = line.bottom
            val rect = RectF(startX - 6f, top, startX + textWidth + 6f, bottom)

            if (intersects(ttsActiveRange, line.startCharOffset, line.endCharOffset) && ttsHighlightPaint != null) {
                canvas.drawRoundRect(rect, 6f, 6f, ttsHighlightPaint)
            }
            if (intersects(selectedRange, line.startCharOffset, line.endCharOffset) && selectionPaint != null) {
                canvas.drawRoundRect(rect, 6f, 6f, selectionPaint)
            }
        }

        // 2. 绘制正文文本（per-line CanvasRecorder 优化）
        for (line in page.lines) {
            drawLineWithRecorder(canvas, line, ctx)
        }

        // 3. 绘制章节标题（仅首页）
        drawChapterTitle(canvas, page, density)
    }

    /**
     * 渲染页面（兼容旧接口，同时绘制壳层和内容）
     */
    fun render(
        canvas: Canvas,
        ctx: PageRenderContext,
        headerSlots: SlotResolution,
        footerSlots: SlotResolution,
        showProgress: Boolean,
        headerAlpha: Float = 0.4f,
        footerAlpha: Float = 0.4f,
        batteryLevel: Int = 100,
        ttsActiveRange: SelectionRange? = null,
        selectedRange: SelectionRange? = null,
        ttsHighlightPaint: Paint? = null,
        selectionPaint: Paint? = null,
        backgroundPaint: Paint? = null,
    ) {
        renderShell(canvas, ctx.page, headerSlots, footerSlots, showProgress, headerAlpha, footerAlpha, batteryLevel, backgroundPaint)
        renderContent(canvas, ctx, ttsActiveRange, selectedRange, ttsHighlightPaint, selectionPaint)
    }

    /**
     * 绘制页眉或页脚（多槽位）
     */
    private fun drawHeaderFooter(
        canvas: Canvas,
        slots: SlotResolution,
        paint: Paint,
        alpha: Float,
        baseline: Float,
        page: TextPage,
    ) {
        if (slots.isEmpty()) return

        val oldAlpha = paint.alpha
        paint.alpha = (alpha * 255).toInt()

        val canvasWidth = canvas.width.toFloat()
        val marginH = page.marginHorizontal

        // 左槽位
        if (slots.left.isNotEmpty()) {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(slots.left, marginH, baseline, paint)
        }

        // 中槽位
        if (slots.center.isNotEmpty()) {
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(slots.center, canvasWidth / 2f, baseline, paint)
        }

        // 右槽位
        if (slots.right.isNotEmpty()) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(slots.right, canvasWidth - marginH, baseline, paint)
        }

        paint.alpha = oldAlpha
        paint.textAlign = Paint.Align.LEFT // 重置
    }

    private fun SlotResolution.isEmpty(): Boolean {
        return left.isEmpty() && center.isEmpty() && right.isEmpty()
    }

    /**
     * 绘制章节标题（仅首页 pageIndex == 0 且 align != HIDDEN）
     */
    private fun drawChapterTitle(canvas: Canvas, page: TextPage, density: Float) {
        if (page.pageIndex != 0) return
        if (page.chapterTitle.isBlank()) return
        if (titleStyle.align == TitleAlign.HIDDEN) return

        val titleTextSize = textPaint.textSize + titleStyle.sizeOffsetSp * density
        titlePaint.textSize = titleTextSize
        titlePaint.color = textPaint.color
        titlePaint.isFakeBoldText = true
        titlePaint.typeface = textPaint.typeface

        val canvasWidth = canvas.width.toFloat()
        val marginH = page.marginHorizontal
        val availableWidth = (canvasWidth - marginH * 2).toInt().coerceAtLeast(1)

        val layoutAlign = when (titleStyle.align) {
            TitleAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TitleAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            TitleAlign.HIDDEN -> return
        }

        // 用 StaticLayout 支持多行自动换行
        val textPaint = TextPaint(titlePaint)
        val layout = StaticLayout.Builder.obtain(
            page.chapterTitle, 0, page.chapterTitle.length, textPaint, availableWidth
        ).setAlignment(layoutAlign).setIncludePad(false).build()

        // 垂直定位：标题底部 = topContentY - marginBottom，向上偏移整个 layout 高度
        val marginBottom = titleStyle.marginBottomDp * density
        val titleTop = page.topContentY - marginBottom - layout.height

        canvas.save()
        canvas.translate(marginH, titleTop)
        layout.draw(canvas)
        canvas.restore()
    }

    /**
     * 使用 per-line CanvasRecorder 绘制单行文本
     * 选区/TTS 高亮变化时仅重画受影响的行，而非整页
     */
    private fun drawLineWithRecorder(canvas: Canvas, line: TextLine, ctx: PageRenderContext) {
        val recorder = line.canvasRecorder
            ?: CanvasRecorderFactory.create().also { line.canvasRecorder = it }

        val lineHeight = (line.bottom - line.top).toInt()
        val startX = ctx.page.marginHorizontal + line.startXOffset

        recorder.recordIfNeeded(canvas.width, lineHeight) {
            val relativeBaseline = line.baseline - line.top

            // 判断是否需要两端对齐：JUSTIFY 模式且非段落末行
            val shouldJustify = textAlign == ReaderTextAlign.JUSTIFY && !line.isParagraphEnd

            if (shouldJustify && line.charWidths != null) {
                drawTextJustified(line, startX, relativeBaseline, ctx)
            } else {
                drawText(ctx.content, line.startCharOffset, line.endCharOffset, startX, relativeBaseline, ctx.textPaint)
            }
        }

        // 平移到行顶部绘制
        canvas.save()
        canvas.translate(0f, line.top)
        recorder.draw(canvas)
        canvas.restore()
    }

    /**
     * 两端对齐绘制：使用预计算的 charWidths 逐字符定位，零 String 分配
     */
    private fun Canvas.drawTextJustified(line: TextLine, x: Float, y: Float, ctx: PageRenderContext) {
        val extraSpace = ctx.availableWidth - line.measuredWidth

        if (extraSpace <= 0f) {
            drawText(ctx.content, line.startCharOffset, line.endCharOffset, x, y, ctx.textPaint)
            return
        }

        val charCount = line.endCharOffset - line.startCharOffset
        val justifySpacing = if (charCount > 1) extraSpace / (charCount - 1) else 0f
        var currentX = x

        for (i in 0 until charCount) {
            drawText(ctx.content, line.startCharOffset + i, line.startCharOffset + i + 1, currentX, y, ctx.textPaint)
            if (i < charCount - 1) {
                currentX += line.charWidths!![i] + ctx.letterSpacingPx + justifySpacing
            }
        }
    }

    private fun intersects(range: SelectionRange?, start: Int, end: Int): Boolean {
        if (range == null) return false
        return range.startPos < end && range.endPos > start
    }

    private fun drawBattery(canvas: Canvas, marginHorizontal: Float, footerBaseline: Float, batteryLevel: Int, density: Float) {
        val capWidth = 2f * density
        val capHeight = 4f * density
        val batWidth = 22f * density
        val batHeight = 11f * density

        val batteryRight = canvas.width - marginHorizontal
        val batteryLeft = batteryRight - batWidth
        val batteryTop = footerBaseline - footerPaint.textSize * 0.8f
        val batteryBottom = batteryTop + batHeight

        // 更新预分配画笔属性
        batteryStrokePaint.color = footerPaint.color
        batteryStrokePaint.alpha = 102
        batteryStrokePaint.strokeWidth = 1f * density
        batteryFillPaint.color = footerPaint.color
        batteryFillPaint.alpha = 102

        // 1. 绘制电池外框
        val batteryRect = RectF(batteryLeft, batteryTop, batteryRight, batteryBottom)
        canvas.drawRoundRect(batteryRect, 1.5f * density, 1.5f * density, batteryStrokePaint)

        // 2. 绘制电池头 (Cap)
        val capLeft = batteryRight
        val capRight = capLeft + capWidth
        val capTop = batteryTop + (batHeight - capHeight) / 2f
        val capBottom = capTop + capHeight
        canvas.drawRect(RectF(capLeft, capTop, capRight, capBottom), batteryFillPaint)

        // 3. 绘制电池内部填充
        val innerPadding = 1.5f * density
        val maxFillWidth = batWidth - innerPadding * 2
        val fillWidth = maxFillWidth * (batteryLevel.coerceIn(0, 100) / 100f)
        val fillRect = RectF(
            batteryLeft + innerPadding,
            batteryTop + innerPadding,
            batteryLeft + innerPadding + fillWidth,
            batteryBottom - innerPadding
        )
        canvas.drawRect(fillRect, batteryFillPaint)

        // 4. 绘制电量百分比
        val percentText = "$batteryLevel%"
        val percentTextWidth = footerPaint.measureText(percentText)
        val percentX = batteryLeft - 4f * density - percentTextWidth
        canvas.drawText(percentText, percentX, footerBaseline, footerPaint)
    }
}