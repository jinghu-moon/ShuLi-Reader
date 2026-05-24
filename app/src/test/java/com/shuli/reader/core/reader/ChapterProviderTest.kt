package com.shuli.reader.core.reader

import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.TextChapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterProviderTest {

    private val textMeasurer = FakeTextMeasurer()
    private val paginator = Paginator(textMeasurer)
    private val chapterProvider = ChapterProvider(paginator)

    private val config = ReaderLayoutConfig(
        pageSize = PageSize(1080, 1920),
        textSize = 18f,
        lineHeight = 1.5f,
        paragraphSpacing = 10f,
        marginHorizontal = 20f,
        marginVertical = 20f,
        indent = 2f,
    )

    @Test
    fun loadChapter_returnsCorrectChapter() = runTest {
        val chapters = listOf(
            "Chapter 1" to "Content of chapter 1",
            "Chapter 2" to "Content of chapter 2",
            "Chapter 3" to "Content of chapter 3",
        )

        var loadedChapter: TextChapter? = null
        chapterProvider.loadChapter(1, chapters, config) { chapter ->
            loadedChapter = chapter
        }

        assertEquals("应加载第2章", 1, loadedChapter?.chapterIndex)
        assertEquals("标题应正确", "Chapter 2", loadedChapter?.title)
    }

    @Test
    fun loadChapter_handlesBoundaryCases() = runTest {
        val chapters = listOf(
            "Chapter 1" to "Content of chapter 1",
        )

        var loadedChapter: TextChapter? = null
        chapterProvider.loadChapter(0, chapters, config) { chapter ->
            loadedChapter = chapter
        }

        assertEquals("应加载第1章", 0, loadedChapter?.chapterIndex)
    }

    @Test
    fun loadChapter_cancelsPreviousTask() = runTest {
        val chapters = listOf(
            "Chapter 1" to "Content of chapter 1",
            "Chapter 2" to "Content of chapter 2",
        )

        var loadedChapter: TextChapter? = null

        // 第一次加载
        chapterProvider.loadChapter(0, chapters, config) { chapter ->
            loadedChapter = chapter
        }

        // 第二次加载
        chapterProvider.loadChapter(1, chapters, config) { chapter ->
            loadedChapter = chapter
        }

        assertEquals("应加载最后一章", 1, loadedChapter?.chapterIndex)
    }
}
