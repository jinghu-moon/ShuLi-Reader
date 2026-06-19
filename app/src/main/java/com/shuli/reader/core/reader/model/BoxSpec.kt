package com.shuli.reader.core.reader.model

/**
 * 描述一个布局盒子的规格（输入参数）。
 *
 * @param insets      四向边距（px）
 * @param innerHeight 内容区高度（px），不含 insets。FILL 模式下忽略。
 * @param placement   放置方式：TOP_DOWN / BOTTOM_UP / FILL
 * @param visible     是否参与布局。false 时 PageLayout 中对应 BoxBounds 为 null
 */
data class BoxSpec(
    val insets: BoxInsetsPx = BoxInsetsPx.ZERO,
    val innerHeight: Float = 0f,
    val placement: Placement = Placement.TOP_DOWN,
    val visible: Boolean = true,
) {
    enum class Placement {
        TOP_DOWN,
        BOTTOM_UP,
        FILL,
    }
}

/**
 * 单个盒子的计算结果：屏幕坐标（px）。
 * width/height 在构造时计算，避免每次访问的减法开销。
 */
data class BoxBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float = right - left
    val height: Float = bottom - top
}

/**
 * 单页布局：四个盒子的最终位置。
 * 按页生成（非按章共享），首页包含 title box，后续页 title=null。
 */
data class PageLayout(
    val header: BoxBounds?,
    val title: BoxBounds?,
    val body: BoxBounds,
    val footer: BoxBounds?,
    val pageWidth: Float,
    val pageHeight: Float,
)
