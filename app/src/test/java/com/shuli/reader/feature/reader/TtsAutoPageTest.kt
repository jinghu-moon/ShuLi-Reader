package com.shuli.reader.feature.reader

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsAutoPageTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // T-3.3.1: ttsAutoPage = true 时朗读完成自动翻页
    @Test
    fun ttsAutoPage_true_onDone_triggersNextPage() = testScope.runTest {
        var nextPageCalled = false
        val controller = TtsAutoPageController(
            scope = testScope,
            autoPage = true,
            timerMinutes = 0,
            onNextPage = { nextPageCalled = true },
        )

        controller.onDone()
        assertTrue("nextPage should be called", nextPageCalled)
    }

    // T-3.3.2: ttsAutoPage = false 时不翻页
    @Test
    fun ttsAutoPage_false_onDone_doesNotTriggerNextPage() = testScope.runTest {
        var nextPageCalled = false
        val controller = TtsAutoPageController(
            scope = testScope,
            autoPage = false,
            timerMinutes = 0,
            onNextPage = { nextPageCalled = true },
        )

        controller.onDone()
        assertFalse("nextPage should NOT be called", nextPageCalled)
    }

    // T-3.3.3: ttsTimer > 0 时定时停止
    @Test
    fun ttsTimer_positive_stopsAfterTimeout() = testScope.runTest {
        var stopped = false
        val controller = TtsAutoPageController(
            scope = testScope,
            autoPage = true,
            timerMinutes = 15,
            onNextPage = { },
            onStop = { stopped = true },
        )

        controller.start()

        // 快进 16 分钟
        advanceTimeBy(16 * 60_000)

        assertTrue("TTS should have stopped", stopped)
    }

    @Test
    fun ttsTimer_zero_doesNotAutoStop() = testScope.runTest {
        var stopped = false
        val controller = TtsAutoPageController(
            scope = testScope,
            autoPage = true,
            timerMinutes = 0,
            onNextPage = { },
            onStop = { stopped = true },
        )

        controller.start()
        advanceTimeBy(30 * 60_000) // 30 分钟

        assertFalse("TTS should NOT have stopped", stopped)
    }

    @Test
    fun stop_cancelsTimer() = testScope.runTest {
        var stopped = false
        val controller = TtsAutoPageController(
            scope = testScope,
            autoPage = true,
            timerMinutes = 15,
            onNextPage = { },
            onStop = { stopped = true },
        )

        controller.start()
        controller.stop()

        advanceTimeBy(20 * 60_000)
        assertFalse("TTS should NOT have stopped after manual stop", stopped)
    }
}
