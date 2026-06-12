package com.shuli.reader.core.reader

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.toLayoutConfig
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginatorMarginTest {

    private val density = 2.75f
    private val pageSize = PageSize(1080, 1920)

    // T-2.3.1: 四边距独立生效
    @Test
    fun fourMargins_independent() {
        val prefs = ReaderPreferences(
            marginTop = 60f,
            marginBottom = 30f,
            marginLeft = 40f,
            marginRight = 20f,
        )
        val config = prefs.toLayoutConfig(pageSize, density)

        assertEquals(60f * density, config.marginTop, 0.01f)
        assertEquals(30f * density, config.marginBottom, 0.01f)
        assertEquals(40f * density, config.marginLeft, 0.01f)
        assertEquals(20f * density, config.marginRight, 0.01f)
    }

    // T-2.3.2: 旧字段 fallback
    @Test
    fun marginVertical_fallback_whenMarginTopNull() {
        val prefs = ReaderPreferences(
            marginVertical = 48f,
            marginTop = null,
            marginBottom = null,
        )
        val config = prefs.toLayoutConfig(pageSize, density)

        assertEquals(48f * density, config.marginTop, 0.01f)
        assertEquals(48f * density, config.marginBottom, 0.01f)
    }

    @Test
    fun marginHorizontal_fallback_whenMarginLeftNull() {
        val prefs = ReaderPreferences(
            marginHorizontal = 24f,
            marginLeft = null,
            marginRight = null,
        )
        val config = prefs.toLayoutConfig(pageSize, density)

        assertEquals(24f * density, config.marginLeft, 0.01f)
        assertEquals(24f * density, config.marginRight, 0.01f)
    }

    // T-2.3.3: 独立边距优先于 fallback
    @Test
    fun independentMargin_overridesFallback() {
        val prefs = ReaderPreferences(
            marginVertical = 48f,
            marginTop = 60f,
            marginBottom = 30f,
        )
        val config = prefs.toLayoutConfig(pageSize, density)

        assertEquals(60f * density, config.marginTop, 0.01f)
        assertEquals(30f * density, config.marginBottom, 0.01f)
    }

    // T-2.3.4: LayoutHasher 使用实际值
    @Test
    fun marginHash_sameForExplicitAndFallback() {
        // marginTop=60 和 marginTop=null, marginVertical=60 应产生相同 hash
        // 因为 toLayoutConfig 解析后的值相同
        val prefs1 = ReaderPreferences(marginTop = 60f, marginVertical = 48f)
        val prefs2 = ReaderPreferences(marginTop = null, marginVertical = 60f)

        val config1 = prefs1.toLayoutConfig(pageSize, density)
        val config2 = prefs2.toLayoutConfig(pageSize, density)

        assertEquals(config1.marginTop, config2.marginTop, 0.01f)
    }

    // Paginator 使用四边距
    @Test
    fun paginator_usesFourMargins() {
        val measurer = SimpleTextMeasurer()
        val paginator = Paginator(measurer)
        val config = ReaderLayoutConfig(
            pageSize = pageSize,
            textSize = 44f,
            lineHeight = 1.5f,
            paragraphSpacing = 44f,
            marginTop = 60f * density,
            marginBottom = 30f * density,
            marginLeft = 40f * density,
            marginRight = 20f * density,
            indent = 88f,
            density = density,
        )

        val content = "测试内容。".repeat(100)
        val chapter = paginator.paginateChapter(0, "Test", content, config)
        assertTrue(chapter.pages.isNotEmpty())

        // 验证第一行的 Y 坐标在 marginTop 之后
        val firstLine = chapter.pages.first().lines.first()
        assertTrue(
            "firstLine.top=${firstLine.top} should be >= marginTop=${config.marginTop}",
            firstLine.top >= config.marginTop - 1f,
        )
    }
}
