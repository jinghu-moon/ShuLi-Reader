package com.shuli.reader.feature.reader.settings

/**
 * 振动反馈辅助接口。
 *
 * 将 Android View 依赖抽象为接口，便于单元测试。
 */
fun interface HapticFeedbackPerformer {
    fun performHapticFeedback()
}

/**
 * 翻页时执行振动反馈（如已启用）。
 *
 * @param enabled 是否启用振动
 * @param performer 振动执行器
 */
fun performPageTurnHaptic(enabled: Boolean, performer: HapticFeedbackPerformer) {
    if (enabled) {
        performer.performHapticFeedback()
    }
}
