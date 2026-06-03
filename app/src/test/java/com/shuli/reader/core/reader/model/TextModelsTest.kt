package com.shuli.reader.core.reader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextModelsTest {

    @Test
    fun textPage_containsCorrectOffsets() {
        val page = TextPage(
            startCharOffset = 0,
            endCharOffset = 100,
            chapterIndex = 0,
            pageIndex = 0,
            pageSize = PageSize(1080, 1920),
            marginHorizontal = 24f,
            lines = emptyList(),
            columns = emptyList(),
        )

        assertEquals(0, page.startCharOffset)
        assertEquals(100, page.endCharOffset)
        assertEquals(0, page.chapterIndex)
        assertEquals(0, page.pageIndex)
    }

    @Test
    fun textLine_containsCorrectCoordinates() {
        val line = TextLine(
            startCharOffset = 0,
            endCharOffset = 11,
            baseline = 100f,
            top = 80f,
            bottom = 120f,
            isParagraphEnd = false,
        )

        assertEquals(100f, line.baseline, 0.001f)
        assertEquals(80f, line.top, 0.001f)
        assertEquals(120f, line.bottom, 0.001f)
        assertEquals(false, line.isParagraphEnd)
        assertEquals(0, line.startCharOffset)
        assertEquals(11, line.endCharOffset)
    }

    @Test
    fun textColumn_supportsCharacterCoordinates() {
        val column = TextColumn(
            startCharOffset = 0,
            endCharOffset = 50,
            startLine = 0,
            endLine = 5,
        )

        assertEquals(0, column.startCharOffset)
        assertEquals(50, column.endCharOffset)
        assertEquals(0, column.startLine)
        assertEquals(5, column.endLine)
    }

    @Test
    fun textChapterGetPageIndexByCharIndex_returnsCorrectPageIndex() {
        val pages = listOf(
            TextPage(
                startCharOffset = 0,
                endCharOffset = 100,
                chapterIndex = 0,
                pageIndex = 0,
                pageSize = PageSize(1080, 1920),
                marginHorizontal = 24f,
                lines = emptyList(),
                columns = emptyList(),
            ),
            TextPage(
                startCharOffset = 100,
                endCharOffset = 200,
                chapterIndex = 0,
                pageIndex = 1,
                pageSize = PageSize(1080, 1920),
                marginHorizontal = 24f,
                lines = emptyList(),
                columns = emptyList(),
            ),
        )

        val chapter = TextChapter(
            chapterIndex = 0,
            title = "Test Chapter",
            content = "A".repeat(200),
            pages = pages,
        )

        assertEquals(0, chapter.getPageIndexByCharIndex(50))
        assertEquals(1, chapter.getPageIndexByCharIndex(150))
    }

    @Test
    fun textChapterGetPageIndexByCharIndex_handlesBoundaries() {
        val pages = listOf(
            TextPage(
                startCharOffset = 0,
                endCharOffset = 100,
                chapterIndex = 0,
                pageIndex = 0,
                pageSize = PageSize(1080, 1920),
                marginHorizontal = 24f,
                lines = emptyList(),
                columns = emptyList(),
            ),
        )

        val chapter = TextChapter(
            chapterIndex = 0,
            title = "Test Chapter",
            content = "A".repeat(100),
            pages = pages,
        )

        // 负数应返回第一页
        assertEquals(0, chapter.getPageIndexByCharIndex(-1))

        // 超出范围应返回最后一页
        assertEquals(0, chapter.getPageIndexByCharIndex(150))
    }

    @Test
    fun readerLayoutConfig_containsCorrectValues() {
        val config = ReaderLayoutConfig(
            pageSize = PageSize(1080, 1920),
            textSize = 18f,
            lineHeight = 1.5f,
            paragraphSpacing = 10f,
            marginHorizontal = 20f,
            marginVertical = 30f,
            indent = 2f,
        )

        assertEquals(1080, config.pageSize.width)
        assertEquals(1920, config.pageSize.height)
        assertEquals(18f, config.textSize, 0.001f)
        assertEquals(1.5f, config.lineHeight, 0.001f)
        assertEquals(10f, config.paragraphSpacing, 0.001f)
        assertEquals(20f, config.marginHorizontal, 0.001f)
        assertEquals(30f, config.marginVertical, 0.001f)
        assertEquals(2f, config.indent, 0.001f)
    }

    // T10.2 - 进度百分比测试

    @Test
    fun readProgressForNonFinalPage_isCappedAt99_9() {
        val pages = listOf(
            makePage(0, 100, 0),
            makePage(100, 200, 1),
        )
        val chapter = makeChapter(pages)
        // 非最后一页，进度应 <= 99.9%
        val progress = chapter.readProgress(99)
        assertTrue("非最后一页进度应 <= 0.999", progress <= 0.999f)
        assertTrue("进度应 > 0", progress > 0f)
    }

    @Test
    fun readProgressForLastPage_returns100Percent() {
        val pages = listOf(
            makePage(0, 100, 0),
            makePage(100, 200, 1),
        )
        val chapter = makeChapter(pages)
        val progress = chapter.readProgress(150)
        // 最后一页，150 在 100-200 之间 → (150-100)/(200-100) = 0.5, 但最后一页返回 1.0
        // 实际：pageIndex=1 是最后一页，所以返回 1.0
        assertEquals(1.0f, progress, 0.001f)
    }

    @Test
    fun readProgressForSinglePageChapter_returnsOne() {
        val pages = listOf(makePage(0, 100, 0))
        val chapter = makeChapter(pages)
        val progress = chapter.readProgress(50)
        assertEquals(1.0f, progress, 0.001f)
    }

    @Test
    fun readProgressAtFirstPageStart_returnsZero() {
        val pages = listOf(
            makePage(0, 100, 0),
            makePage(100, 200, 1),
        )
        val chapter = makeChapter(pages)
        val progress = chapter.readProgress(0)
        assertEquals(0f, progress, 0.001f)
    }

    private fun makePage(start: Int, end: Int, index: Int) = TextPage(
        startCharOffset = start,
        endCharOffset = end,
        chapterIndex = 0,
        pageIndex = index,
        pageSize = PageSize(1080, 1920),
        marginHorizontal = 24f,
        lines = emptyList(),
        columns = emptyList(),
    )

    private fun makeChapter(pages: List<TextPage>) = TextChapter(
        chapterIndex = 0,
        title = "Test",
        content = "A".repeat(pages.last().endCharOffset),
        pages = pages,
    )
}
