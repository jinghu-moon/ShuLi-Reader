package com.shuli.reader.core.performance

data class FrameStats(
    val totalFrames: Int,
    val jankFrames: Int,
    val maxFrameMs: Double,
    val averageFrameMs: Double,
) {
    val jankRate: Double
        get() = if (totalFrames == 0) 0.0 else jankFrames.toDouble() / totalFrames.toDouble()
}

class FrameTimeRecorder(
    private val jankThresholdNs: Long = DEFAULT_JANK_THRESHOLD_NS,
) {
    private val frameDurationsNs = ArrayDeque<Long>(INITIAL_CAPACITY)
    private var lastFrameTimeNs: Long? = null

    fun recordFrame(frameTimeNs: Long) {
        val last = lastFrameTimeNs
        if (last != null && frameTimeNs > last) {
            if (frameDurationsNs.size >= MAX_FRAMES) {
                frameDurationsNs.removeFirst()
            }
            frameDurationsNs.addLast(frameTimeNs - last)
        }
        lastFrameTimeNs = frameTimeNs
    }

    fun stats(): FrameStats {
        if (frameDurationsNs.isEmpty()) {
            return FrameStats(totalFrames = 0, jankFrames = 0, maxFrameMs = 0.0, averageFrameMs = 0.0)
        }

        val total = frameDurationsNs.size
        val jank = frameDurationsNs.count { it > jankThresholdNs }
        val max = frameDurationsNs.maxOrNull() ?: 0L
        val average = frameDurationsNs.average()
        return FrameStats(
            totalFrames = total,
            jankFrames = jank,
            maxFrameMs = nsToMs(max),
            averageFrameMs = nsToMs(average),
        )
    }

    fun reset() {
        frameDurationsNs.clear()
        lastFrameTimeNs = null
    }

    private fun nsToMs(value: Long): Double = value / NS_PER_MS
    private fun nsToMs(value: Double): Double = value / NS_PER_MS

    private companion object {
        private const val DEFAULT_JANK_THRESHOLD_NS = 16_666_667L
        private const val NS_PER_MS = 1_000_000.0
        private const val MAX_FRAMES = 10_000
        private const val INITIAL_CAPACITY = 1024
    }
}

data class PerformanceStage(
    val name: String,
    val elapsedMs: Long,
)

class StartupTrace(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val stages = mutableListOf<PerformanceStage>()
    private var lastMark = clock()

    fun mark(name: String) {
        val now = clock()
        stages += PerformanceStage(name = name, elapsedMs = now - lastMark)
        lastMark = now
    }

    fun report(): List<PerformanceStage> = stages.toList()

    fun totalMs(): Long = stages.sumOf { it.elapsedMs }
}

data class MemorySample(
    val usedBytes: Long,
    val maxBytes: Long,
) {
    val usedMegabytes: Long get() = usedBytes / BYTES_PER_MB
    val maxMegabytes: Long get() = maxBytes / BYTES_PER_MB

    private companion object {
        private const val BYTES_PER_MB = 1024L * 1024L
    }
}

class RuntimeMemorySampler(
    private val runtime: Runtime = Runtime.getRuntime(),
) {
    fun sample(): MemorySample {
        val used = runtime.totalMemory() - runtime.freeMemory()
        return MemorySample(usedBytes = used, maxBytes = runtime.maxMemory())
    }
}
