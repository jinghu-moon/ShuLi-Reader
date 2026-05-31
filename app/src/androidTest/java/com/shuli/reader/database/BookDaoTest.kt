package com.shuli.reader.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shuli.reader.core.database.ShuLiDatabase
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.entity.BookContentIndexEntity
import com.shuli.reader.core.database.entity.BookEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BookDaoTest {

    private lateinit var database: ShuLiDatabase
    private lateinit var bookDao: BookDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = TestDatabaseFactory.create(context)
        bookDao = database.bookDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createTestBook(
        title: String = "测试书籍",
        filePath: String = "/storage/test.txt",
        author: String = "测试作者",
        lastReadTime: Long? = null,
        addedTime: Long = System.currentTimeMillis(),
    ) = BookEntity(
        title = title,
        author = author,
        filePath = filePath,
        fileType = "TXT",
        fileSize = 1024L,
        coverPath = null,
        lastReadTime = lastReadTime,
        addedTime = addedTime,
    )

    @Test
    fun insertAndQuery() = runTest {
        val book = createTestBook()
        val id = bookDao.insertBook(book)
        val retrieved = bookDao.getBookById(id).first()
        assertNotNull(retrieved)
        assertEquals("测试书籍", retrieved!!.title)
    }

    @Test
    fun deleteById() = runTest {
        val id = bookDao.insertBook(createTestBook())
        bookDao.deleteBookById(id)
        val result = bookDao.getBookById(id).first()
        assertNull(result)
    }

    @Test
    fun getAllBooksReturnsInsertOrder() = runTest {
        bookDao.insertBook(createTestBook(title = "第一本", filePath = "/a.txt"))
        bookDao.insertBook(createTestBook(title = "第二本", filePath = "/b.txt"))
        val books = bookDao.getAllBooks().first()
        assertEquals(2, books.size)
    }

    @Test
    fun getBooksPageReturnsLimitedWindow() = runTest {
        (1..150).forEach { index ->
            bookDao.insertBook(
                createTestBook(
                    title = "Book $index",
                    filePath = "/page-$index.txt",
                    lastReadTime = index.toLong(),
                    addedTime = index.toLong(),
                ),
            )
        }

        val books = bookDao.getBooksPage(limit = 20, offset = 40).first()

        assertEquals(20, books.size)
        assertEquals("Book 110", books.first().title)
    }

    @Test
    fun getBookRowsPageReturnsBookshelfProjection() = runTest {
        bookDao.insertBook(
            createTestBook(
                title = "Projection Book",
                filePath = "/projection.epub",
                lastReadTime = 3L,
                addedTime = 1L,
            ).copy(
                fileType = "EPUB",
                readingProgress = 0.5f,
                isFavorite = true,
                durChapterTitle = "Hidden Chapter",
            ),
        )

        val rows = bookDao.getBookRowsPage(limit = 10, offset = 0).first()

        assertEquals(1, rows.size)
        assertEquals("Projection Book", rows.first().title)
        assertEquals("EPUB", rows.first().fileType)
        assertEquals(0.5f, rows.first().readingProgress)
    }

    @Test
    fun updateReadingProgress() = runTest {
        val id = bookDao.insertBook(createTestBook())
        bookDao.updateReadingProgress(id, 0.75f)
        val updated = bookDao.getBookById(id).first()
        assertEquals(0.75f, updated!!.readingProgress)
    }

    @Test
    fun getBookByFilePathReturnsCorrectBook() = runTest {
        bookDao.insertBook(createTestBook(title = "路径测试", filePath = "/unique/path.txt"))
        val result = bookDao.getBookByFilePath("/unique/path.txt")
        assertNotNull(result)
        assertEquals("路径测试", result!!.title)
    }

    @Test
    fun getBookByFilePathReturnsNullWhenNotFound() = runTest {
        val result = bookDao.getBookByFilePath("/nonexistent/path.txt")
        assertNull(result)
    }

    // T2.4 - FTS 全文搜索测试

    @Test
    fun searchBooksFtsByTitle() = runTest {
        bookDao.insertBook(createTestBook(title = "三体", filePath = "/a.txt"))
        bookDao.insertBook(createTestBook(title = "红楼梦", filePath = "/b.txt"))
        bookDao.insertBook(createTestBook(title = "三国演义", filePath = "/c.txt"))

        val results = bookDao.searchBooksFts("三*").first()
        assertEquals(2, results.size)
        assert(results.any { it.title == "三体" })
        assert(results.any { it.title == "三国演义" })
    }

    @Test
    fun searchBooksFtsByAuthor() = runTest {
        bookDao.insertBook(createTestBook(title = "书A", filePath = "/a.txt"))
        bookDao.insertBook(createTestBook(title = "书B", filePath = "/b.txt"))
        // 默认 author 是 "测试作者"

        val results = bookDao.searchBooksFts("测试*").first()
        assertEquals(2, results.size)
    }

    @Test
    fun searchBooksFtsReturnsEmptyForNoMatch() = runTest {
        bookDao.insertBook(createTestBook(title = "三体", filePath = "/a.txt"))

        val results = bookDao.searchBooksFts("不存在的关键词").first()
        assertEquals(0, results.size)
    }

    @Test
    fun searchBooksFtsPageReturnsLimitedWindow() = runTest {
        (1..30).forEach { index ->
            bookDao.insertBook(
                createTestBook(
                    title = "Space Story $index",
                    filePath = "/space-$index.txt",
                    author = "Author $index",
                    lastReadTime = index.toLong(),
                    addedTime = index.toLong(),
                ),
            )
        }

        val results = bookDao.searchBooksFtsPage(query = "Space*", limit = 10, offset = 10).first()

        assertEquals(10, results.size)
        assertEquals("Space Story 20", results.first().title)
    }

    @Test
    fun searchBookRowsFtsPageReturnsBookshelfProjection() = runTest {
        bookDao.insertBook(
            createTestBook(
                title = "Projected Search",
                filePath = "/projected-search.txt",
                author = "Search Author",
                lastReadTime = 2L,
                addedTime = 1L,
            ),
        )

        val results = bookDao.searchBookRowsFtsPage(query = "Projected*", limit = 10, offset = 0).first()

        assertEquals(1, results.size)
        assertEquals("Projected Search", results.first().title)
        assertEquals("/projected-search.txt", results.first().filePath)
    }

    @Test
    fun searchBooksFtsUpdatesAfterDelete() = runTest {
        val id = bookDao.insertBook(createTestBook(title = "将被删除", filePath = "/a.txt"))

        var results = bookDao.searchBooksFts("删除*").first()
        assertEquals(1, results.size)

        bookDao.deleteBookById(id)

        results = bookDao.searchBooksFts("删除*").first()
        assertEquals(0, results.size)
    }

    @Test
    fun replaceBookContentIndex_searchesAndDeletesRowsByBook() = runTest {
        val bookId = bookDao.insertBook(createTestBook(title = "正文索引", filePath = "/indexed.txt"))
        bookDao.replaceBookContentIndex(
            bookId = bookId,
            rows = listOf(
                BookContentIndexEntity(
                    bookId = bookId,
                    chapterIndex = 0,
                    chapterTitle = "Chapter 1",
                    byteStart = 0,
                    content = "Alpha keyword",
                ),
            ),
        )

        val indexedRows = bookDao.searchBookContentIndex(bookId, "keyword")
        assertEquals(1, indexedRows.size)
        assertEquals(1, bookDao.countBookContentIndex(bookId))

        bookDao.deleteBookContentIndex(bookId)
        assertEquals(0, bookDao.countBookContentIndex(bookId))
    }
}
