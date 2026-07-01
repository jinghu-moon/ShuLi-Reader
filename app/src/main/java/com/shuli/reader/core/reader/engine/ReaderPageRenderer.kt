package com.shuli.reader.core.reader.engine

import kotlin.math.roundToInt
import com.shuli.reader.core.reader.model.BoxBounds
import com.shuli.reader.core.reader.model.PageLayout
import com.shuli.reader.core.reader.model.SlotContent
import com.shuli.reader.core.reader.model.SlotResolution
import com.shuli.reader.core.reader.model.TitleAlign
import com.shuli.reader.core.reader.model.TitleStyleConfig
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.shuli.reader.core.reader.engine.cache.LineKey
import com.shuli.reader.core.reader.engine.cache.PageKey
import com.shuli.reader.core.recorder.recordIfNeeded
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.reader.engine.selection.CanvasTextSelection
import com.shuli.reader.core.reader.engine.selection.SelectionVisualStyle
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

    /** 选区绘制复用对象 (Phase 1 低分配优化) */
    private val highlightRects = ArrayList<RectF>(50)
    private val unifiedPath = Path()
    private val tempPath = Path()
    private val noteHighlightRect = RectF()
    private val findMatchRect = RectF()
    private val expandedSelectionRect = RectF()
    private val selectionHandlePaint = Paint().apply {
        isAntiAlias = true
        color = SelectionVisualStyle.HANDLE_COLOR
        style = Paint.Style.FILL
    }
    private val selectionHandleStemPaint = Paint().apply {
        isAntiAlias = true
        color = SelectionVisualStyle.HANDLE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = SelectionVisualStyle.HANDLE_STEM_WIDTH
        strokeCap = Paint.Cap.ROUND
    }

    /** 选区几何缓存 (Phase 2) */
    private val selectionGeometryCache = com.shuli.reader.core.reader.engine.selection.SelectionGeometryCache()
    var layoutVersion: Int = 0
    var styleVersion: Int = 0

    /**
     * 清理资源
     */
    fun clearCache() {
        selectionGeometryCache.clear()
    }

    /**
     * 更新对齐方式
     */
    fun setTextAlign(align: ReaderTextAlign) {
        if (textAlign == align) return
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
        selectedRange: SelectionRange? = null,
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
            selectedRange = selectedRange,
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
        val layout = page.layout

        // 1. 绘制背景
        if (backgroundPaint != null) {
            canvas.drawRect(0f, 0f, layout.pageWidth, layout.pageHeight, backgroundPaint)
        }

        val density = page.density

        // 2. 绘制页眉
        layout.header?.let { box ->
            val headerBaseline = box.top + box.height * 0.6f
            drawHeaderFooter(canvas, headerSlots, headerPaint, headerAlpha, headerBaseline, box, batteryLevel, density)

            // 2.5 绘制页眉分割线
            if (showHeaderLine) {
                val lineY = (headerBaseline + 4f * density).roundToInt().toFloat()
                dividerPaint.color = headerPaint.color
                dividerPaint.alpha = (headerAlpha * 255 * 0.5f).toInt()
                canvas.drawLine(box.left, lineY, box.right, lineY, dividerPaint)
            }
        }

        // 3. 绘制页脚
        layout.footer?.let { box ->
            val footerBaseline = box.bottom - box.height * 0.4f
            drawHeaderFooter(canvas, footerSlots, footerPaint, footerAlpha, footerBaseline, box, batteryLevel, density)

            // 3.5 绘制页脚分割线
            if (showFooterLine) {
                val lineY = (footerBaseline - footerPaint.textSize * 0.6f).roundToInt().toFloat()
                dividerPaint.color = footerPaint.color
                dividerPaint.alpha = (footerAlpha * 255 * 0.5f).toInt()
                canvas.drawLine(box.left, lineY, box.right, lineY, dividerPaint)
            }
        }

        // 5. 绘制进度条
        if (showProgress) {
            val progress = if (page.chapterContentLength > 0) {
                (page.startCharOffset.toFloat() / page.chapterContentLength).coerceIn(0f, 1f)
            } else {
                0f
            }
            val progressWidth = layout.pageWidth * progress
            canvas.drawRect(0f, layout.pageHeight - 3f * density, progressWidth, layout.pageHeight, progressPaint)
        }
    }

    /**
     * 渲染内容：正文文本 + 章节标题。
     *
     * 排版参数变化时需要重录。
     *
     * @param ctx 页面渲染上下文，提供 content + page + paint + metrics
     */
    fun renderContent(
        canvas: Canvas,
        ctx: PageRenderContext,
    ) {
        val page = ctx.page
        val density = page.density

        // 1. 绘制正文文本（per-line CanvasRecorder 优化）
        for ((lineIndex, line) in page.lines.withIndex()) {
            drawLineWithRecorder(canvas, line, lineIndex, ctx)
        }

        // 2. 绘制章节标题（仅首页）
        page.layout.title?.let { titleBox ->
            page.titleLayout?.let { titleLayout ->
                drawChapterTitle(canvas, titleLayout, titleBox, page.density)
            }
        }
    }

    /**
     * 渲染覆盖层：笔记高亮、选区高亮。
     *
     * 独立录制在 overlay layer 中（由 PageRenderStateStore 管理），
     * 选区变化时仅 overlay 失效，正文不重录（§10 分层 recorder）。
     */
    fun renderOverlay(
        canvas: Canvas,
        page: TextPage,
        selectedRange: SelectionRange? = null,
        selectionPaint: Paint? = null,
        textSelection: CanvasTextSelection? = null,
        noteRanges: List<Pair<SelectionRange, Paint>> = emptyList(),
        findMatches: List<SelectionRange> = emptyList(),
        currentFindMatch: SelectionRange? = null,
        findMatchPaint: Paint? = null,
        currentFindMatchPaint: Paint? = null,
    ) {
        // 1. 笔记高亮背景（彩色半透明，在选区高亮之下）
        if (noteRanges.isNotEmpty()) {
            val linesSize = page.lines.size
            for (lineIndex in 0 until linesSize) {
                val line = page.lines[lineIndex]
                val startX = page.layout.body.left + line.startXOffset
                val textWidth = line.measuredWidth
                val top = line.top
                val bottom = line.bottom
                noteHighlightRect.set(startX - 6f, top, startX + textWidth + 6f, bottom)
                for ((range, paint) in noteRanges) {
                    if (intersects(range, line.startCharOffset, line.endCharOffset)) {
                        canvas.drawRoundRect(noteHighlightRect, 6f, 6f, paint)
                    }
                }
            }
        }

        // 2. 选区高亮背景（字符级精确范围）
        if (selectedRange != null && selectionPaint != null) {
            val cacheKey = com.shuli.reader.core.reader.engine.selection.SelectionGeometryCacheKey.create(
                page, selectedRange, layoutVersion, styleVersion
            )
            var geometry = selectionGeometryCache.get(cacheKey)
            
            if (geometry == null) {
                geometry = com.shuli.reader.core.reader.engine.selection.SelectionGeometry()
                val linesSize = page.lines.size
                for (lineIndex in 0 until linesSize) {
                    val line = page.lines[lineIndex]
                    if (intersects(selectedRange, line.startCharOffset, line.endCharOffset)) {
                        val bodyLeft = page.layout.body.left
                        val lineStart = line.startCharOffset
                        val lineEnd = line.endCharOffset
                        val selStart = maxOf(selectedRange.startPos, lineStart)
                        val selEnd = minOf(selectedRange.endPos, lineEnd)
                        val charWidths = line.charWidths
                        var selStartX = bodyLeft + line.startXOffset
                        var selEndX = selStartX
                        if (charWidths != null && charWidths.size == (lineEnd - lineStart)) {
                            for (i in 0 until (selStart - lineStart)) { selStartX += charWidths[i] }
                            selEndX = selStartX
                            for (i in (selStart - lineStart) until (selEnd - lineStart)) { selEndX += charWidths[i] }
                        } else {
                            selStartX = bodyLeft + line.startXOffset
                            selEndX = selStartX + line.measuredWidth
                        }
                        val rect = RectF(
                            selStartX - SelectionVisualStyle.HIGHLIGHT_HORIZONTAL_PADDING,
                            line.top,
                            selEndX + SelectionVisualStyle.HIGHLIGHT_HORIZONTAL_PADDING,
                            line.bottom,
                        )
                        geometry.highlightRects.add(rect)
                    }
                }
                
                val radius = SelectionVisualStyle.HIGHLIGHT_CORNER_RADIUS
                if (geometry.highlightRects.size > 1 && android.os.Build.VERSION.SDK_INT >= 21) {
                    try {
                        val verticalGrow = 1f * page.density // 纵向延伸1dp以消除行间隙
                        geometry.unifiedPath.reset()
                        for (index in 0 until geometry.highlightRects.size) {
                            val rect = geometry.highlightRects[index]
                            // 稍微放大上下边界，使相邻行有重叠区域从而能够完美 UNION
                            expandedSelectionRect.set(
                                rect.left, 
                                rect.top - verticalGrow, 
                                rect.right, 
                                rect.bottom + verticalGrow
                            )
                            tempPath.reset()
                            tempPath.addRoundRect(expandedSelectionRect, radius, radius, Path.Direction.CW)
                            
                            if (index == 0) {
                                geometry.unifiedPath.addPath(tempPath)
                            } else {
                                val success = geometry.unifiedPath.op(tempPath, Path.Op.UNION)
                                if (!success) {
                                    throw IllegalStateException("Path.op UNION failed")
                                }
                            }
                        }
                        geometry.isUnifiedPathValid = true
                    } catch (e: Exception) {
                        geometry.fallbackToRects = true
                    }
                } else {
                    geometry.fallbackToRects = true
                }
                selectionGeometryCache.put(cacheKey, geometry)
            }
            
            val radius = SelectionVisualStyle.HIGHLIGHT_CORNER_RADIUS
            if (geometry.fallbackToRects) {
                val rects = geometry.highlightRects
                for (i in 0 until rects.size) {
                    canvas.drawRoundRect(rects[i], radius, radius, selectionPaint)
                }
            } else if (geometry.isUnifiedPathValid) {
                canvas.drawPath(geometry.unifiedPath, selectionPaint)
            }
        }

        // 3. 查找匹配高亮
        if (findMatches.isNotEmpty() && findMatchPaint != null) {
            val linesSize = page.lines.size
            for (lineIndex in 0 until linesSize) {
                val line = page.lines[lineIndex]
                for (match in findMatches) {
                    if (intersects(match, line.startCharOffset, line.endCharOffset)) {
                        drawFindMatchHighlight(canvas, page, line, match, findMatchPaint)
                    }
                }
            }
        }
        // 当前匹配高亮（更醒目）
        if (currentFindMatch != null && currentFindMatchPaint != null) {
            val linesSize = page.lines.size
            for (lineIndex in 0 until linesSize) {
                val line = page.lines[lineIndex]
                if (intersects(currentFindMatch, line.startCharOffset, line.endCharOffset)) {
                    drawFindMatchHighlight(canvas, page, line, currentFindMatch, currentFindMatchPaint)
                }
            }
        }

        // 4. 绘制选区把手（如果有选区）
        if (selectedRange != null && selectionPaint != null && textSelection != null) {
            val viewWidth = page.layout.pageWidth
            val handleInfos = textSelection.getHandleRects(page, viewWidth)
            if (handleInfos != null) {
                val activeAnchor = textSelection.activeAnchor
                for (info in handleInfos) {
                    if (info.anchorId != activeAnchor) {
                        drawSelectionHandle(canvas, info.rect, selectionPaint, info.isStart)
                    }
                }
                for (info in handleInfos) {
                    if (info.anchorId == activeAnchor) {
                        drawSelectionHandle(canvas, info.rect, selectionPaint, info.isStart)
                    }
                }
            }
        }
    }

    /**
     * 绘制选区把手
     */
    private fun drawSelectionHandle(canvas: Canvas, rect: RectF, paint: Paint, isStart: Boolean) {
        val centerX = rect.centerX()
        val dotRadius = SelectionVisualStyle.HANDLE_DOT_RADIUS

        val stemStartY = if (isStart) rect.top + dotRadius else rect.top
        val stemEndY = if (isStart) rect.bottom else rect.bottom - dotRadius
        val dotCenterY = if (isStart) rect.top + dotRadius else rect.bottom - dotRadius
        canvas.drawLine(centerX, stemStartY, centerX, stemEndY, selectionHandleStemPaint)
        canvas.drawCircle(centerX, dotCenterY, dotRadius, selectionHandlePaint)
    }

    /**
     * 绘制查找匹配高亮
     */
    private fun drawFindMatchHighlight(
        canvas: Canvas,
        page: TextPage,
        line: TextLine,
        range: SelectionRange,
        paint: Paint,
    ) {
        val bodyLeft = page.layout.body.left
        val lineStart = line.startCharOffset
        val lineEnd = line.endCharOffset
        val matchStart = maxOf(range.startPos, lineStart)
        val matchEnd = minOf(range.endPos, lineEnd)

        if (matchStart >= matchEnd) return

        val charWidths = line.charWidths
        var startX = bodyLeft + line.startXOffset
        var endX = startX

        if (charWidths != null && charWidths.size == (lineEnd - lineStart)) {
            for (i in 0 until (matchStart - lineStart)) { startX += charWidths[i] }
            endX = startX
            for (i in (matchStart - lineStart) until (matchEnd - lineStart)) { endX += charWidths[i] }
        } else {
            startX = bodyLeft + line.startXOffset
            endX = startX + line.measuredWidth
        }

        findMatchRect.set(startX - 1f, line.top, endX + 1f, line.bottom)
        canvas.drawRoundRect(findMatchRect, 3f, 3f, paint)
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
        selectedRange: SelectionRange? = null,
        selectionPaint: Paint? = null,
        backgroundPaint: Paint? = null,
    ) {
        renderShell(canvas, ctx.page, headerSlots, footerSlots, showProgress, headerAlpha, footerAlpha, batteryLevel, backgroundPaint)
        renderContent(canvas, ctx)
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
        box: BoxBounds,
        batteryLevel: Int,
        density: Float,
    ) {
        if (slots.isEmpty()) return

        val oldAlpha = paint.alpha
        paint.alpha = (alpha * 255).toInt()

        fun drawSlot(text: String, content: SlotContent, x: Float, align: Paint.Align) {
            if (text.isEmpty()) return
            if (content == SlotContent.BATTERY) {
                drawBatteryAt(canvas, x, baseline, align, batteryLevel, density, paint)
            } else {
                paint.textAlign = align
                canvas.drawText(text, x, baseline, paint)
            }
        }

        drawSlot(slots.left, slots.leftContent, box.left, Paint.Align.LEFT)
        drawSlot(slots.center, slots.centerContent, (box.left + box.right) / 2f, Paint.Align.CENTER)
        drawSlot(slots.right, slots.rightContent, box.right, Paint.Align.RIGHT)

        paint.alpha = oldAlpha
        paint.textAlign = Paint.Align.LEFT // 重置
    }

    private fun SlotResolution.isEmpty(): Boolean {
        return left.isEmpty() && center.isEmpty() && right.isEmpty()
    }

    /**
     * 绘制章节标题（仅首页 pageIndex == 0 且 align != HIDDEN）
     */
    private fun drawChapterTitle(canvas: Canvas, titleLayout: StaticLayout, titleBox: BoxBounds, density: Float) {
        val titleTop = titleBox.top + titleStyle.marginTopDp * density

        canvas.save()
        canvas.translate(titleBox.left, titleTop)
        titleLayout.draw(canvas)
        canvas.restore()
    }

    /**
     * 使用 per-line CanvasRecorder 绘制单行文本
     * 选区高亮变化时仅重画受影响的行，而非整页
     */
    private fun drawLineWithRecorder(canvas: Canvas, line: TextLine, lineIndex: Int, ctx: PageRenderContext) {
        val pageKey = PageKey(ctx.page.chapterIndex, ctx.page.pageIndex, ctx.page.startCharOffset, ctx.page.endCharOffset)
        val lineKey = LineKey(pageKey, lineIndex)
        val recorder = ctx.renderStateStore.getLineRecorder(lineKey)

        val lineHeight = (line.bottom - line.top).toInt()
        val startX = ctx.page.layout.body.left + line.startXOffset

        recorder.recordIfNeeded(canvas.width, lineHeight) {
            val relativeBaseline = line.baseline - line.top

            // C (defense-in-depth): 行偏移必须在 content 范围内；否则说明 page/content 不匹配
            // （典型场景：跨章翻页时 prevPage/nextPage 拿到错章的 content），跳过绘制避免崩溃。
            val start = line.startCharOffset
            val end = line.endCharOffset
            val len = ctx.content.length
            if (start < 0 || end < start || end > len) {
                android.util.Log.w(
                    "ReaderPageRenderer",
                    "skip out-of-bounds line: start=$start end=$end contentLen=$len pageChapter=${ctx.page.chapterIndex}",
                )
                return@recordIfNeeded
            }

            // 判断是否需要两端对齐：JUSTIFY 模式且非段落末行
            val shouldJustify = textAlign == ReaderTextAlign.JUSTIFY && !line.isParagraphEnd
            // 判断是否需要右对齐
            val shouldRightAlign = textAlign == ReaderTextAlign.RIGHT

            if (shouldJustify && line.charWidths != null) {
                drawTextJustified(line, startX, relativeBaseline, ctx)
            } else if (shouldRightAlign) {
                // 右对齐：将文本绘制位置偏移到右侧
                val rightEdge = ctx.availableWidth
                val textWidth = line.measuredWidth
                val rightAlignedX = rightEdge - textWidth
                drawText(ctx.content, start, end, rightAlignedX, relativeBaseline, ctx.textPaint)
            } else {
                drawText(ctx.content, start, end, startX, relativeBaseline, ctx.textPaint)
            }
        }

        // 平移到行顶部绘制
        canvas.save()
        canvas.translate(0f, line.top)
        recorder.draw(canvas)
        canvas.restore()
    }

    /**
     * 两端对齐绘制：通过临时调整 Paint.letterSpacing 实现单次 drawText，O(1) JNI 调用
     */
    private fun Canvas.drawTextJustified(line: TextLine, x: Float, y: Float, ctx: PageRenderContext) {
        // 两端对齐时，可用宽度需要减去该行的起始偏移（即首行缩进），否则会将缩进宽度也作为额外空间分配给字距，导致整行超出右边界
        val extraSpace = ctx.availableWidth - line.startXOffset - line.measuredWidth

        if (extraSpace <= 0f) {
            drawText(ctx.content, line.startCharOffset, line.endCharOffset, x, y, ctx.textPaint)
            return
        }

        val charCount = line.endCharOffset - line.startCharOffset
        val justifySpacing = if (charCount > 1) extraSpace / (charCount - 1) else 0f
        
        // 临时调整画笔的 letterSpacing 以合并 JNI 调用
        // Paint.letterSpacing 的单位是 em (em = pixel / textSize)
        val originalLetterSpacing = ctx.textPaint.letterSpacing
        val totalSpacingPx = ctx.letterSpacingPx + justifySpacing
        if (ctx.textPaint.textSize > 0) {
            ctx.textPaint.letterSpacing = totalSpacingPx / ctx.textPaint.textSize
        }

        drawText(ctx.content, line.startCharOffset, line.endCharOffset, x, y, ctx.textPaint)

        // 恢复画笔
        ctx.textPaint.letterSpacing = originalLetterSpacing
    }

    private fun intersects(range: SelectionRange?, start: Int, end: Int): Boolean {
        if (range == null) return false
        return range.startPos < end && range.endPos > start
    }

    private fun drawBatteryAt(canvas: Canvas, x: Float, y: Float, align: Paint.Align, batteryLevel: Int, density: Float, paint: Paint) {
        val batWidth = 22f * density
        val capWidth = 2f * density
        val capHeight = 4f * density
        val batHeight = 11f * density
        val percentText = "$batteryLevel%"
        val percentTextWidth = paint.measureText(percentText)
        val spacing = 4f * density

        val totalWidth = batWidth + spacing + percentTextWidth

        val groupLeft = when (align) {
            Paint.Align.LEFT -> x
            Paint.Align.CENTER -> x - totalWidth / 2f
            Paint.Align.RIGHT -> x - totalWidth
        }

        // Draw text
        val textX = groupLeft
        val oldAlign = paint.textAlign
        paint.textAlign = Paint.Align.LEFT // Temporarily left-align for explicit coordinates
        canvas.drawText(percentText, textX, y, paint)
        paint.textAlign = oldAlign

        // Draw battery icon
        val batteryLeft = groupLeft + percentTextWidth + spacing
        val batteryRight = batteryLeft + batWidth
        val batteryTop = y - paint.textSize * 0.8f
        val batteryBottom = batteryTop + batHeight

        batteryStrokePaint.color = paint.color
        batteryStrokePaint.alpha = paint.alpha
        batteryStrokePaint.strokeWidth = 1f * density
        batteryFillPaint.color = paint.color
        batteryFillPaint.alpha = paint.alpha

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
    }
}
