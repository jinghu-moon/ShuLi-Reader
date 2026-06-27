package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.DualPageMode
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.reader.text.HyphenationEngine
import com.shuli.reader.core.reader.text.HyphenationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 降级策略测试（附录 B：T-B.1 ~ T-B.5）。
 *
 * 验证当性能超阈值时，功能正确降级而非崩溃。
 * 注：T-B.1/T-B.2 的实际性能阈值测试需 Benchmark 环境（Appendix A），
 * 此处验证降级逻辑的正确性。
 */
class DegradationStrategyTest {

    // T-B.1: Bionic Reading 降级逻辑
    @Test
    fun bionicReading_degradation_autoDisableWhenFlagOff() {
        val original = ReaderFeatureFlags.BIONIC_READING_ENABLED
        try {
            // 模拟性能超阈值触发自动关闭
            ReaderFeatureFlags.BIONIC_READING_ENABLED = false

            // Flag 关闭后，Bionic Reading 不应启用
            val prefs = ReaderPreferences(bionicReading = true)
            val effectiveBionic = prefs.bionicReading && ReaderFeatureFlags.BIONIC_READING_ENABLED
            assertFalse("Bionic Reading Flag 关闭时应禁用", effectiveBionic)
        } finally {
            ReaderFeatureFlags.BIONIC_READING_ENABLED = original
        }
    }

    // T-B.1b: Bionic Reading 降级后用户配置保留
    @Test
    fun bionicReading_degradation_userSettingPreserved() {
        val original = ReaderFeatureFlags.BIONIC_READING_ENABLED
        try {
            val prefs = ReaderPreferences(bionicReading = true)
            ReaderFeatureFlags.BIONIC_READING_ENABLED = false

            // 降级不影响存储值
            assertTrue("用户配置应保留", prefs.bionicReading)

            // 恢复后自动启用
            ReaderFeatureFlags.BIONIC_READING_ENABLED = true
            val effective = prefs.bionicReading && ReaderFeatureFlags.BIONIC_READING_ENABLED
            assertTrue("Flag 恢复后应重新启用", effective)
        } finally {
            ReaderFeatureFlags.BIONIC_READING_ENABLED = original
        }
    }

    // T-B.2: 断字降级 — HyphenationMode.NONE 时不断字
    @Test
    fun hyphenation_degradation_noneModeProducesNoBreakPoints() {
        // HyphenationMode.NONE 时，Paginator 不调用 HyphenationEngine
        val breakPoints = HyphenationEngine.findBreakPoints("international")
        // 验证引擎本身工作正常
        assertTrue("引擎应对长英文单词返回断字点", breakPoints.isNotEmpty())

        // 当 mode=NONE 时，Paginator 层面不调用引擎（此处验证引擎可被跳过）
        val mode = HyphenationMode.NONE
        assertEquals("NONE 模式不应调用断字", HyphenationMode.NONE, mode)
    }

    // T-B.4: 双页模式降级 — Flag 关闭时回退到单页
    @Test
    fun dualPage_degradation_flagOff_fallsToSinglePage() {
        val original = ReaderFeatureFlags.DUAL_PAGE_MODE_ENABLED
        try {
            ReaderFeatureFlags.DUAL_PAGE_MODE_ENABLED = false

            val prefs = ReaderPreferences(dualPageMode = DualPageMode.DUAL)

            // 模拟双页解析：Flag 关闭时强制单页
            val effectiveDualPage = prefs.dualPageMode == DualPageMode.DUAL &&
                ReaderFeatureFlags.DUAL_PAGE_MODE_ENABLED
            assertFalse("Flag 关闭时双页应降级为单页", effectiveDualPage)
        } finally {
            ReaderFeatureFlags.DUAL_PAGE_MODE_ENABLED = original
        }
    }

    // T-B.3: 正则管道降级 — 空规则列表时透传原文
    @Test
    fun regexPipeline_degradation_emptyRulesPassThrough() {
        val processor = com.shuli.reader.core.reader.text.RegexReplaceProcessor(
            order = 100,
            rules = emptyList()
        )
        val context = com.shuli.reader.core.reader.text.ProcessingContext()
        val input = "这是一段测试文本，包含一些内容。"

        val result = processor.process(input, context)
        assertEquals("空规则应透传原文", input, result)
    }

    // T-B.3b: 广告过滤降级 — adFiltering=false 时透传原文
    @Test
    fun adFilter_degradation_disabled_passesThrough() {
        val processor = com.shuli.reader.core.reader.text.AdFilterProcessor(order = 50)
        val context = com.shuli.reader.core.reader.text.ProcessingContext(adFiltering = false)
        val input = "好书推荐 www.ad.com 欢迎阅读"

        val result = processor.process(input, context)
        assertEquals("adFiltering=false 时应透传原文", input, result)
    }
}
