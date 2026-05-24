package com.shuli.reader.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 阅读器场景 Macrobenchmark。每个 @Test 对应一个独立场景，便于指标按场景归因。
 *
 * 运行命令：./gradlew.bat :benchmark:connectedDebugAndroidTest
 *
 * 前置条件：测试设备 cache 目录已存在 ~100MB UTF-8 TXT fixture，路径与 [TEST_FILE_PATH] 一致。
 * 生成方式参见 [com.shuli.reader.benchmark.FixtureGenerator]（在 :app androidTest 源集中按需创建）。
 */
@RunWith(AndroidJUnit4::class)
class ReaderPerformanceBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * T12.1 — 进入阅读首屏：测量从冷启动到 Reader Canvas 渲染完成的耗时。
     */
    @Test
    fun firstFrameAfterColdStart() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = { ensureFixtureImported() },
        ) {
            launchAndOpenReader()
            device.wait(
                Until.findObject(By.res(TARGET_PACKAGE_NAME, RESOURCE_READER_CANVAS)),
                CANVAS_READY_TIMEOUT_MS,
            )
        }
    }

    /**
     * T12.2 — 连续翻页 50× 双向：测帧时间稳定性。
     */
    @Test
    fun continuousPaging() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = {
                ensureFixtureImported()
                launchAndOpenReader()
                device.wait(
                    Until.findObject(By.res(TARGET_PACKAGE_NAME, RESOURCE_READER_CANVAS)),
                    CANVAS_READY_TIMEOUT_MS,
                )
            },
        ) {
            val width = device.displayWidth
            val height = device.displayHeight
            val rightX = (width * RIGHT_TAP_RATIO).toInt()
            val leftX = (width * LEFT_TAP_RATIO).toInt()
            val centerY = height / 2

            repeat(PAGE_TURNS) {
                device.click(rightX, centerY)
                Thread.sleep(PAGE_TURN_INTERVAL_MS)
            }
            repeat(PAGE_TURNS) {
                device.click(leftX, centerY)
                Thread.sleep(PAGE_TURN_INTERVAL_MS)
            }
        }
    }

    /**
     * T0.5 — 滚动目录：测目录抽屉滚动帧时间。
     */
    @Test
    fun directoryScroll() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = {
                ensureFixtureImported()
                launchAndOpenReader()
                device.wait(
                    Until.findObject(By.res(TARGET_PACKAGE_NAME, RESOURCE_READER_CANVAS)),
                    CANVAS_READY_TIMEOUT_MS,
                )
                // 唤起底部工具栏 → 打开目录抽屉
                device.click(device.displayWidth / 2, device.displayHeight / 2)
                Thread.sleep(TOOLBAR_REVEAL_DELAY_MS)
                device.wait(
                    Until.findObject(By.res(TARGET_PACKAGE_NAME, RESOURCE_DIRECTORY_BUTTON)),
                    TIMEOUT_MS,
                )?.click()
                device.wait(
                    Until.findObject(By.res(TARGET_PACKAGE_NAME, RESOURCE_CHAPTER_LIST)),
                    TIMEOUT_MS,
                )
            },
        ) {
            val list = device.findObject(By.res(TARGET_PACKAGE_NAME, RESOURCE_CHAPTER_LIST))
                ?: return@measureRepeated
            list.setGestureMargin(GESTURE_MARGIN_PX)
            repeat(SCROLL_REPEATS) {
                list.scroll(Direction.DOWN, SCROLL_PERCENT)
                Thread.sleep(SCROLL_INTERVAL_MS)
                list.scroll(Direction.UP, SCROLL_PERCENT)
                Thread.sleep(SCROLL_INTERVAL_MS)
            }
        }
    }

    /**
     * T12.3 — 100MB 元数据导入：测大文件导入耗时（解析 + 持久化）。
     */
    @Test
    fun largeFileImport() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = {
                killProcess()
                clearAppData()
            },
        ) {
            launchImportIntent()
            device.wait(Until.findObject(By.textContains(FIXTURE_TITLE_PART)), IMPORT_WAIT_TIMEOUT_MS)
        }
    }

    // ─── 共享 helpers ─────────────────────────────────────────────────

    private fun MacrobenchmarkScope.ensureFixtureImported() {
        launchImportIntent()
        Thread.sleep(IMPORT_SETTLE_DELAY_MS)
    }

    private fun MacrobenchmarkScope.launchImportIntent() {
        val intent = Intent().apply {
            action = Intent.ACTION_MAIN
            setClassName(TARGET_PACKAGE_NAME, ACTIVITY_CLASS_NAME)
            putExtra(EXTRA_TEST_FILE_PATH, TEST_FILE_PATH)
        }
        startActivityAndWait(intent)
    }

    private fun MacrobenchmarkScope.launchAndOpenReader() {
        launchImportIntent()
        device.wait(
            Until.findObject(By.textContains(FIXTURE_TITLE_PART)),
            IMPORT_WAIT_TIMEOUT_MS,
        )?.click()
    }

    private fun clearAppData() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm clear $TARGET_PACKAGE_NAME")
        Thread.sleep(CLEAR_DATA_DELAY_MS)
    }

    private companion object {
        private const val TARGET_PACKAGE_NAME = "com.shuli.reader.debug"
        private const val ACTIVITY_CLASS_NAME = "com.shuli.reader.MainActivity"
        private const val TEST_FILE_PATH = "/sdcard/Android/data/com.shuli.reader.debug/cache/test_100mb.txt"
        private const val EXTRA_TEST_FILE_PATH = "EXTRA_TEST_FILE_PATH"
        private const val FIXTURE_TITLE_PART = "test_100mb"

        private const val RESOURCE_READER_CANVAS = "reader_canvas"
        private const val RESOURCE_DIRECTORY_BUTTON = "reader_directory_button"
        private const val RESOURCE_CHAPTER_LIST = "directory_chapter_list"

        private const val ITERATIONS = 3
        private const val PAGE_TURNS = 50
        private const val PAGE_TURN_INTERVAL_MS = 100L
        private const val SCROLL_REPEATS = 5
        private const val SCROLL_PERCENT = 1.0f
        private const val SCROLL_INTERVAL_MS = 300L
        private const val GESTURE_MARGIN_PX = 100
        private const val TOOLBAR_REVEAL_DELAY_MS = 500L
        private const val CANVAS_READY_TIMEOUT_MS = 10_000L
        private const val IMPORT_WAIT_TIMEOUT_MS = 60_000L
        private const val IMPORT_SETTLE_DELAY_MS = 4_000L
        private const val CLEAR_DATA_DELAY_MS = 1_500L
        private const val TIMEOUT_MS = 5_000L

        private const val RIGHT_TAP_RATIO = 0.9
        private const val LEFT_TAP_RATIO = 0.1
    }
}
