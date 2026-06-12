package com.shuli.reader.feature.reader.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 护眼提醒计时器。
 *
 * 每次翻页调用 [onPageTurned] 累加阅读时间，达到阈值后触发 [onTrigger] 回调。
 * 翻页间隔超过 5 分钟不累加（视为用户离开）。
 */
class EyeCareTimer(
    private val scope: CoroutineScope? = null,
    private val triggered: () -> Unit = {},
) {
    /** 当前累计阅读时间（毫秒） */
    var accumulatedReadingMs: Long = 0L
        private set

    /** 上次翻页时间戳 */
    private var lastPageTurnMs: Long = -1L

    /** 定期检查 Job */
    private var checkJob: Job? = null

    /** 当前提醒间隔（分钟），0 表示禁用 */
    private var intervalMinutes: Int = 0

    /** 累加时间超过此值后不再累加（视为离开） */
    private companion object {
        const val MAX_GAP_MS = 5 * 60_000L // 5 分钟
        const val CHECK_INTERVAL_MS = 10_000L // 10 秒检查一次
    }

    /**
     * 启动计时器。
     *
     * @param intervalMinutes 提醒间隔（分钟），0 表示禁用
     */
    fun start(intervalMinutes: Int) {
        stop()
        this.intervalMinutes = intervalMinutes
        if (intervalMinutes <= 0) return

        lastPageTurnMs = -1L

        scope?.let { s ->
            checkJob = s.launch {
                while (true) {
                    delay(CHECK_INTERVAL_MS)
                    checkThreshold()
                }
            }
        }
    }

    /**
     * 翻页时调用，累加阅读时间。
     *
     * @param nowMs 当前时间戳（毫秒），默认使用系统时间
     */
    fun onPageTurned(nowMs: Long = System.currentTimeMillis()) {
        if (lastPageTurnMs >= 0) {
            val gap = nowMs - lastPageTurnMs
            if (gap in 0 until MAX_GAP_MS) {
                accumulatedReadingMs += gap
            }
            // gap >= MAX_GAP_MS: 视为离开，不累加
        }
        lastPageTurnMs = nowMs
    }

    /**
     * 检查是否达到阈值，达到则触发提醒。
     */
    fun checkThreshold() {
        val thresholdMs = intervalMinutes * 60_000L
        if (thresholdMs > 0 && accumulatedReadingMs >= thresholdMs) {
            triggered()
            accumulatedReadingMs = 0L
        }
    }

    /**
     * 停止计时器并重置。
     */
    fun stop() {
        checkJob?.cancel()
        checkJob = null
        accumulatedReadingMs = 0L
        lastPageTurnMs = -1L
        intervalMinutes = 0
    }

    /**
     * 关闭提醒后重启计时器（保持当前间隔）。
     */
    fun restart() {
        val currentInterval = intervalMinutes
        accumulatedReadingMs = 0L
        start(currentInterval)
    }

    /**
     * 计时器是否正在运行。
     */
    val isRunning: Boolean get() = checkJob?.isActive == true
}
