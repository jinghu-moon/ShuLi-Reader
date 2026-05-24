package com.shuli.reader.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtParserChapterDetectionTest {

    private val parser = TxtParser()

    // ── 前导空白和短标题测试 ──────────────────────────────────────

    @Test
    fun chapterTitleWithLeadingWhitespace_isDetected() {
        val content = """
            |   第一章 测试标题
            |这是内容
            |   第二章 另一个标题
            |更多内容
        """.trimMargin()
        val chapters = parser.detectChapters(content)
        assertEquals("应检测到2个章节", 2, chapters.size)
        assertEquals("第一章 测试标题", chapters[0].title)
        assertEquals("第二章 另一个标题", chapters[1].title)
    }

    @Test
    fun shortChapterTitle_isDetected() {
        val content = """
            |第一章
            |这是内容
            |第二章
            |更多内容
        """.trimMargin()
        val chapters = parser.detectChapters(content)
        assertEquals("应检测到2个章节", 2, chapters.size)
        assertEquals("第一章", chapters[0].title)
        assertEquals("第二章", chapters[1].title)
    }

    // ── 避免误判测试 ──────────────────────────────────────────────

    @Test
    fun numericLineInBody_isNotMisdetectedAsChapter() {
        val content = """
            |第一章 开始
            |这是内容
            |1234567890
            |这是另一行内容
            |2024年1月1日
            |第二章 继续
            |更多内容
        """.trimMargin()
        val chapters = parser.detectChapters(content)
        assertEquals("应只检测到2个章节", 2, chapters.size)
        assertEquals("第一章 开始", chapters[0].title)
        assertEquals("第二章 继续", chapters[1].title)
    }

    @Test
    fun numericTitleWithPunctuation_isDetected() {
        val content = """
            |1. 开始
            |这是内容
            |2. 继续
            |更多内容
            |3、第三部分
            |最后内容
        """.trimMargin()
        val chapters = parser.detectChapters(content)
        assertEquals("应检测到3个章节", 3, chapters.size)
        assertEquals("1. 开始", chapters[0].title)
        assertEquals("2. 继续", chapters[1].title)
        assertEquals("3、第三部分", chapters[2].title)
    }

    // ── 更多模式测试 ──────────────────────────────────────────────

    @Test
    fun volumeAndCollectionTitles_areDetected() {
        val content = """
            |卷一 少年行
            |这是内容
            |卷二 风云起
            |更多内容
            |集一 开篇
            |最后内容
        """.trimMargin()
        val chapters = parser.detectChapters(content)
        assertEquals("应检测到3个章节", 3, chapters.size)
        assertEquals("卷一 少年行", chapters[0].title)
        assertEquals("卷二 风云起", chapters[1].title)
        assertEquals("集一 开篇", chapters[2].title)
    }

    @Test
    fun partAndSectionTitles_areDetected() {
        val content = """
            |部一 引子
            |这是内容
            |篇一 序章
            |更多内容
        """.trimMargin()
        val chapters = parser.detectChapters(content)
        assertEquals("应检测到2个章节", 2, chapters.size)
        assertEquals("部一 引子", chapters[0].title)
        assertEquals("篇一 序章", chapters[1].title)
    }

    // ── 边界情况测试 ──────────────────────────────────────────────

    @Test
    fun emptyContent_returnsEmptyList() {
        val chapters = parser.detectChapters("")
        assertTrue("空内容应返回空列表", chapters.isEmpty())
    }

    @Test
    fun whitespaceOnlyContent_returnsEmptyList() {
        val chapters = parser.detectChapters("   \n   \n   ")
        assertTrue("只有空白应返回空列表", chapters.isEmpty())
    }

    @Test
    fun contentWithoutChapters_generatesSingleChapter() {
        val content = "这是一段没有章节标题的连续文本内容。"
        val chapters = parser.detectChapters(content)
        assertEquals("无章节文件应自动生成单章节", 1, chapters.size)
        assertEquals("自动生成的章节标题应为默认值", "Full Text", chapters[0].title)
        assertEquals("章节开始位置应为0", 0, chapters[0].startIndex)
        assertEquals("章节结束位置应为文本长度", content.length, chapters[0].endIndex)
    }

    @Test
    fun chapterBoundaries_doNotOverlap() {
        val content = """
            |第一章 开始
            |这是内容
            |第二章 继续
            |更多内容
            |第三章 结束
            |最后内容
        """.trimMargin()
        val chapters = parser.detectChapters(content)
        assertEquals("应检测到3个章节", 3, chapters.size)

        for (i in 0 until chapters.size - 1) {
            assertTrue(
                "章节 ${chapters[i].title} 的结束偏移应小于下一章开始偏移",
                chapters[i].startIndex < chapters[i + 1].startIndex,
            )
        }
    }

    @Test
    fun finalChapterEndIndex_matchesTextLength() {
        val content = """
            |第一章 开始
            |这是内容
            |第二章 继续
            |更多内容
        """.trimMargin()
        val chapters = parser.detectChapters(content)
        assertEquals("应检测到2个章节", 2, chapters.size)
        assertEquals(
            "末章结束位置应等于文本长度",
            content.length,
            chapters.last().endIndex,
        )
    }
}
