package com.shuli.reader.core.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceMetricsTest {

    @Test
    fun frameTimeRecorder_countsTotalAndDroppedFrames() {
        val recorder = FrameTimeRecorder()

        recorder.recordFrame(0L)
        recorder.recordFrame(16_000_000L)
        recorder.recordFrame(33_000_000L)
        recorder.recordFrame(60_000_000L)

        val stats = recorder.stats()
        assertEquals(3, stats.totalFrames)
        assertEquals(2, stats.jankFrames)
        assertTrue(stats.maxFrameMs > 26.0)
    }

    @Test
    fun frameTimeRecorderReset_clearsState() {
        val recorder = FrameTimeRecorder()
        recorder.recordFrame(0L)
        recorder.recordFrame(20_000_000L)

        recorder.reset()

        assertEquals(0, recorder.stats().totalFrames)
    }

    @Test
    fun startupTrace_recordsStageAndTotalDuration() {
        var now = 1000L
        val trace = StartupTrace(clock = { now })

        now += 120L
        trace.mark("file")
        now += 180L
        trace.mark("paginate")

        val report = trace.report()
        assertEquals(2, report.size)
        assertEquals(120L, report[0].elapsedMs)
        assertEquals(300L, trace.totalMs())
    }

    @Test
    fun memorySample_convertsBytesToMegabytes() {
        val sample = MemorySample(
            usedBytes = 12L * 1024L * 1024L,
            maxBytes = 256L * 1024L * 1024L,
        )

        assertEquals(12L, sample.usedMegabytes)
        assertEquals(256L, sample.maxMegabytes)
    }
}
