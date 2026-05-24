package com.shuli.reader.core.reader

/**
 * 用于测试的假文本测量器
 */
class FakeTextMeasurer : TextMeasurer {
    // 假设每个字符的宽度是 textSize * 0.6
    override fun measureTextWidth(text: String, textSize: Float): Float {
        return text.length * textSize * 0.6f
    }

    // 假设行高是 textSize * lineHeight
    override fun measureTextHeight(textSize: Float, lineHeight: Float): Float {
        return textSize * lineHeight
    }

    // 假设每个字符的宽度是 textSize * 0.6
    override fun measureCharWidth(char: Char, textSize: Float): Float {
        return textSize * 0.6f
    }
}