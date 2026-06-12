package com.shuli.reader.feature.reader.settings

/**
 * 蓝光过滤预设：通过色温快捷控制蓝光。
 *
 * 不在 Registry 中注册独立条目，复用 `color_temperature` 字段。
 * 开启时将色温设为 3400K（暖色），关闭时恢复上次手动设置的色温。
 */
object BlueLightPreset {
    /** 蓝光过滤启用时的目标色温 */
    const val BLUE_LIGHT_TEMP = 3400f

    /** 默认色温（无手动设置时的恢复值） */
    private const val DEFAULT_TEMP = 6500f

    /**
     * 切换蓝光过滤。
     *
     * @param currentTemp 当前色温
     * @param lastManualTemp 上次手动设置的色温（关闭时恢复），null 则恢复默认
     * @return 新的色温值
     */
    fun toggle(currentTemp: Float, lastManualTemp: Float? = null): Float {
        return if (isActive(currentTemp)) {
            // 关闭蓝光：恢复上次手动色温
            lastManualTemp ?: DEFAULT_TEMP
        } else {
            // 开启蓝光
            BLUE_LIGHT_TEMP
        }
    }

    /**
     * 判断蓝光过滤是否处于激活状态。
     */
    fun isActive(colorTemperature: Float): Boolean =
        colorTemperature <= BLUE_LIGHT_TEMP
}
