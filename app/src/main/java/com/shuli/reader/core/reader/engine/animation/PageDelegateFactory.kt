package com.shuli.reader.core.reader.engine.animation

import com.shuli.reader.core.data.PageAnimSpeed

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
        VERTICAL_SLIDE, // 上下滑动翻页
        SCROLL,    // 连续滚动
    }

    /**
     * 创建翻页委托（使用默认速度 NORMAL）
     */
    fun create(type: PageAnimType): PageDelegate = create(type, PageAnimSpeed.NORMAL)

    /**
     * 创建翻页委托，指定动画速度。
     *
     * @param type 动画类型
     * @param speed 动画速度，影响动画时长
     */
    fun create(type: PageAnimType, speed: PageAnimSpeed): PageDelegate {
        val spec = AnimSpecCache.create(speed)
        return when (type) {
            PageAnimType.NONE -> NoAnimPageDelegate()
            PageAnimType.COVER -> CoverPageDelegate(durationMs = spec.durationMs)
            PageAnimType.HORIZONTAL -> HorizontalPageDelegate(durationMs = spec.durationMs)
            PageAnimType.SIMULATION -> SimulationPageDelegate(durationMs = spec.durationMs)
            PageAnimType.VERTICAL_SLIDE -> VerticalSlidePageDelegate(durationMs = spec.durationMs)
            PageAnimType.SCROLL -> ScrollPageDelegate(durationMs = spec.durationMs)
        }
    }
}
