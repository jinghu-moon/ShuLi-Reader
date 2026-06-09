package com.shuli.reader.feature.reader

import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.tts.TtsState

/**
 * 阅读器覆盖层状态（高频变化）。
 *
 * 选区、TTS 高亮、笔记高亮变化时只更新此 StateFlow，
 * 不触发 pageState 或 preferences 的 recomposition。
 */
data class ReaderOverlayState(
    val selectedRange: SelectionRange? = null,
    val ttsState: TtsState = TtsState.IDLE,
    val ttsActiveRange: SelectionRange? = null,
    val sleepTimerRemainingSeconds: Int = -1,
)
