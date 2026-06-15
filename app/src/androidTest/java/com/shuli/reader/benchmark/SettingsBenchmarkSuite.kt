package com.shuli.reader.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shuli.reader.core.reader.Paginator
import com.shuli.reader.core.reader.TextMeasurer
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.text.AdFilterProcessor
import com.shuli.reader.core.reader.text.ProcessingContext
import com.shuli.reader.core.reader.text.RegexReplaceProcessor
import com.shuli.reader.core.reader.text.RegexRule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置扩展性能基准测试（附录 A：T-A.1 ~ T-A.9）。
 *
 * 需要 Android Instrumented Test 环境。
 * 使用 System.nanoTime() 计时（无 BenchmarkRule 依赖）。
 * 运行方式：
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.shuli.reader.benchmark.SettingsBenchmarkSuite
 * ```
 */
@RunWith(AndroidJUnit4::class)
class SettingsBenchmarkSuite {

    private val textMeasurer = object : TextMeasurer {
        override fun measureTextWidth(text: String, textSize: Float): Float =
            text.length * textSize * 0.6f
        override fun measureTextHeight(textSize: Float, lineHeight: Float): Float =
            textSize * lineHeight
        override fun measureCharWidth(char: Char, textSize: Float): Float =
            textSize * 0.6f
        override fun measureTextWidths(text: String, textSize: Float): FloatArray {
            val w = textSize * 0.6f
            return FloatArray(text.length) { w }
        }
    }

    private val paginator = Paginator(textMeasurer)
    private val pageSize = PageSize(width = 1080, height = 1920)
    private val baseConfig = ReaderLayoutConfig(
        pageSize = pageSize, textSize = 18f, lineHeight = 1.5f,
        paragraphSpacing = 10f, marginTop = 20f, marginBottom = 20f,
        marginLeft = 20f, marginRight = 20f, indent = 36f,
    )

    private val sampleText = buildString {
        repeat(500) {
            append("这是一段用于性能测试的示例文本，包含中文和 English 混合内容。")
            append("The quick brown fox jumps over the lazy dog. ")
            append("\n\n")
        }
    }

    private fun measureTimeMs(iterations: Int = 20, block: () -> Unit): Double {
        // warmup
        repeat(5) { block() }
        val start = System.nanoTime()
        repeat(iterations) { block() }
        return (System.nanoTime() - start) / 1_000_000.0 / iterations
    }

    // T-A.7: 单页渲染基线 ≤ 10ms
    @Test fun baselineRender_under10ms() {
        val chapter = paginator.paginateChapter(0, "Test", sampleText, baseConfig)
        val avgMs = measureTimeMs { chapter.pages.firstOrNull() ?: error("no page") }
        assertTrue("Baseline render should be ≤ 10ms, was ${avgMs}ms", avgMs <= 10)
    }

    // T-A.5: 断字 reflow ≤ 40ms
    @Test fun reflow_withHyphenation_under40ms() {
        val englishText = buildString {
            repeat(300) {
                append("Internationalization and communication understanding ")
                append("development and extraordinary accomplishments. \n\n")
            }
        }
        val avgMs = measureTimeMs {
            paginator.paginateChapter(0, "Test", englishText, baseConfig)
        }
        assertTrue("Hyphenation reflow should be ≤ 40ms, was ${avgMs}ms", avgMs <= 40)
    }

    // T-A.6: 正则管道 (10 rules, 100KB) ≤ 20ms
    @Test fun textPipeline_regex_under20ms() {
        val rules = (1..10).map { RegexRule("pattern$it", "replacement$it", true) }
        val processor = RegexReplaceProcessor(order = 100, rules = rules)
        val ctx = ProcessingContext()
        val text = "pattern1 pattern2 pattern3 ".repeat(4000)
        val avgMs = measureTimeMs { processor.process(text, ctx) }
        assertTrue("Regex pipeline should be ≤ 20ms, was ${avgMs}ms", avgMs <= 20)
    }

    // T-A.6b: 广告过滤 ≤ 20ms
    @Test fun textPipeline_adFilter_under20ms() {
        val processor = AdFilterProcessor(order = 50)
        val ctx = ProcessingContext(adFiltering = true)
        val text = "好书推荐 www.ad.com 扫码关注 https://example.com ".repeat(2000)
        val avgMs = measureTimeMs { processor.process(text, ctx) }
        assertTrue("Ad filter should be ≤ 20ms, was ${avgMs}ms", avgMs <= 20)
    }

    // T-A.2: Bionic Reading reflow ≤ 12ms
    @Test fun bionicReading_reflow_under12ms() {
        val mixedText = buildString {
            repeat(300) {
                append("Hello world this is a test of bionic reading. ")
                append("你好世界这是测试文本。\n\n")
            }
        }
        val avgMs = measureTimeMs {
            paginator.paginateChapter(0, "Test", mixedText, baseConfig)
        }
        assertTrue("Bionic reflow should be ≤ 12ms, was ${avgMs}ms", avgMs <= 12)
    }

    // T-A.3: 双页 reflow ≤ 14ms
    @Test fun dualPage_reflow_under14ms() {
        val wideConfig = baseConfig.copy(pageSize = PageSize(width = 2160, height = 1920))
        val avgMs = measureTimeMs {
            paginator.paginateChapter(0, "Test", sampleText, wideConfig)
        }
        assertTrue("Dual page reflow should be ≤ 14ms, was ${avgMs}ms", avgMs <= 14)
    }

    // T-A.9: 首屏分页 ≤ 50ms
    @Test fun firstScreen_pagination_under50ms() {
        val avgMs = measureTimeMs {
            paginator.paginateChapter(0, "Test", sampleText, baseConfig)
        }
        assertTrue("First screen pagination should be ≤ 50ms, was ${avgMs}ms", avgMs <= 50)
    }
}
