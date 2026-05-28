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

    /**
     * 批量测量整段文本中每个字符的宽度。
     * 返回 FloatArray，长度 = text.length，每个元素为对应字符的宽度。
     *
     * Android 实现应使用 Paint.getTextWidths() 一次性获取；
     * SimpleTextMeasurer 逐字符计算但避免多次调用开销。
     */
    fun measureTextWidths(text: String, textSize: Float): FloatArray
}