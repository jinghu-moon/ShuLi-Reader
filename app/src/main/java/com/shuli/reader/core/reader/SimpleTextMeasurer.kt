package com.shuli.reader.core.reader

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
        val baseWidth = text.sumOf { char -> measureCharWidth(char, textSize).toDouble() }.toFloat()
        val spacing = letterSpacingPx * (text.length - 1).coerceAtLeast(0)
        return baseWidth + spacing
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

    private companion object {
        private const val ASCII_BOUNDARY = 128
        private const val ASCII_WIDTH_RATIO = 0.56f
    }
}
