package com.shuli.reader.feature.reader.screen
import com.shuli.reader.feature.reader.screen.ReaderOverlayState

import com.shuli.reader.core.reader.model.SelectionRange

/**
 * 阅读器覆盖层状态（高频变化）。
 *
 * 选区、笔记高亮变化时只更新此 StateFlow，
 * 不触发 pageState 或 preferences 的 recomposition。
 */
data class ReaderOverlayState(
    val selectionScreenX: Float = 0f,
    val selectionScreenY: Float = 0f,
    val selectedRange: SelectionRange? = null,
    val sleepTimerRemainingSeconds: Int = -1,
)
