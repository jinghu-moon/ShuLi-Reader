package com.shuli.reader.feature.reader.settings

import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EyeCareTimer 纯逻辑测试。
 *
 * 测试时间累加逻辑和阈值判断，不依赖协程调度。
 */
class EyeCareTimerTest {

    // T-1.3.1: 间隔为 0 时不启动计时器
    @Test
    fun start_intervalZero_doesNotStartChecker() {
        var triggered = false
        val timer = EyeCareTimer(triggered = { triggered = true })
        timer.start(0)
        assertFalse("checker should not be running", timer.isRunning)
    }

    // T-1.3.2: onPageTurned() 累加阅读时间
    @Test
    fun onPageTurned_accumulatesReadingTime() {
        val timer = EyeCareTimer()
        timer.start(15) // 15 分钟

        // 模拟翻页：第一次翻页设置 lastPageTurnMs
        timer.onPageTurned(0)
        // 快进 2 分钟
        timer.onPageTurned(120_000)

        // accumulatedReadingMs 应等于 120_000（2 分钟）
        assertEquals(120_000L, timer.accumulatedReadingMs)
    }

    // T-1.3.3: 翻页间隔 > 5 分钟不累加
    @Test
    fun onPageTurned_gapOver5Min_doesNotAccumulate() {
        val timer = EyeCareTimer()
        timer.start(15)

        timer.onPageTurned(0)
        // 快进 10 分钟（超过 5 分钟阈值）
        timer.onPageTurned(600_000)

        // 不应累加（gap > 5 分钟）
        assertEquals(0L, timer.accumulatedReadingMs)
    }

    // T-1.3.4: 达到间隔阈值触发提醒
    @Test
    fun accumulatedTime_reachesThreshold_triggersReminder() {
        var triggered = false
        val timer = EyeCareTimer(triggered = { triggered = true })
        timer.start(1) // 1 分钟阈值

        timer.onPageTurned(0)
        // 快进 61 秒（超过 1 分钟）
        timer.onPageTurned(61_000)

        // 检查阈值
        timer.checkThreshold()

        assertTrue("reminder should have triggered", triggered)
    }

    // T-1.3.5: 关闭提醒后重置计时
    @Test
    fun stop_resetsAccumulatedTime() {
        val timer = EyeCareTimer()
        timer.start(15)

        timer.onPageTurned(0)
        timer.onPageTurned(120_000)

        assertTrue(timer.accumulatedReadingMs > 0)

        timer.stop()
        assertEquals(0L, timer.accumulatedReadingMs)
        assertFalse(timer.isRunning)
    }

    // T-1.3.6: stop() 取消 Job
    @Test
    fun stop_cancelsJob() {
        val scope = TestScope()
        val timer = EyeCareTimer(scope = scope)
        timer.start(15)
        assertTrue(timer.isRunning)

        timer.stop()
        assertFalse(timer.isRunning)
    }

    // T-1.3.7: restart() 重置计时并重启 checker
    @Test
    fun restart_resetsAndRestartsChecker() {
        val scope = TestScope()
        var triggerCount = 0
        val timer = EyeCareTimer(scope = scope, triggered = { triggerCount++ })
        timer.start(1) // 1 分钟

        timer.onPageTurned(0)
        timer.onPageTurned(61_000)
        timer.checkThreshold()
        assertEquals(1, triggerCount)

        // restart 重置计时
        timer.restart()
        assertEquals(0L, timer.accumulatedReadingMs)
        assertTrue(timer.isRunning)

        // 再次触发
        timer.onPageTurned(61_000)
        timer.onPageTurned(122_000)
        timer.checkThreshold()
        assertEquals(2, triggerCount)
    }
}
