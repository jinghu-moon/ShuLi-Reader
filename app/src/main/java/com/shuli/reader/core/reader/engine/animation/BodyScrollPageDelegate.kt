package com.shuli.reader.core.reader.engine.animation

/**
 * 使用正文盒纵向位移绘制的翻页委托。
 *
 * View 层只依赖这个窄接口裁剪正文区域，避免把“连续滚动”和“上下滑动翻页”
 * 两种交互语义绑定到同一个具体类。
 */
interface BodyScrollPageDelegate : PageDelegate {
    fun getScrollPosition(): Float
    fun getViewportHeight(): Float
    fun setViewportHeight(height: Float)
}

