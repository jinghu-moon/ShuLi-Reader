package com.shuli.reader.feature.stats

import com.shuli.reader.core.database.dao.BookWithDurationTuple
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.DailyTotalTuple
import com.shuli.reader.core.database.dao.ReadingSessionDao
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsRepositoryTest {

    private lateinit var sessionDao: ReadingSessionDao
    private lateinit var bookDao: BookDao
    private lateinit var repo: StatsRepository

    @Before
    fun setup() {
        sessionDao = mockk(relaxed = true)
        bookDao = mockk(relaxed = true)
        repo = StatsRepository(
            readingSessionDao = sessionDao,
            bookDao = bookDao,
        )
    }

    @Test
    fun getLongestStreak_consecutive() {
        val keys = listOf(20260101, 20260102, 20260103, 20260105, 20260106)
        assertEquals(3, repo.getLongestStreak(keys))
    }

    @Test
    fun getLongestStreak_empty() {
        assertEquals(0, repo.getLongestStreak(emptyList()))
    }

    @Test
    fun getLongestStreak_allConsecutive() {
        val keys = listOf(20260101, 20260102, 20260103, 20260104, 20260105, 20260106, 20260107)
        assertEquals(7, repo.getLongestStreak(keys))
    }

    @Test
    fun getDailyNeededMinutes_normal() {
        assertEquals(20L, repo.getDailyNeededMinutes(300, 100, 10))
    }

    @Test
    fun getDailyNeededMinutes_alreadyMet() {
        assertEquals(0L, repo.getDailyNeededMinutes(300, 350, 10))
    }

    @Test
    fun getDailyNeededMinutes_zeroRemainingDays() {
        assertEquals(0L, repo.getDailyNeededMinutes(300, 100, 0))
    }

    @Test
    fun getSpeedTrend_up() = runTest {
        assertEquals(SpeedTrend.UP, repo.getSpeedTrend(200, 150))
    }

    @Test
    fun getSpeedTrend_down() = runTest {
        assertEquals(SpeedTrend.DOWN, repo.getSpeedTrend(100, 150))
    }

    @Test
    fun getSpeedTrend_flat() = runTest {
        assertEquals(SpeedTrend.FLAT, repo.getSpeedTrend(150, 150))
    }

    @Test
    fun getHeatmapData_empty() = runTest {
        every { sessionDao.getDailyTotals(any(), any()) } returns flowOf(emptyList())
        val result = repo.getHeatmapData(20260101, 20260131).first()
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.heatLevel == HeatLevel.L0 })
    }

    @Test
    fun getDistribution_byFormat() = runTest {
        val books = listOf(
            BookWithDurationTuple(1, "Book1", "Author", "TXT", "READING", 100, 50000, 3600),
            BookWithDurationTuple(2, "Book2", "Author", "TXT", "READING", 50, 30000, 1800),
            BookWithDurationTuple(3, "Book3", "Author", "EPUB", "FINISHED", 200, 80000, 7200),
        )
        every { bookDao.getAllBooksWithDuration() } returns flowOf(books)
        val result = repo.getDistribution(DistributionDim.FORMAT).first()
        assertEquals(2, result.size)
        assertTrue(result.any { it.label == "TXT" })
        assertTrue(result.any { it.label == "EPUB" })
    }

    @Test
    fun getTopN_byDuration() = runTest {
        val books = listOf(
            BookWithDurationTuple(1, "Book1", "Author", "TXT", "READING", 100, 50000, 7200),
            BookWithDurationTuple(2, "Book2", "Author", "TXT", "READING", 50, 30000, 3600),
            BookWithDurationTuple(3, "Book3", "Author", "EPUB", "FINISHED", 200, 80000, 1800),
        )
        every { bookDao.getAllBooksWithDuration() } returns flowOf(books)
        val result = repo.getTopN(TopNSort.DURATION, 2).first()
        assertEquals(2, result.size)
        assertEquals(1L, result[0].bookId)
    }

    @Test
    fun getReadingStatusDistribution() = runTest {
        val books = listOf(
            BookWithDurationTuple(1, "B1", "A", "TXT", "READING", 100, 50000, 3600),
            BookWithDurationTuple(2, "B2", "A", "TXT", "READING", 50, 30000, 1800),
            BookWithDurationTuple(3, "B3", "A", "EPUB", "FINISHED", 200, 80000, 7200),
        )
        every { bookDao.getAllBooksWithDuration() } returns flowOf(books)
        val result = repo.getReadingStatusDistribution().first()
        assertEquals(2, result.size)
        val reading = result.first { it.status == "READING" }
        assertEquals(2, reading.count)
    }

    @Test
    fun todayDateKey_format() {
        val key = StatsRepository.todayDateKey()
        assertTrue(key > 20200000)
        assertTrue(key < 21000000)
    }

    @Test
    fun nextDateKey_basic() {
        assertEquals(20260102, StatsRepository.nextDateKey(20260101))
    }

    @Test
    fun prevDateKey_basic() {
        assertEquals(20260101, StatsRepository.prevDateKey(20260102))
    }

    @Test
    fun getDistribution_byWords_bucketsCorrectly() = runTest {
        val books = listOf(
            BookWithDurationTuple(1, "B1", "A", "TXT", "READING", 10, 5_000, 600),
            BookWithDurationTuple(2, "B2", "A", "TXT", "READING", 50, 30_000, 1800),
            BookWithDurationTuple(3, "B3", "A", "TXT", "READING", 150, 80_000, 3600),
            BookWithDurationTuple(4, "B4", "A", "TXT", "READING", 500, 200_000, 7200),
            BookWithDurationTuple(5, "B5", "A", "TXT", "READING", 1000, 800_000, 14400),
        )
        every { bookDao.getAllBooksWithDuration() } returns flowOf(books)
        val result = repo.getDistribution(DistributionDim.WORDS).first()
        val labels = result.map { it.label }.toSet()
        assertEquals(
            setOf("<1万字", "1-5万字", "5-10万字", "10-50万字", ">50万字"),
            labels,
        )
    }

    @Test
    fun getTopN_byBookmarks() = runTest {
        val books = listOf(
            BookWithDurationTuple(1, "B1", "A", "TXT", "READING", 100, 50000, 7200),
            BookWithDurationTuple(2, "B2", "A", "TXT", "READING", 50, 30000, 3600),
            BookWithDurationTuple(3, "B3", "A", "EPUB", "FINISHED", 200, 80000, 1800),
        )
        val counts = listOf(
            com.shuli.reader.core.database.dao.BookCountTuple(1, 2),
            com.shuli.reader.core.database.dao.BookCountTuple(2, 10),
            com.shuli.reader.core.database.dao.BookCountTuple(3, 5),
        )
        every { bookDao.getAllBooksWithDuration() } returns flowOf(books)
        every { bookDao.getBookmarkCounts() } returns flowOf(counts)
        val result = repo.getTopN(TopNSort.BOOKMARKS).first()
        assertEquals(3, result.size)
        assertEquals(2L, result[0].bookId)
        assertEquals(10L, result[0].value)
        assertEquals(3L, result[1].bookId)
    }

    @Test
    fun getTopN_byNotes() = runTest {
        val books = listOf(
            BookWithDurationTuple(1, "B1", "A", "TXT", "READING", 100, 50000, 7200),
            BookWithDurationTuple(2, "B2", "A", "TXT", "READING", 50, 30000, 3600),
        )
        val counts = listOf(
            com.shuli.reader.core.database.dao.BookCountTuple(1, 3),
            com.shuli.reader.core.database.dao.BookCountTuple(2, 15),
        )
        every { bookDao.getAllBooksWithDuration() } returns flowOf(books)
        every { bookDao.getNoteCounts() } returns flowOf(counts)
        val result = repo.getTopN(TopNSort.NOTES).first()
        assertEquals(2L, result[0].bookId)
        assertEquals(15L, result[0].value)
    }

    @Test
    fun getTopN_bySpeed() = runTest {
        val books = listOf(
            BookWithDurationTuple(1, "B1", "A", "TXT", "READING", 100, 6000, 60),
            BookWithDurationTuple(2, "B2", "A", "TXT", "READING", 100, 1000, 60),
        )
        every { bookDao.getAllBooksWithDuration() } returns flowOf(books)
        val result = repo.getTopN(TopNSort.SPEED).first()
        assertEquals(1L, result[0].bookId)
        assertEquals(6000L, result[0].value)
        assertEquals(1000L, result[1].value)
    }

    @Test
    fun getTopN_limitParameter() = runTest {
        val books = (1..10).map {
            BookWithDurationTuple(it.toLong(), "B$it", "A", "TXT", "READING", 100, 50000, it * 60L)
        }
        every { bookDao.getAllBooksWithDuration() } returns flowOf(books)
        val result = repo.getTopN(TopNSort.DURATION, limit = 3).first()
        assertEquals(3, result.size)
    }

    @Test
    fun getReadingStatusDistribution_countsAllStatuses() = runTest {
        val books = listOf(
            BookWithDurationTuple(1, "B1", "A", "TXT", "READING", 100, 50000, 3600),
            BookWithDurationTuple(2, "B2", "A", "TXT", "READING", 50, 30000, 1800),
            BookWithDurationTuple(3, "B3", "A", "EPUB", "READING", 200, 80000, 7200),
            BookWithDurationTuple(4, "B4", "A", "EPUB", "FINISHED", 200, 80000, 7200),
            BookWithDurationTuple(5, "B5", "A", "EPUB", "FINISHED", 200, 80000, 7200),
            BookWithDurationTuple(6, "B6", "A", "EPUB", "PAUSED", 200, 80000, 7200),
        )
        every { bookDao.getAllBooksWithDuration() } returns flowOf(books)
        val result = repo.getReadingStatusDistribution().first()
        assertEquals(3, result.first { it.status == "READING" }.count)
        assertEquals(2, result.first { it.status == "FINISHED" }.count)
        assertEquals(1, result.first { it.status == "PAUSED" }.count)
    }

    @Test
    fun getLongestStreak_singleElement() {
        assertEquals(1, repo.getLongestStreak(listOf(20260101)))
    }

    @Test
    fun getCurrentStreak_fromTodayBackwards() {
        val today = StatsRepository.todayDateKey()
        val yesterday = StatsRepository.prevDateKey(today)
        val twoDaysAgo = StatsRepository.prevDateKey(yesterday)
        val keys = listOf(twoDaysAgo, yesterday, today)
        assertEquals(3, repo.getCurrentStreak(keys))
    }

    @Test
    fun getCurrentStreak_todayMissing_returnsZero() {
        val yesterday = StatsRepository.prevDateKey(StatsRepository.todayDateKey())
        val keys = listOf(StatsRepository.prevDateKey(yesterday), yesterday)
        assertEquals(0, repo.getCurrentStreak(keys))
    }

    @Test
    fun getCurrentStreak_empty_returnsZero() {
        assertEquals(0, repo.getCurrentStreak(emptyList()))
    }

    @Test
    fun getDailyNeededMinutes_negativeRemaining_returnsZero() {
        assertEquals(0L, repo.getDailyNeededMinutes(300, 100, -5))
    }

    @Test
    fun getDailyNeededMinutes_exactGoal_returnsZero() {
        assertEquals(0L, repo.getDailyNeededMinutes(300, 300, 10))
    }
}
