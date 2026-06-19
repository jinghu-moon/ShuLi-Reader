package com.shuli.reader.core.reader.text

import android.graphics.Paint

/**
 * 基于 Android [Paint] 的精确文本测量器。
 *
 * 与 [SimpleTextMeasurer] 的粗略估算不同，本实现使用 [Paint.getTextWidths]
 * 获取真实字形宽度，确保分页结果与 Canvas 绘制完全一致。
 *
 * ## 线程安全
 * [Paint] 不是线程安全的。调用方应确保：
 * - [measureTextWidths] 在单个 Dispatcher（如 Default）上调用
 * - [updatePaint] 仅在测量间隙（如 reflow 前）调用
 *
 * ## letterSpacing 处理
 * [Paint.getTextWidths] 返回的宽度不包含 [Paint.getLetterSpacing]。
 * [measureTextWidths] 的返回值同样不含字距，由 Paginator 在 calculateLine
 * 中根据 [ReaderLayoutConfig.letterSpacingPx] 统一追加，保持架构一致。
 */
class AndroidTextMeasurer(
    paint: Paint,
) : TextMeasurer {

    // 复制 paint 属性快照，避免外部修改影响测量一致性
    @Volatile
    private var paintSnapshot: Paint = Paint(paint)

    // 复用单字符缓冲，避免 measureCharWidth 每次 char.toString() 分配
    private val charBuf = CharArray(1)
    private val charWidthBuf = FloatArray(1)

    /**
     * 同步 View 层 Paint 的最新配置。
     * 应在 textSize / typeface / letterSpacing / fakeBold 等属性变更后调用。
     */
    fun updatePaint(source: Paint) {
        paintSnapshot.set(source)
    }

    override fun measureTextWidth(text: String, textSize: Float): Float {
        if (text.isEmpty()) return 0f
        val p = paintSnapshot
        val savedSize = p.textSize
        if (savedSize != textSize) p.textSize = textSize
        val w = p.measureText(text)
        if (savedSize != textSize) p.textSize = savedSize
        return w
    }

    override fun measureTextHeight(textSize: Float, lineHeight: Float): Float {
        val p = paintSnapshot
        val savedSize = p.textSize
        if (savedSize != textSize) p.textSize = textSize
        val fm = p.fontMetrics
        val height = (fm.descent - fm.ascent) * lineHeight
        if (savedSize != textSize) p.textSize = savedSize
        return height
    }

    override fun measureCharWidth(char: Char, textSize: Float): Float {
        val p = paintSnapshot
        val savedSize = p.textSize
        if (savedSize != textSize) p.textSize = textSize
        charBuf[0] = char
        p.getTextWidths(charBuf, 0, 1, charWidthBuf)
        if (savedSize != textSize) p.textSize = savedSize
        return charWidthBuf[0]
    }

    override fun measureTextWidths(text: String, textSize: Float): FloatArray {
        if (text.isEmpty()) return FloatArray(0)
        val p = paintSnapshot
        val savedSize = p.textSize
        if (savedSize != textSize) p.textSize = textSize
        val widths = FloatArray(text.length)
        p.getTextWidths(text, 0, text.length, widths)
        if (savedSize != textSize) p.textSize = savedSize
        return widths
    }

    override fun measureTextWidths(text: String, start: Int, end: Int, textSize: Float): FloatArray {
        if (start >= end) return FloatArray(0)
        val p = paintSnapshot
        val savedSize = p.textSize
        if (savedSize != textSize) p.textSize = textSize
        val len = end - start
        val widths = FloatArray(len)
        p.getTextWidths(text, start, end, widths)
        if (savedSize != textSize) p.textSize = savedSize
        return widths
    }
}
