package com.shuli.reader.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shuli.reader.core.database.ShuLiDatabase
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.ReadingSessionDao
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.ReadingSessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BookDaoStatsTest {

    private lateinit var database: ShuLiDatabase
    private lateinit var bookDao: BookDao
    private lateinit var sessionDao: ReadingSessionDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = TestDatabaseFactory.create(context)
        bookDao = database.bookDao()
        sessionDao = database.readingSessionDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createBook(title: String, filePath: String, fileType: String = "TXT") =
        BookEntity(
            title = title,
            author = "作者",
            filePath = filePath,
            fileType = fileType,
            fileSize = 1024L,
            coverPath = null,
            lastReadTime = null,
            addedTime = System.currentTimeMillis(),
        )

    @Test
    fun getAllBooksWithDurationBasic() = runTest {
        val id1 = bookDao.insertBook(createBook("书A", "/a.txt"))
        val id2 = bookDao.insertBook(createBook("书B", "/b.epub", "EPUB"))

        sessionDao.insert(
            ReadingSessionEntity(
                bookId = id1, chapterIndex = 0,
                startedAt = 1000, endedAt = 2000,
                durationSeconds = 1000, dateKey = 20260609, hour = 10,
            ),
        )
        sessionDao.insert(
            ReadingSessionEntity(
                bookId = id1, chapterIndex = 1,
                startedAt = 2000, endedAt = 3000,
                durationSeconds = 1000, dateKey = 20260609, hour = 11,
            ),
        )
        sessionDao.insert(
            ReadingSessionEntity(
                bookId = id2, chapterIndex = 0,
                startedAt = 3000, endedAt = 3500,
                durationSeconds = 500, dateKey = 20260609, hour = 12,
            ),
        )

        val result = bookDao.getAllBooksWithDuration().first()

        assertEquals(2, result.size)
        val bookA = result.first { it.id == id1 }
        val bookB = result.first { it.id == id2 }
        assertEquals(2000L, bookA.totalDuration)
        assertEquals(500L, bookB.totalDuration)
        assertEquals("书A", bookA.title)
        assertEquals("TXT", bookA.fileType)
        assertEquals("EPUB", bookB.fileType)
    }

    @Test
    fun getAllBooksWithDurationNoSessions() = runTest {
        bookDao.insertBook(createBook("无阅读", "/empty.txt"))

        val result = bookDao.getAllBooksWithDuration().first()

        assertEquals(1, result.size)
        assertEquals(0L, result[0].totalDuration)
    }
}
