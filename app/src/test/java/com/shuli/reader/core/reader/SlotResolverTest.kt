package com.shuli.reader.core.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class SlotResolverTest {

    // --- resolve() ---

    @Test
    fun resolve_none_returnsEmpty() {
        assertEquals("", SlotResolver.resolve(SlotContent.NONE))
    }

    @Test
    fun resolve_chapterTitle_returnsTitle() {
        assertEquals("第一章 开始", SlotResolver.resolve(SlotContent.CHAPTER_TITLE, chapterTitle = "第一章 开始"))
    }

    @Test
    fun resolve_bookTitle_returnsTitle() {
        assertEquals("测试书籍", SlotResolver.resolve(SlotContent.BOOK_TITLE, bookTitle = "测试书籍"))
    }

    @Test
    fun resolve_pageNumber_returnsFormattedString() {
        assertEquals("3/100", SlotResolver.resolve(SlotContent.CHAPTER_PROGRESS_FRACTION, pageNumber = 3, totalPages = 100))
    }

    @Test
    fun resolve_progress_returnsPercentage() {
        assertEquals("45.0%", SlotResolver.resolve(SlotContent.BOOK_PROGRESS_PERCENT, bookProgressPercent = 0.45f))
    }

    @Test
    fun resolve_battery_returnsPercentage() {
        assertEquals("85%", SlotResolver.resolve(SlotContent.BATTERY, batteryLevel = 85))
    }

    @Test
    fun resolve_time_returnsNonEmpty() {
        val result = SlotResolver.resolve(SlotContent.TIME)
        assert(result.matches(Regex("\\d{2}:\\d{2}"))) { "TIME 应匹配 HH:mm 格式，实际: $result" }
    }

    @Test
    fun resolve_date_returnsNonEmpty() {
        val result = SlotResolver.resolve(SlotContent.DATE)
        assert(result.matches(Regex("\\d{2}-\\d{2}"))) { "DATE 应匹配 MM-dd 格式，实际: $result" }
    }

    // --- resolveHeader() ---

    @Test
    fun resolveHeader_defaultConfig_resolvesCorrectly() {
        val config = HeaderConfig(
            left = SlotContent.CHAPTER_TITLE,
            center = SlotContent.NONE,
            right = SlotContent.NONE,
        )
        val resolution = SlotResolver.resolveHeader(config, chapterTitle = "测试章节")

        assertEquals("测试章节", resolution.left)
        assertEquals("", resolution.center)
        assertEquals("", resolution.right)
    }

    @Test
    fun resolveHeader_allSlotsPopulated() {
        val config = HeaderConfig(
            left = SlotContent.BOOK_TITLE,
            center = SlotContent.CHAPTER_TITLE,
            right = SlotContent.CHAPTER_PROGRESS_FRACTION,
        )
        val resolution = SlotResolver.resolveHeader(
            config,
            chapterTitle = "第一章",
            bookTitle = "我的书",
            pageNumber = 5,
            totalPages = 200,
        )

        assertEquals("我的书", resolution.left)
        assertEquals("第一章", resolution.center)
        assertEquals("5/200", resolution.right)
    }

    // --- resolveFooter() ---

    @Test
    fun resolveFooter_defaultConfig_resolvesCorrectly() {
        val config = FooterConfig()
        val resolution = SlotResolver.resolveFooter(
            config,
            pageNumber = 10,
            totalPages = 50,
            bookProgressPercent = 0.2f,
        )

        assertEquals("20.0%", resolution.left)
        assertEquals("10/50", resolution.center)
        // right is TIME, just check non-empty
        assert(resolution.right.isNotEmpty()) { "页脚右侧时间不应为空" }
    }

    @Test
    fun resolveFooter_allNone_returnsEmptyStrings() {
        val config = FooterConfig(
            left = SlotContent.NONE,
            center = SlotContent.NONE,
            right = SlotContent.NONE,
        )
        val resolution = SlotResolver.resolveFooter(config)

        assertEquals("", resolution.left)
        assertEquals("", resolution.center)
        assertEquals("", resolution.right)
    }

    @Test
    fun resolve_progress_zeroPercent() {
        assertEquals("0.0%", SlotResolver.resolve(SlotContent.BOOK_PROGRESS_PERCENT, bookProgressPercent = 0f))
    }

    @Test
    fun resolve_progress_hundredPercent() {
        assertEquals("100.0%", SlotResolver.resolve(SlotContent.BOOK_PROGRESS_PERCENT, bookProgressPercent = 1f))
    }

    @Test
    fun resolve_pageNumber_zeroPage() {
        assertEquals("0/0", SlotResolver.resolve(SlotContent.CHAPTER_PROGRESS_FRACTION, pageNumber = 0, totalPages = 0))
    }
}
