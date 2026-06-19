package com.shuli.reader.core.reader.engine

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.toLayoutConfig
import com.shuli.reader.core.reader.model.BoxInsetsDp
import com.shuli.reader.core.reader.model.BoxInsetsPx
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.text.SimpleTextMeasurer
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
            bodyBox = BoxInsetsDp(top = 60f, bottom = 30f, left = 40f, right = 20f),
        )
        val config = prefs.toLayoutConfig(pageSize, density)

        assertEquals(60f * density, config.bodyInsets.top, 0.01f)
        assertEquals(30f * density, config.bodyInsets.bottom, 0.01f)
        assertEquals(40f * density, config.bodyInsets.left, 0.01f)
        assertEquals(20f * density, config.bodyInsets.right, 0.01f)
    }

    // T-2.3.2: 等值 top/bottom 产生等值 bodyInsets
    @Test
    fun bodyBox_equalTopBottom_producesEqualInsets() {
        val prefs = ReaderPreferences(
            bodyBox = BoxInsetsDp(top = 48f, bottom = 48f),
        )
        val config = prefs.toLayoutConfig(pageSize, density)

        assertEquals(config.bodyInsets.top, config.bodyInsets.bottom, 0.01f)
    }

    // T-2.3.3: 等值 left/right 产生等值 bodyInsets
    @Test
    fun bodyBox_equalLeftRight_producesEqualInsets() {
        val prefs = ReaderPreferences(
            bodyBox = BoxInsetsDp(left = 24f, right = 24f),
        )
        val config = prefs.toLayoutConfig(pageSize, density)

        assertEquals(config.bodyInsets.left, config.bodyInsets.right, 0.01f)
    }

    // T-2.3.4: 不同 top/bottom 产生不同 bodyInsets
    @Test
    fun bodyBox_differentTopBottom_producesDifferentInsets() {
        val prefs = ReaderPreferences(
            bodyBox = BoxInsetsDp(top = 60f, bottom = 30f),
        )
        val config = prefs.toLayoutConfig(pageSize, density)

        assertEquals(60f * density, config.bodyInsets.top, 0.01f)
        assertEquals(30f * density, config.bodyInsets.bottom, 0.01f)
        assertTrue(config.bodyInsets.top != config.bodyInsets.bottom)
    }

    // T-2.3.5: 相同 bodyBox 产生相同 bodyInsets
    @Test
    fun bodyBox_sameValues_producesSameInsets() {
        val prefs1 = ReaderPreferences(bodyBox = BoxInsetsDp(top = 60f, bottom = 48f, left = 24f, right = 24f))
        val prefs2 = ReaderPreferences(bodyBox = BoxInsetsDp(top = 60f, bottom = 48f, left = 24f, right = 24f))

        val config1 = prefs1.toLayoutConfig(pageSize, density)
        val config2 = prefs2.toLayoutConfig(pageSize, density)

        assertEquals(config1.bodyInsets.top, config2.bodyInsets.top, 0.01f)
        assertEquals(config1.bodyInsets.bottom, config2.bodyInsets.bottom, 0.01f)
        assertEquals(config1.bodyInsets.left, config2.bodyInsets.left, 0.01f)
        assertEquals(config1.bodyInsets.right, config2.bodyInsets.right, 0.01f)
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
            bodyInsets = BoxInsetsPx(top = 60f * density, bottom = 30f * density, left = 40f * density, right = 20f * density),
            indent = 88f,
            density = density,
        )

        val content = "测试内容。".repeat(100)
        val chapter = paginator.paginateChapter(0, "Test", content, config)
        assertTrue(chapter.pages.isNotEmpty())

        // 验证第一行的 Y 坐标在 bodyInsets.top 之后
        val firstLine = chapter.pages.first().lines.first()
        assertTrue(
            "firstLine.top=${firstLine.top} should be >= bodyInsets.top=${config.bodyInsets.top}",
            firstLine.top >= config.bodyInsets.top - 1f,
        )
    }
}
