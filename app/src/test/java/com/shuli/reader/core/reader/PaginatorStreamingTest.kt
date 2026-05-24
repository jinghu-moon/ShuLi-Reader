package com.shuli.reader.core.reader

import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.TextChapter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PaginatorStreamingTest {

    private lateinit var paginator: Paginator
    private lateinit var config: ReaderLayoutConfig

    @Before
    fun setup() {
        val measurer = object : TextMeasurer {
            override fun measureTextWidth(text: String, textSize: Float): Float {
                return text.length * textSize * 0.5f
            }
            override fun measureCharWidth(char: Char, textSize: Float): Float {
                return textSize * 0.5f
            }
            override fun measureTextHeight(textSize: Float, lineHeight: Float): Float {
                return textSize * lineHeight
            }
        }
        paginator = Paginator(measurer)

        config = ReaderLayoutConfig(
            pageSize = PageSize(1080, 1920),
            textSize = 24f,
            lineHeight = 1.5f,
            paragraphSpacing = 12f,
            marginHorizontal = 24f,
            marginVertical = 48f,
            indent = 2f,
            density = 3f,
        )
    }

    @Test
    fun paginateStreaming_emitsPagesInOrder() = runTest {
        val content = "a".repeat(5000) // 足够长的内容
        val chapter = TextChapter(0, "Test", content)

        val pages = paginator.paginateStreaming(chapter, content, config).toList()

        // 验证页码顺序
        for (i in pages.indices) {
            assertEquals(i, pages[i].pageIndex)
        }
    }

    @Test
    fun paginateStreaming_charOffsetsAreMonotonic() = runTest {
        val content = "b".repeat(10000)
        val chapter = TextChapter(0, "Test", content)

        val pages = paginator.paginateStreaming(chapter, content, config).toList()

        // 验证 startCharOffset 单调递增
        for (i in 1 until pages.size) {
            assertTrue(
                "Page ${i} startCharOffset should be > previous endCharOffset",
                pages[i].startCharOffset >= pages[i - 1].endCharOffset
            )
        }

        // 验证最后一页覆盖到内容末尾
        assertEquals(content.length, pages.last().endCharOffset)
    }

    @Test
    fun paginateStreaming_addsPagesToChapter() = runTest {
        val content = "c".repeat(5000)
        val chapter = TextChapter(0, "Test", content)

        paginator.paginateStreaming(chapter, content, config).toList()

        // 验证章节包含所有页面
        assertTrue(chapter.pageSize > 0)
        assertEquals(chapter.pageSize, chapter.pages.size)
    }

    @Test
    fun paginateStreaming_handlesEmptyContent() = runTest {
        val content = ""
        val chapter = TextChapter(0, "Test", content)

        val pages = paginator.paginateStreaming(chapter, content, config).toList()

        assertEquals(0, pages.size)
        assertEquals(0, chapter.pageSize)
    }

    @Test
    fun paginateStreaming_handlesShortContent() = runTest {
        val content = "Hello, World!"
        val chapter = TextChapter(0, "Test", content)

        val pages = paginator.paginateStreaming(chapter, content, config).toList()

        assertTrue(pages.isNotEmpty())
        assertEquals(0, pages.first().startCharOffset)
        assertEquals(content.length, pages.last().endCharOffset)
    }

    @Test
    fun paginateStreaming_sameAsNonStreaming() = runTest {
        val content = "d".repeat(20000)
        val chapter1 = TextChapter(0, "Test", content)
        val chapter2 = TextChapter(0, "Test", content)

        // 非流式
        val nonStreaming = paginator.paginateChapter(0, "Test", content, config)

        // 流式
        val streamingPages = paginator.paginateStreaming(chapter2, content, config).toList()

        // 验证页数相同
        assertEquals(nonStreaming.pages.size, streamingPages.size)
        assertEquals(nonStreaming.pages.size, chapter2.pageSize)

        // 验证每页的起止位置相同
        for (i in nonStreaming.pages.indices) {
            assertEquals(nonStreaming.pages[i].startCharOffset, streamingPages[i].startCharOffset)
            assertEquals(nonStreaming.pages[i].endCharOffset, streamingPages[i].endCharOffset)
        }
    }
}
