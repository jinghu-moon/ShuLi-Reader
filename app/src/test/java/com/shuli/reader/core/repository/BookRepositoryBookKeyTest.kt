package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookChapterDao
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.parser.ByteWindowReader
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.TxtParser
import io.mockk.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    private lateinit var repository: BookImportRepository

    @Before
    fun setup() {
        bookDao = mockk(relaxed = true)
        val bookChapterDao = mockk<BookChapterDao>(relaxed = true)

        coEvery { bookDao.insertBook(any()) } returns 1L
        coEvery { bookDao.getBookByFilePath(any()) } returns null
        coEvery { bookDao.getBooksByFileNameAndSize(any(), any()) } returns emptyList()

        val searchIndexRepository = SearchIndexRepository(
            bookDao = bookDao,
            bookChapterDao = bookChapterDao,
            txtParser = TxtParser(),
            epubParser = EpubParser(),
            byteWindowReader = ByteWindowReader(),
        )

        repository = BookImportRepository(
            bookDao = bookDao,
            txtParser = TxtParser(),
            epubParser = EpubParser(),
            booksDir = tempFolder.newFolder("books"),
            searchIndexRepository = searchIndexRepository,
            applicationScope = TestScope(StandardTestDispatcher()),
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
