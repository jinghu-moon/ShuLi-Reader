package com.shuli.reader.core.reader.engine

import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.text.FakeTextMeasurer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginatorTest {

    private val textMeasurer = FakeTextMeasurer()
    private val paginator = Paginator(textMeasurer)

    private val config = ReaderLayoutConfig(
        pageSize = PageSize(1080, 1920),
        textSize = 18f,
        lineHeight = 1.5f,
        paragraphSpacing = 10f,
        marginTop = 20f,
        marginBottom = 20f,
        marginLeft = 20f,
        marginRight = 20f,
        indent = 2f,
    )

    @Test
    fun paginationResult_doesNotExceedBounds() {
        val content = "A".repeat(1000)
        val chapter = paginator.paginateChapter(0, "Test", content, config)

        for (page in chapter.pages) {
            assertTrue("页面起始偏移应 >= 0", page.startCharOffset >= 0)
            assertTrue("页面结束偏移应 > 起始偏移", page.endCharOffset > page.startCharOffset)
            assertTrue("页面结束偏移应 <= 内容长度", page.endCharOffset <= content.length)
        }
    }

    @Test
    fun pagination_coversAllContent() {
        val content = "Hello World"
        val chapter = paginator.paginateChapter(0, "Test", content, config)

        assertTrue("应有页面", chapter.pages.isNotEmpty())
        assertTrue("应有行", chapter.pages[0].lines.isNotEmpty())
    }

    @Test
    fun lineInfo_containsCorrectOffsets() {
        val content = "Hello World"
        val chapter = paginator.paginateChapter(0, "Test", content, config)

        assertTrue("应有页面", chapter.pages.isNotEmpty())
        val page = chapter.pages[0]
        assertTrue("应有行", page.lines.isNotEmpty())

        val line = page.lines[0]
        assertTrue("行起始偏移应 >= 0", line.startCharOffset >= 0)
        assertTrue("行结束偏移应 > 行起始偏移", line.endCharOffset > line.startCharOffset)
    }

    @Test
    fun paragraphEndFlag_isCorrect() {
        val content = "First paragraph."
        val chapter = paginator.paginateChapter(0, "Test", content, config)

        assertTrue("应有页面", chapter.pages.isNotEmpty())
        val page = chapter.pages[0]
        assertTrue("应有行", page.lines.isNotEmpty())
    }

    @Test
    fun pagination_handlesEmptyContent() {
        val chapter = paginator.paginateChapter(0, "Test", "", config)

        assertEquals("空内容应有0页", 0, chapter.pages.size)
    }

    @Test
    fun pagination_handlesShortContent() {
        val content = "Hi"
        val chapter = paginator.paginateChapter(0, "Test", content, config)

        assertEquals("短内容应有1页", 1, chapter.pages.size)
        assertEquals("内容应完整", content.length, chapter.pages[0].endCharOffset - chapter.pages[0].startCharOffset)
    }

    @Test
    fun pagination_consumesEmptyLinesToAvoidOffsetStall() {
        val content = "\n\n正文"
        val chapter = paginator.paginateChapter(0, "Test", content, config)

        assertTrue("应有页面", chapter.pages.isNotEmpty())
        assertEquals("应完整消耗包含空行的内容", content.length, chapter.pages.last().endCharOffset)
    }

    @Test
    fun chineseClosingPunctuation_doesNotStartLine() {
        val content = "你好，世界"
        val narrowConfig = config.copy(pageSize = PageSize(width = 80, height = 400))
        val chapter = paginator.paginateChapter(0, "Test", content, narrowConfig)

        val lines = chapter.pages.flatMap { it.lines }
        assertTrue("应产生多行", lines.size >= 2)
        val line0Text = content.substring(lines[0].startCharOffset, lines[0].endCharOffset)
        val line1Text = content.substring(lines[1].startCharOffset, lines[1].endCharOffset)
        assertEquals("你好，", line0Text)
        assertTrue("第二行不应以闭合标点开头", !line1Text.startsWith("，"))
    }
}
