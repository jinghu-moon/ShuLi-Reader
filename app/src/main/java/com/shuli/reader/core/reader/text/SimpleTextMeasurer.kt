package com.shuli.reader.core.reader.text

/**
 * 无 Android 依赖的默认文本测量器。
 * 用于 ViewModel 首屏分页，Canvas 层仍负责最终绘制。
 */
class SimpleTextMeasurer : TextMeasurer {
    override fun measureTextWidth(text: String, textSize: Float): Float {
        return measureTextWidth(text, textSize, 0f)
    }

    fun measureTextWidth(text: String, textSize: Float, letterSpacingPx: Float): Float {
        if (text.isEmpty()) return 0f
        // 消除 sumOf { .toDouble() } 的逐字符 Double 装箱开销
        var width = 0f
        for (char in text) {
            width += measureCharWidth(char, textSize)
        }
        width += letterSpacingPx * (text.length - 1).coerceAtLeast(0)
        return width
    }

    override fun measureTextHeight(textSize: Float, lineHeight: Float): Float {
        return textSize * lineHeight
    }

    override fun measureCharWidth(char: Char, textSize: Float): Float {
        return if (char.code < ASCII_BOUNDARY) {
            textSize * ASCII_WIDTH_RATIO
        } else {
            textSize
        }
    }

    override fun measureTextWidths(text: String, textSize: Float): FloatArray {
        val widths = FloatArray(text.length)
        val asciiWidth = textSize * ASCII_WIDTH_RATIO
        for (i in text.indices) {
            widths[i] = if (text[i].code < ASCII_BOUNDARY) asciiWidth else textSize
        }
        return widths
    }

    override fun measureTextWidths(text: String, start: Int, end: Int, textSize: Float): FloatArray {
        val len = end - start
        val widths = FloatArray(len)
        val asciiWidth = textSize * ASCII_WIDTH_RATIO
        for (i in 0 until len) {
            widths[i] = if (text[start + i].code < ASCII_BOUNDARY) asciiWidth else textSize
        }
        return widths
    }

    private companion object {
        private const val ASCII_BOUNDARY = 128
        private const val ASCII_WIDTH_RATIO = 0.56f
    }
}
