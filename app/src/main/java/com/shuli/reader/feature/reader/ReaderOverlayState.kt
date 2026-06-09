package com.shuli.reader.feature.reader

import com.shuli.reader.core.reader.model.SelectionRange

/**
 * 阅读器覆盖层状态（高频变化）。
 *
 * 选区、笔记高亮变化时只更新此 StateFlow，
 * 不触发 pageState 或 preferences 的 recomposition。
 */
data class ReaderOverlayState(
    val selectedRange: SelectionRange? = null,
    val sleepTimerRemainingSeconds: Int = -1,
)
