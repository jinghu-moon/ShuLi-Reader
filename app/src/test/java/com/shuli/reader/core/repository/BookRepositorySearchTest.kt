package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookChapterDao
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.ReadingProgressDao
import com.shuli.reader.core.database.entity.BookContentIndexEntity
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.TxtParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookRepositorySearchTest {

    @Test
    fun searchInBook_returnsChapterPositionsAndContext() = runTest {
        val file = File.createTempFile("search_chapter", ".txt").apply {
            writeText(
                """
                第一章 开始
                星海在窗外展开，主角第一次看见远方。
                第二章 归途
                归途中再次提到星海，但这一章不应丢失章节定位。
                """.trimIndent(),
            )
            deleteOnExit()
        }
        val bookDao = mockk<BookDao>()
        every { bookDao.getBookById(1L) } returns flowOf(book(file))
        coEvery { bookDao.countBookContentIndex(1L) } returns 0
        coEvery { bookDao.replaceBookContentIndex(eq(1L), any()) } just runs
        val repository = repository(bookDao)

        val results = repository.searchInBook(1L, "星海")

        assertEquals(2, results.size)
        assertEquals(0, results[0].chapterIndex)
        assertEquals(1, results[1].chapterIndex)
        assertEquals("星海", results[0].matchedText)
        assertTrue("摘要不应携带大段正文", results.all { it.context.length <= 42 })
        assertTrue("匹配位置应为正", results.all { it.byteOffset >= 0 })
    }

    @Test
    fun searchInBook_withBlankQuery_doesNotReadDatabase() = runTest {
        val bookDao = mockk<BookDao>(relaxed = true)
        val repository = repository(bookDao)

        val results = repository.searchInBook(1L, "   ")

        assertTrue(results.isEmpty())
        verify(exactly = 0) { bookDao.getBookById(any()) }
    }

    @Test
    fun searchInBook_withExistingIndex_usesContentIndexWithoutOpeningBook() = runTest {
        val bookDao = mockk<BookDao>()
        coEvery { bookDao.countBookContentIndex(1L) } returns 1
        coEvery { bookDao.searchBookContentIndex(1L, "星海") } returns listOf(
            BookContentIndexEntity(
                bookId = 1L,
                chapterIndex = 2,
                chapterTitle = "Indexed Chapter",
                byteStart = 100L,
                charset = "UTF-8",
                content = "远方的星海正在发亮。",
            ),
        )
        val repository = repository(bookDao)

        val results = repository.searchInBook(1L, "星海")

        assertEquals(1, results.size)
        assertEquals(2, results.first().chapterIndex)
        assertEquals(103L, results.first().byteOffset)
        verify(exactly = 0) { bookDao.getBookById(any()) }
    }

    @Test
    fun deleteBook_removesContentIndexBeforeBookRow() = runTest {
        val bookDao = mockk<BookDao>(relaxed = true)
        val repository = repository(bookDao)

        repository.deleteBook(1L)

        coVerify { bookDao.deleteBookContentIndex(1L) }
        coVerify { bookDao.deleteBookById(1L) }
    }

    private fun repository(bookDao: BookDao): BookRepository {
        return BookRepository(
            bookDao = bookDao,
            bookChapterDao = mockk<BookChapterDao>(relaxed = true),
            readingProgressDao = mockk<ReadingProgressDao>(relaxed = true),
            txtParser = TxtParser(),
            epubParser = EpubParser(),
            byteWindowReader = com.shuli.reader.core.parser.ByteWindowReader(),
            booksDir = File(requireNotNull(System.getProperty("java.io.tmpdir"))),
        )
    }

    private fun book(file: File): BookEntity {
        return BookEntity(
            id = 1L,
            title = "Search Book",
            author = null,
            filePath = file.absolutePath,
            fileType = "TXT",
            fileSize = file.length(),
            coverPath = null,
            lastReadTime = null,
            addedTime = System.currentTimeMillis(),
        )
    }
}
