package com.shuli.reader.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shuli.reader.core.database.ShuLiDatabase
import com.shuli.reader.core.database.dao.ReadingSessionDao
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.ReadingSessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReadingSessionDaoTest {

    private lateinit var database: ShuLiDatabase
    private lateinit var dao: ReadingSessionDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = TestDatabaseFactory.create(context)
        dao = database.readingSessionDao()
        runTest {
            database.bookDao().insertBook(
                BookEntity(
                    title = "测试书籍",
                    author = "作者",
                    filePath = "/test.txt",
                    fileType = "TXT",
                    fileSize = 1024L,
                    coverPath = null,
                    lastReadTime = null,
                    addedTime = System.currentTimeMillis(),
                ),
            )
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun session(
        bookId: Long = 1L,
        chapterIndex: Int = 0,
        startedAt: Long = 1000L,
        endedAt: Long = 2000L,
        durationSeconds: Long = 1000L,
        dateKey: Int = 20260609,
        hour: Int = 10,
    ) = ReadingSessionEntity(
        bookId = bookId,
        chapterIndex = chapterIndex,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSeconds = durationSeconds,
        dateKey = dateKey,
        hour = hour,
    )

    // ── 1.2 写入 ──

    @Test
    fun insertReturnsAutoIncrementId() = runTest {
        val id1 = dao.insert(session())
        val id2 = dao.insert(session())
        assertEquals(1L, id1)
        assertEquals(2L, id2)
    }

    @Test
    fun concurrentInsertsDoNotLoseData() = runTest {
        dao.insert(session(durationSeconds = 30))
        dao.insert(session(durationSeconds = 20))
        val totals = dao.getBookTotals().first()
        assertEquals(1, totals.size)
        assertEquals(50L, totals[0].totalDuration)
    }

    // ── 1.3 热力图查询 ──

    @Test
    fun getDailyTotalsBasicAggregation() = runTest {
        dao.insert(session(dateKey = 20260609, durationSeconds = 100))
        dao.insert(session(dateKey = 20260610, durationSeconds = 200))
        dao.insert(session(dateKey = 20260611, durationSeconds = 300))

        val result = dao.getDailyTotals(20260609, 20260611).first()

        assertEquals(3, result.size)
        assertEquals(100L, result[0].total)
        assertEquals(200L, result[1].total)
        assertEquals(300L, result[2].total)
    }

    @Test
    fun getDailyTotalsRangeFilter() = runTest {
        (1..5).forEach { day ->
            dao.insert(session(dateKey = 20260100 + day, durationSeconds = day * 100L))
        }

        val result = dao.getDailyTotals(20260102, 20260104).first()

        assertEquals(3, result.size)
        assertEquals(20260102, result[0].dateKey)
        assertEquals(20260104, result[2].dateKey)
    }

    @Test
    fun getDailyTotalsMergeSameDay() = runTest {
        dao.insert(session(dateKey = 20260609, durationSeconds = 100))
        dao.insert(session(dateKey = 20260609, durationSeconds = 200))
        dao.insert(session(dateKey = 20260609, durationSeconds = 300))

        val result = dao.getDailyTotals(20260609, 20260609).first()

        assertEquals(1, result.size)
        assertEquals(600L, result[0].total)
    }

    @Test
    fun getDailyTotalsEmptyData() = runTest {
        val result = dao.getDailyTotals(20260101, 20261231).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun getDailyTotalsOrderedByDateKeyAsc() = runTest {
        dao.insert(session(dateKey = 20260611, durationSeconds = 300))
        dao.insert(session(dateKey = 20260609, durationSeconds = 100))
        dao.insert(session(dateKey = 20260610, durationSeconds = 200))

        val result = dao.getDailyTotals(20260609, 20260611).first()

        assertEquals(20260609, result[0].dateKey)
        assertEquals(20260610, result[1].dateKey)
        assertEquals(20260611, result[2].dateKey)
    }

    // ── 1.4 24h 热力条查询 ──

    @Test
    fun getHourlyTotalsBasicAggregation() = runTest {
        dao.insert(session(hour = 9, durationSeconds = 100))
        dao.insert(session(hour = 10, durationSeconds = 200))
        dao.insert(session(hour = 11, durationSeconds = 300))

        val result = dao.getHourlyTotals(20260609)

        assertEquals(3, result.size)
    }

    @Test
    fun getHourlyTotalsMergeSameHour() = runTest {
        dao.insert(session(hour = 10, durationSeconds = 100))
        dao.insert(session(hour = 10, durationSeconds = 200))

        val result = dao.getHourlyTotals(20260609)

        assertEquals(1, result.size)
        assertEquals(10, result[0].hour)
        assertEquals(300L, result[0].total)
    }

    @Test
    fun getHourlyTotalsFiltersByDateKey() = runTest {
        dao.insert(session(dateKey = 20260609, hour = 10, durationSeconds = 100))
        dao.insert(session(dateKey = 20260610, hour = 10, durationSeconds = 999))

        val result = dao.getHourlyTotals(20260609)

        assertEquals(1, result.size)
        assertEquals(100L, result[0].total)
    }

    // ── 1.5 连续活跃天数 ──

    @Test
    fun getActiveDateKeysDistinctAndOrdered() = runTest {
        dao.insert(session(dateKey = 20260103))
        dao.insert(session(dateKey = 20260101))
        dao.insert(session(dateKey = 20260103))

        val result = dao.getActiveDateKeys(20260101, 20260131)

        assertEquals(listOf(20260101, 20260103), result)
    }

    @Test
    fun getActiveDateKeysEmpty() = runTest {
        val result = dao.getActiveDateKeys(20260101, 20260131)
        assertTrue(result.isEmpty())
    }

    // ── 1.6 今日总时长 ──

    @Test
    fun getTodayTotalBasic() = runTest {
        dao.insert(session(dateKey = 20260609, durationSeconds = 300))
        dao.insert(session(dateKey = 20260609, durationSeconds = 600))

        val result = dao.getTodayTotal(20260609).first()

        assertEquals(900L, result)
    }

    @Test
    fun getTodayTotalNoDataReturnsNull() = runTest {
        val result = dao.getTodayTotal(20260609).first()
        assertNull(result)
    }

    @Test
    fun getTodayTotalFlowReactive() = runTest {
        dao.insert(session(dateKey = 20260609, durationSeconds = 300))
        var value = dao.getTodayTotal(20260609).first()
        assertEquals(300L, value)

        dao.insert(session(dateKey = 20260609, durationSeconds = 600))
        value = dao.getTodayTotal(20260609).first()
        assertEquals(900L, value)
    }

    // ── 1.7 书籍总时长 ──

    @Test
    fun getBookTotalsMultiBook() = runTest {
        database.bookDao().insertBook(
            BookEntity(
                title = "第二本书",
                author = "作者",
                filePath = "/test2.txt",
                fileType = "TXT",
                fileSize = 2048L,
                coverPath = null,
                lastReadTime = null,
                addedTime = System.currentTimeMillis(),
            ),
        )
        dao.insert(session(bookId = 1, durationSeconds = 100))
        dao.insert(session(bookId = 1, durationSeconds = 200))
        dao.insert(session(bookId = 2, durationSeconds = 500))

        val result = dao.getBookTotals().first()

        assertEquals(2, result.size)
        val book1 = result.first { it.bookId == 1L }
        val book2 = result.first { it.bookId == 2L }
        assertEquals(300L, book1.totalDuration)
        assertEquals(500L, book2.totalDuration)
    }

    @Test
    fun getBookTotalsFlowReactive() = runTest {
        dao.insert(session(bookId = 1, durationSeconds = 100))
        var result = dao.getBookTotals().first()
        assertEquals(1, result.size)
        assertEquals(100L, result[0].totalDuration)

        dao.insert(session(bookId = 1, durationSeconds = 200))
        result = dao.getBookTotals().first()
        assertEquals(300L, result[0].totalDuration)
    }

    // ── 1.8 章节总时长 ──

    @Test
    fun getChapterTotalsBasic() = runTest {
        dao.insert(session(chapterIndex = 0, durationSeconds = 100, startedAt = 1000, endedAt = 1100))
        dao.insert(session(chapterIndex = 0, durationSeconds = 200, startedAt = 1100, endedAt = 1300))
        dao.insert(session(chapterIndex = 1, durationSeconds = 300, startedAt = 2000, endedAt = 2300))

        val result = dao.getChapterTotals(1L).first()

        assertEquals(2, result.size)
        assertEquals(0, result[0].chapterIndex)
        assertEquals(300L, result[0].totalSeconds)
        assertEquals(1, result[1].chapterIndex)
        assertEquals(300L, result[1].totalSeconds)
    }

    @Test
    fun getChapterTotalsFirstAndLastVisited() = runTest {
        dao.insert(session(chapterIndex = 0, startedAt = 100, endedAt = 200, durationSeconds = 100))
        dao.insert(session(chapterIndex = 0, startedAt = 300, endedAt = 400, durationSeconds = 100))

        val result = dao.getChapterTotals(1L).first()

        assertEquals(100L, result[0].firstVisitedAt)
        assertEquals(400L, result[0].lastVisitedAt)
    }

    @Test
    fun getChapterTotalsOrderedByChapterIndex() = runTest {
        dao.insert(session(chapterIndex = 2, startedAt = 300, endedAt = 400, durationSeconds = 100))
        dao.insert(session(chapterIndex = 0, startedAt = 100, endedAt = 200, durationSeconds = 100))
        dao.insert(session(chapterIndex = 1, startedAt = 200, endedAt = 300, durationSeconds = 100))

        val result = dao.getChapterTotals(1L).first()

        assertEquals(0, result[0].chapterIndex)
        assertEquals(1, result[1].chapterIndex)
        assertEquals(2, result[2].chapterIndex)
    }

    // ── 1.9 阅读时间轴 ──

    @Test
    fun getSessionsInRangeReturnsFullEntities() = runTest {
        dao.insert(session(dateKey = 20260609, startedAt = 1000, durationSeconds = 500))
        dao.insert(session(dateKey = 20260609, startedAt = 2000, durationSeconds = 300))

        val result = dao.getSessionsInRange(20260609, 20260609).first()

        assertEquals(2, result.size)
        assertEquals(2000L, result[0].startedAt)
        assertEquals(1000L, result[1].startedAt)
    }

    @Test
    fun getSessionsInRangeOrderedByStartedAtDesc() = runTest {
        dao.insert(session(dateKey = 20260609, startedAt = 100, endedAt = 200, durationSeconds = 100))
        dao.insert(session(dateKey = 20260609, startedAt = 300, endedAt = 400, durationSeconds = 100))
        dao.insert(session(dateKey = 20260609, startedAt = 200, endedAt = 300, durationSeconds = 100))

        val result = dao.getSessionsInRange(20260609, 20260609).first()

        assertEquals(300L, result[0].startedAt)
        assertEquals(200L, result[1].startedAt)
        assertEquals(100L, result[2].startedAt)
    }

    @Test
    fun getSessionsInRangeFiltersByDateKey() = runTest {
        dao.insert(session(dateKey = 20260609, startedAt = 100, endedAt = 200, durationSeconds = 100))
        dao.insert(session(dateKey = 20260610, startedAt = 300, endedAt = 400, durationSeconds = 100))
        dao.insert(session(dateKey = 20260611, startedAt = 500, endedAt = 600, durationSeconds = 100))

        val result = dao.getSessionsInRange(20260609, 20260610).first()

        assertEquals(2, result.size)
    }
}
