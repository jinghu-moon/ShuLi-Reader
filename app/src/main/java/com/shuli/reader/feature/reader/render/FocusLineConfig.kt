package com.shuli.reader.feature.reader.render

/**
 * 聚焦线渲染参数。
 *
 * 将聚焦线的绘制逻辑抽象为纯数据，便于单元测试。
 * 实际 Canvas 绘制由 ReaderCanvasView 负责。
 */
data class FocusLineConfig(
    val enabled: Boolean,
    val lineY: Float,
    val startX: Float,
    val endX: Float,
) {
    /**
     * 是否应该绘制聚焦线。
     */
    val shouldDraw: Boolean get() = enabled && lineY > 0f

    companion object {
        /**
         * 从当前阅读行构建聚焦线配置。
         *
         * @param enabled 是否启用聚焦线
         * @param currentReadingLineY 当前阅读行的 Y 坐标（baseline），null 表示无当前行
         * @param marginLeft 左边距
         * @param pageWidth 页面宽度
         * @param marginRight 右边距
         */
        fun fromReadingLine(
            enabled: Boolean,
            currentReadingLineY: Float?,
            marginLeft: Float,
            pageWidth: Float,
            marginRight: Float,
        ): FocusLineConfig = FocusLineConfig(
            enabled = enabled,
            lineY = currentReadingLineY ?: 0f,
            startX = marginLeft,
            endX = pageWidth - marginRight,
        )
    }
}
