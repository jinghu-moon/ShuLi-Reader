package com.shuli.reader.feature.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TTS 自动翻页 + 定时停止控制器。
 *
 * 朗读完成时自动翻页，支持定时停止功能。
 */
class TtsAutoPageController(
    private val scope: CoroutineScope,
    private val autoPage: Boolean,
    private val timerMinutes: Int,
    private val onNextPage: () -> Unit,
    private val onStop: () -> Unit = {},
) {
    private var timerJob: Job? = null

    /**
     * 启动定时器（如果 timerMinutes > 0）。
     */
    fun start() {
        stop()
        if (timerMinutes > 0) {
            timerJob = scope.launch {
                delay(timerMinutes * 60_000L)
                onStop()
            }
        }
    }

    /**
     * 朗读完成时调用。
     */
    fun onDone() {
        if (autoPage) {
            onNextPage()
        }
    }

    /**
     * 停止定时器。
     */
    fun stop() {
        timerJob?.cancel()
        timerJob = null
    }
}
