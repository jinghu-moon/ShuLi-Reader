package com.shuli.reader.core.reader.model

import kotlinx.serialization.Serializable

/**
 * 盒子四向边距（单位：dp）。
 * 用于 ReaderPreferences 存储用户设置。
 * 通过 toPx() 转换为 BoxInsetsPx 后参与布局计算。
 */
@Serializable
data class BoxInsetsDp(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
    val right: Float = 0f,
) {
    fun toPx(density: Float) = BoxInsetsPx(
        top = top * density,
        bottom = bottom * density,
        left = left * density,
        right = right * density,
    )

    companion object {
        val ZERO = BoxInsetsDp()
    }
}

/**
 * 盒子四向边距（单位：px）。
 * 用于 PageLayoutCalculator 参与布局计算。
 * 由 BoxInsetsDp.toPx(density) 产生。
 */
data class BoxInsetsPx(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
    val right: Float = 0f,
) {
    companion object {
        val ZERO = BoxInsetsPx()
    }
}

/**
 * 将系统安全区合并到盒子边距中。
 * 实际边距 = max(用户设置边距, 系统安全区)。
 */
fun BoxInsetsPx.withSystemInsets(insets: androidx.core.graphics.Insets): BoxInsetsPx = BoxInsetsPx(
    top = maxOf(this.top, insets.top.toFloat()),
    bottom = maxOf(this.bottom, insets.bottom.toFloat()),
    left = maxOf(this.left, insets.left.toFloat()),
    right = maxOf(this.right, insets.right.toFloat()),
)
