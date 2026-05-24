package com.shuli.reader.core.reader

/**
 * 文本测量接口，用于计算文本的宽度和高度
 */
interface TextMeasurer {
    /**
     * 测量文本的宽度
     */
    fun measureTextWidth(text: String, textSize: Float): Float

    /**
     * 测量文本的高度
     */
    fun measureTextHeight(textSize: Float, lineHeight: Float): Float

    /**
     * 测量单个字符的宽度
     */
    fun measureCharWidth(char: Char, textSize: Float): Float
}