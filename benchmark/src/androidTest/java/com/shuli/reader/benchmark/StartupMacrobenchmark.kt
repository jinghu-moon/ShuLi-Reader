package com.shuli.reader.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 可连接设备执行的启动性能基准。
 * 运行: ./gradlew.bat :benchmark:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class StartupMacrobenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        measureStartup(StartupMode.COLD)
    }

    @Test
    fun warmStartup() {
        measureStartup(StartupMode.WARM)
    }

    @Test
    fun hotStartup() {
        measureStartup(StartupMode.HOT)
    }

    private fun measureStartup(startupMode: StartupMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = STARTUP_ITERATIONS,
            startupMode = startupMode,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    private companion object {
        private const val TARGET_PACKAGE_NAME = "com.shuli.reader.debug"
        private const val STARTUP_ITERATIONS = 5
    }
}
