package com.shuli.reader.core.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.shuli.reader.core.canvasrecorder.CanvasRecorderFactory
import com.shuli.reader.core.canvasrecorder.recordIfNeeded
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
) {
    /**
     * 渲染页面
     */
    fun render(
        canvas: Canvas,
        page: TextPage,
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
        // 1. 绘制背景
        if (backgroundPaint != null) {
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), backgroundPaint)
        }

        val density = page.density

        // 2. 绘制高亮背景（TTS高亮与用户选区）
        page.lines.forEach { line ->
            val startX = page.marginHorizontal + line.startXOffset
            val textWidth = textPaint.measureText(line.text)
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

        // 3. 绘制正文文本（per-line CanvasRecorder 优化）
        for (line in page.lines) {
            drawLineWithRecorder(canvas, line, page)
        }

        // 4. 绘制页眉（40% 透明度）
        if (headerText.isNotEmpty()) {
            val oldHeaderAlpha = headerPaint.alpha
            headerPaint.alpha = 102 // 40% alpha (102 / 255)

            // 页眉垂直定位在 marginVertical 的中心区域
            val headerBaseline = 48f * density + 24f * density * 0.6f
            canvas.drawText(headerText, page.marginHorizontal, headerBaseline, headerPaint)

            headerPaint.alpha = oldHeaderAlpha
        }

        // 5. 绘制页脚（含 40% 透明度的页脚文本与精美电池图标）
        val footerBaseline = canvas.height - 48f * density - 24f * density * 0.4f
        if (footerText.isNotEmpty()) {
            val oldFooterAlpha = footerPaint.alpha
            footerPaint.alpha = 102

            canvas.drawText(footerText, page.marginHorizontal, footerBaseline, footerPaint)

            footerPaint.alpha = oldFooterAlpha
        }

        // 绘制电池电量图标
        val oldFooterAlphaForBattery = footerPaint.alpha
        footerPaint.alpha = 102
        drawBattery(canvas, page.marginHorizontal, footerBaseline, batteryLevel, density)
        footerPaint.alpha = oldFooterAlphaForBattery

        // 6. 绘制进度条
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
     * 使用 per-line CanvasRecorder 绘制单行文本
     * 选区/TTS 高亮变化时仅重画受影响的行，而非整页
     */
    private fun drawLineWithRecorder(canvas: Canvas, line: TextLine, page: TextPage) {
        val recorder = line.canvasRecorder
            ?: CanvasRecorderFactory.create().also { line.canvasRecorder = it }

        val lineHeight = (line.bottom - line.top).toInt()
        val startX = page.marginHorizontal + line.startXOffset

        recorder.recordIfNeeded(canvas.width, lineHeight) {
            // 绘制文本（相对于行顶部）
            val relativeBaseline = line.baseline - line.top
            drawText(line.text, startX, relativeBaseline, textPaint)
        }

        // 平移到行顶部绘制
        canvas.save()
        canvas.translate(0f, line.top)
        recorder.draw(canvas)
        canvas.restore()
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

        // 1. 绘制电池外框
        val strokePaint = Paint().apply {
            color = footerPaint.color
            alpha = 102
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            isAntiAlias = true
        }
        val batteryRect = RectF(batteryLeft, batteryTop, batteryRight, batteryBottom)
        canvas.drawRoundRect(batteryRect, 1.5f * density, 1.5f * density, strokePaint)

        // 2. 绘制电池头 (Cap)
        val capLeft = batteryRight
        val capRight = capLeft + capWidth
        val capTop = batteryTop + (batHeight - capHeight) / 2f
        val capBottom = capTop + capHeight
        val capPaint = Paint().apply {
            color = footerPaint.color
            alpha = 102
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(RectF(capLeft, capTop, capRight, capBottom), capPaint)

        // 3. 绘制电池内部填充
        val fillPaint = Paint().apply {
            color = footerPaint.color
            alpha = 102
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val innerPadding = 1.5f * density
        val maxFillWidth = batWidth - innerPadding * 2
        val fillWidth = maxFillWidth * (batteryLevel.coerceIn(0, 100) / 100f)
        val fillRect = RectF(
            batteryLeft + innerPadding,
            batteryTop + innerPadding,
            batteryLeft + innerPadding + fillWidth,
            batteryBottom - innerPadding
        )
        canvas.drawRect(fillRect, fillPaint)

        // 4. 绘制电量百分比
        val percentText = "$batteryLevel%"
        val percentTextWidth = footerPaint.measureText(percentText)
        val percentX = batteryLeft - 4f * density - percentTextWidth
        canvas.drawText(percentText, percentX, footerBaseline, footerPaint)
    }
}