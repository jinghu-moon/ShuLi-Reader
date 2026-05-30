package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookChapterDao
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.ReadingProgressDao
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.parser.ByteWindowReader
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.TxtParser
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// Part of T-02 bookKey generation in importBook()
class BookRepositoryBookKeyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var bookDao: BookDao
    private lateinit var bookChapterDao: BookChapterDao
    private lateinit var readingProgressDao: ReadingProgressDao
    private lateinit var repository: BookRepository

    @Before
    fun setup() {
        bookDao = mockk(relaxed = true)
        bookChapterDao = mockk(relaxed = true)
        readingProgressDao = mockk(relaxed = true)

        // Mock insertBook to return a bookId
        coEvery { bookDao.insertBook(any()) } returns 1L

        // Mock duplicate checker methods to return no duplicates
        coEvery { bookDao.getBookByFilePath(any()) } returns null
        coEvery { bookDao.getBooksByFileNameAndSize(any(), any()) } returns emptyList()

        repository = BookRepository(
            bookDao = bookDao,
            bookChapterDao = bookChapterDao,
            readingProgressDao = readingProgressDao,
            txtParser = TxtParser(),
            epubParser = EpubParser(),
            byteWindowReader = ByteWindowReader(),
            booksDir = tempFolder.newFolder("books"),
        )
    }

    @Test
    fun `importBook assigns non-empty bookKey immediately`() = runTest {
        val fakeFile = File(tempFolder.root, "book.txt").also { it.writeText("content") }

        repository.importBook(fakeFile, copyToAppDir = false)

        val entities = mutableListOf<BookEntity>()
        coVerify { bookDao.insertBook(capture(entities)) }

        assertTrue("bookKey should not be empty", entities[0].bookKey.isNotEmpty())
        assertTrue(
            "bookKey should be UUID format",
            entities[0].bookKey.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
        )
    }

    @Test
    fun `two independent importBook calls produce different bookKeys`() = runTest {
        val f1 = File(tempFolder.root, "a.txt").also { it.writeText("aaa") }
        val f2 = File(tempFolder.root, "b.txt").also { it.writeText("bbb") }

        repository.importBook(f1, copyToAppDir = false)
        repository.importBook(f2, copyToAppDir = false)

        val entities = mutableListOf<BookEntity>()
        coVerify(exactly = 2) { bookDao.insertBook(capture(entities)) }

        assertNotEquals(
            "Two imports should produce different bookKeys",
            entities[0].bookKey,
            entities[1].bookKey
        )
    }
}
