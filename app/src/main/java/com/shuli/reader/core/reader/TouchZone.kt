package com.shuli.reader.core.reader

/**
 * 触控区域枚举
 */
enum class TouchZone {
    // 上方三个区域
    TOP_LEFT,      // 左上
    TOP_CENTER,    // 中上
    TOP_RIGHT,     // 右上

    // 中间三个区域
    MIDDLE_LEFT,   // 左中
    MIDDLE_CENTER, // 中中
    MIDDLE_RIGHT,  // 右中

    // 下方三个区域
    BOTTOM_LEFT,   // 左下
    BOTTOM_CENTER, // 中下
    BOTTOM_RIGHT,  // 右下
}

/**
 * 触控动作枚举
 */
enum class TouchAction {
    PREV_PAGE,      // 上一页
    NEXT_PAGE,      // 下一页
    TOGGLE_TOOLBAR, // 切换工具栏
    SCROLL_UP,      // 向上滚动
    SCROLL_DOWN,    // 向下滚动
}

/**
 * 热区计算器
 */
object TouchZoneCalculator {

    /**
     * 计算触控区域
     *
     * @param leftZoneRatio 左侧区域宽度比例（0.2~0.5），右侧对称，中间为剩余
     */
    fun calculateZone(
        touchX: Float,
        touchY: Float,
        screenWidth: Int,
        screenHeight: Int,
        leftZoneRatio: Float = 0.33f,
    ): TouchZone {
        val leftWidth = screenWidth * leftZoneRatio.coerceIn(0.2f, 0.5f)
        val rightStart = screenWidth - leftWidth
        val thirdHeight = screenHeight / 3f

        val col = when {
            touchX < leftWidth -> 0
            touchX < rightStart -> 1
            else -> 2
        }

        val row = when {
            touchY < thirdHeight -> 0
            touchY < thirdHeight * 2 -> 1
            else -> 2
        }

        return when (row) {
            0 -> when (col) {
                0 -> TouchZone.TOP_LEFT
                1 -> TouchZone.TOP_CENTER
                else -> TouchZone.TOP_RIGHT
            }
            1 -> when (col) {
                0 -> TouchZone.MIDDLE_LEFT
                1 -> TouchZone.MIDDLE_CENTER
                else -> TouchZone.MIDDLE_RIGHT
            }
            else -> when (col) {
                0 -> TouchZone.BOTTOM_LEFT
                1 -> TouchZone.BOTTOM_CENTER
                else -> TouchZone.BOTTOM_RIGHT
            }
        }
    }

    /**
     * 根据触控区域获取动作
     */
    fun getActionForZone(
        zone: TouchZone,
        isScrollMode: Boolean = false,
    ): TouchAction {
        return when (zone) {
            TouchZone.TOP_LEFT,
            TouchZone.MIDDLE_LEFT,
            TouchZone.BOTTOM_LEFT -> {
                if (isScrollMode) TouchAction.SCROLL_UP else TouchAction.PREV_PAGE
            }
            TouchZone.TOP_RIGHT,
            TouchZone.MIDDLE_RIGHT,
            TouchZone.BOTTOM_RIGHT -> {
                if (isScrollMode) TouchAction.SCROLL_DOWN else TouchAction.NEXT_PAGE
            }
            TouchZone.TOP_CENTER,
            TouchZone.MIDDLE_CENTER,
            TouchZone.BOTTOM_CENTER -> {
                TouchAction.TOGGLE_TOOLBAR
            }
        }
    }
}