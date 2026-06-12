package com.shuli.reader.core.reader.text

import com.shuli.reader.core.data.ChineseConvert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFilterProcessorTest {

    private fun context(adFiltering: Boolean) = ProcessingContext(
        adFiltering = adFiltering,
        regexRules = emptyList(),
        locale = "zh-CN",
        chineseConvert = ChineseConvert.NONE,
        usePanguSpacing = false,
    )

    // T-3.5.1: adFiltering = false 时透传
    @Test
    fun adFiltering_disabled_passthrough() {
        val processor = AdFilterProcessor()
        val input = "好书推荐 www.ad.com 欢迎阅读"
        val result = processor.process(input, context(false))
        assertEquals(input, result)
    }

    // T-3.5.2: 过滤 URL 广告
    @Test
    fun adFiltering_enabled_filtersUrl() {
        val processor = AdFilterProcessor()
        val result = processor.process(
            "好书推荐 www.ad.com 欢迎阅读",
            context(true),
        )
        assertFalse("URL should be filtered", result.contains("www.ad.com"))
        assertTrue("Content should remain", result.contains("好书推荐"))
        assertTrue("Content should remain", result.contains("欢迎阅读"))
    }

    @Test
    fun adFiltering_enabled_filtersHttpUrl() {
        val processor = AdFilterProcessor()
        val result = processor.process(
            "访问 http://example.com 了解更多",
            context(true),
        )
        assertFalse(result.contains("http://example.com"))
    }

    // T-3.5.3: 过滤中文广告关键词
    @Test
    fun adFiltering_enabled_filtersChineseKeywords() {
        val processor = AdFilterProcessor()
        val result = processor.process(
            "点击扫码关注获取优惠",
            context(true),
        )
        assertFalse("Ad keywords should be filtered", result.contains("扫码"))
        assertFalse("Ad keywords should be filtered", result.contains("关注"))
    }

    // 纯文本无广告时不变
    @Test
    fun adFiltering_enabled_noAdText_unchanged() {
        val processor = AdFilterProcessor()
        val input = "天地玄黄，宇宙洪荒。"
        val result = processor.process(input, context(true))
        assertEquals(input, result)
    }

    // order 值验证
    @Test
    fun order_defaultIs50() {
        val processor = AdFilterProcessor()
        assertEquals(50, processor.order)
    }
}
