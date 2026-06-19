package com.shuli.reader.core.reader.model

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TextChapterTest {

    private fun createPage(chapterIndex: Int, pageIndex: Int, start: Int, end: Int): TextPage {
        return TextPage(
            startCharOffset = start,
            endCharOffset = end,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            lines = emptyList(),
            layout = PageLayout(null, null, BoxBounds(24f, 0f, 1056f, 1920f), null, 1080f, 1920f),
        )
    }

    @Test
    fun initialState_isNotCompleted() {
        val chapter = TextChapter(0, "Test", "content")
        assertFalse(chapter.isCompleted)
        assertEquals(0, chapter.pageSize)
    }

    @Test
    fun addPage_increasesPageSize() {
        val chapter = TextChapter(0, "Test", "content")
        chapter.addPage(createPage(0, 0, 0, 100))
        assertEquals(1, chapter.pageSize)

        chapter.addPage(createPage(0, 1, 100, 200))
        assertEquals(2, chapter.pageSize)
    }

    @Test
    fun markCompleted_setsIsCompleted() {
        val chapter = TextChapter(0, "Test", "content")
        assertFalse(chapter.isCompleted)

        chapter.markCompleted()
        assertTrue(chapter.isCompleted)
    }

    @Test
    fun getPageIndexByCharIndex_returnsCorrectIndex() {
        val chapter = TextChapter(0, "Test", "content")
        chapter.addPage(createPage(0, 0, 0, 100))
        chapter.addPage(createPage(0, 1, 100, 200))
        chapter.addPage(createPage(0, 2, 200, 300))

        assertEquals(0, chapter.getPageIndexByCharIndex(50))
        assertEquals(1, chapter.getPageIndexByCharIndex(150))
        assertEquals(2, chapter.getPageIndexByCharIndex(250))
    }

    @Test
    fun getPageIndexByCharIndex_handlesBoundaries() {
        val chapter = TextChapter(0, "Test", "content")
        chapter.addPage(createPage(0, 0, 0, 100))
        chapter.addPage(createPage(0, 1, 100, 200))

        // 边界值
        assertEquals(0, chapter.getPageIndexByCharIndex(0))
        assertEquals(0, chapter.getPageIndexByCharIndex(99))
        assertEquals(1, chapter.getPageIndexByCharIndex(100))
        assertEquals(1, chapter.getPageIndexByCharIndex(199))
    }

    @Test
    fun getPageIndexByCharIndex_clampsOutOfBounds() {
        val chapter = TextChapter(0, "Test", "content")
        chapter.addPage(createPage(0, 0, 0, 100))
        chapter.addPage(createPage(0, 1, 100, 200))

        assertEquals(0, chapter.getPageIndexByCharIndex(-1))
        assertEquals(1, chapter.getPageIndexByCharIndex(300))
    }

    @Test
    fun layoutListener_receivesEvents() {
        val chapter = TextChapter(0, "Test", "content")
        val events = mutableListOf<String>()

        chapter.layoutListener = object : TextChapter.LayoutListener {
            override fun onPageReady(index: Int, page: TextPage) {
                events.add("page_$index")
            }
            override fun onLayoutCompleted() {
                events.add("completed")
            }
        }

        chapter.addPage(createPage(0, 0, 0, 100))
        chapter.addPage(createPage(0, 1, 100, 200))
        chapter.markCompleted()

        assertEquals(listOf("page_0", "page_1", "completed"), events)
    }

    @Test
    fun concurrentAddPage_isThreadSafe() {
        val chapter = TextChapter(0, "Test", "x".repeat(10000))
        val threadCount = 10
        val pagesPerThread = 100
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (p in 0 until pagesPerThread) {
                        val index = t * pagesPerThread + p
                        chapter.addPage(createPage(0, index, index * 100, (index + 1) * 100))
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(threadCount * pagesPerThread, chapter.pageSize)
    }

    @Test
    fun constructorWithPages_initializesCorrectly() {
        val pages = listOf(
            createPage(0, 0, 0, 100),
            createPage(0, 1, 100, 200),
        )
        val chapter = TextChapter(0, "Test", "content", pages)

        assertEquals(2, chapter.pageSize)
        assertEquals(0, chapter.getPageIndexByCharIndex(50))
        assertEquals(1, chapter.getPageIndexByCharIndex(150))
    }
}
