package com.shuli.reader.core.reader.animation

/**
 * 翻页动画工厂
 */
object PageDelegateFactory {

    /**
     * 翻页动画类型
     */
    enum class PageAnimType {
        NONE,      // 无动画
        COVER,     // 覆盖翻页
        HORIZONTAL, // 水平平移
        SIMULATION, // 仿真翻页
        SCROLL,    // 垂直滚动
    }

    /**
     * 创建翻页委托
     */
    fun create(type: PageAnimType): PageDelegate {
        return when (type) {
            PageAnimType.NONE -> NoAnimPageDelegate()
            PageAnimType.COVER -> CoverPageDelegate()
            PageAnimType.HORIZONTAL -> HorizontalPageDelegate()
            PageAnimType.SIMULATION -> SimulationPageDelegate()
            PageAnimType.SCROLL -> ScrollPageDelegate()
        }
    }
}
