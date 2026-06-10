package com.shuli.reader.feature.stats

import com.shuli.reader.core.database.dao.BookCountTuple
import com.shuli.reader.core.database.dao.BookWithDurationTuple
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.BookmarkDao
import com.shuli.reader.core.database.dao.ChapterTotalTuple
import com.shuli.reader.core.database.dao.DailyTotalTuple
import com.shuli.reader.core.database.dao.NoteDao
import com.shuli.reader.core.database.dao.ReadingSessionDao
import com.shuli.reader.core.data.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class DailyHeatCell(
    val dateKey: Int,
    val minutes: Long,
    val heatLevel: HeatLevel,
)

enum class HeatLevel { L0, L1, L2, L3, L4, L5 }

data class HeroMetrics(
    val totalMinutes: Long = 0,
    val activeDays: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val dailyAvgMinutes: Long = 0,
    val deltaPercent: Float = 0f,
    val deltaIsUp: Boolean = true,
    val goalPercent: Int = 0,
    val goalMinutes: Long = 0,
    val dailyNeededMinutes: Long = 0,
)

data class WeekChartData(
    val thisWeek: List<Long>,
    val lastWeek: List<Long>,
)

enum class DistributionDim { AUTHOR, GROUP, FORMAT, WORDS }
enum class TopNSort { DURATION, BOOKMARKS, NOTES, SPEED }
enum class SpeedTrend { UP, DOWN, FLAT }

data class DistributionItem(
    val label: String,
    val minutes: Long,
    val percent: Float,
)

data class TopNBookItem(
    val bookId: Long,
    val title: String,
    val author: String?,
    val value: Long,
) {
    companion object
}

fun BookWithDurationTuple.toTopNItem(value: Long): TopNBookItem = TopNBookItem(
    bookId = id,
    title = title,
    author = author,
    value = value,
)

data class StatusItem(
    val status: String,
    val count: Int,
    val percent: Float,
)

class StatsRepository(
    private val readingSessionDao: ReadingSessionDao,
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao? = null,
    private val noteDao: NoteDao? = null,
    private val userPreferences: UserPreferences? = null,
) {

    fun getHeatmapData(start: Int, end: Int): Flow<List<DailyHeatCell>> {
        return readingSessionDao.getDailyTotals(start, end).map { tuples ->
            val maxMinutes = if (tuples.isEmpty()) 0L else tuples.maxOf { it.total / 60 }
            buildFullRange(start, end, tuples, maxMinutes)
        }
    }

    private fun buildFullRange(
        start: Int,
        end: Int,
        tuples: List<DailyTotalTuple>,
        maxMinutes: Long,
    ): List<DailyHeatCell> {
        val map = tuples.associate { it.dateKey to it.total }
        val cells = mutableListOf<DailyHeatCell>()
        var current = start
        while (current <= end) {
            val seconds = map[current] ?: 0L
            val minutes = seconds / 60
            cells.add(
                DailyHeatCell(
                    dateKey = current,
                    minutes = minutes,
                    heatLevel = heatLevel(minutes, maxMinutes),
                ),
            )
            current = nextDateKey(current)
        }
        return cells
    }

    suspend fun getHeroMetrics(start: Int, end: Int, prevStart: Int, prevEnd: Int): HeroMetrics {
        val tuples = readingSessionDao.getDailyTotals(start, end).map { it }.let { flow ->
            var result: List<DailyTotalTuple> = emptyList()
            flow.collect { result = it; return@collect }
            result
        }
        val prevTuples = readingSessionDao.getDailyTotals(prevStart, prevEnd).map { it }.let { flow ->
            var result: List<DailyTotalTuple> = emptyList()
            flow.collect { result = it; return@collect }
            result
        }
        val activeDateKeys = readingSessionDao.getActiveDateKeys(start, end)
        val totalSeconds = tuples.sumOf { it.total }
        val totalMinutes = totalSeconds / 60
        val prevTotalMinutes = prevTuples.sumOf { it.total } / 60
        val activeDays = activeDateKeys.size
        val currentStreak = getCurrentStreak(activeDateKeys)
        val longestStreak = getLongestStreak(activeDateKeys)
        val dailyAvg = if (activeDays > 0) totalMinutes / activeDays else 0L

        val deltaPercent = if (prevTotalMinutes > 0) {
            ((totalMinutes - prevTotalMinutes).toFloat() / prevTotalMinutes) * 100f
        } else if (totalMinutes > 0) 100f else 0f

        val goalMinutes = (userPreferences?.readingDailyTarget?.let { flow ->
            var target = 30L
            flow.collect { target = it.toLong(); return@collect }
            target
        } ?: 30L) * countDaysInRange(start, end)

        val goalPercent = if (goalMinutes > 0) {
            ((totalMinutes.toFloat() / goalMinutes) * 100).toInt().coerceIn(0, 100)
        } else 0

        val remainingDays = countDaysInRange(start, end) - activeDays
        val dailyNeeded = getDailyNeededMinutes(goalMinutes, totalMinutes, remainingDays)

        return HeroMetrics(
            totalMinutes = totalMinutes,
            activeDays = activeDays,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            dailyAvgMinutes = dailyAvg,
            deltaPercent = deltaPercent,
            deltaIsUp = totalMinutes >= prevTotalMinutes,
            goalPercent = goalPercent,
            goalMinutes = goalMinutes,
            dailyNeededMinutes = dailyNeeded,
        )
    }

    fun getHourlyData(dateKey: Int): Flow<List<Int>> {
        return flowOf(Unit).map {
            val hourlyTotals = readingSessionDao.getHourlyTotals(dateKey)
            val result = MutableList(24) { 0 }
            hourlyTotals.forEach { tuple ->
                if (tuple.hour in 0..23) {
                    result[tuple.hour] = (tuple.total / 60).toInt()
                }
            }
            result.toList()
        }
    }

    fun getWeeklyChartData(
        thisWeekStart: Int,
        thisWeekEnd: Int,
        lastWeekStart: Int,
        lastWeekEnd: Int,
    ): Flow<WeekChartData> {
        return combine(
            readingSessionDao.getDailyTotals(thisWeekStart, thisWeekEnd),
            readingSessionDao.getDailyTotals(lastWeekStart, lastWeekEnd),
        ) { thisTuples, lastTuples ->
            val thisMap = thisTuples.associate { it.dateKey to it.total / 60 }
            val lastMap = lastTuples.associate { it.dateKey to it.total / 60 }
            val thisWeek = buildDayList(thisWeekStart).map { thisMap[it] ?: 0L }
            val lastWeek = buildDayList(lastWeekStart).map { lastMap[it] ?: 0L }
            WeekChartData(thisWeek = thisWeek, lastWeek = lastWeek)
        }
    }

    fun getDistribution(dim: DistributionDim): Flow<List<DistributionItem>> {
        return bookDao.getAllBooksWithDuration().map { books ->
            val grouped = when (dim) {
                DistributionDim.AUTHOR -> books.groupBy { it.author ?: "Unknown" }
                DistributionDim.FORMAT -> books.groupBy { it.fileType }
                DistributionDim.GROUP -> books.groupBy { "All" }
                DistributionDim.WORDS -> books.groupBy { bucketWords(it.estimatedTotalChars) }
            }
            val total = grouped.values.sumOf { list -> list.sumOf { it.totalDuration } }
            grouped.map { (label, list) ->
                val minutes = list.sumOf { it.totalDuration } / 60
                val percent = if (total > 0) (list.sumOf { it.totalDuration }.toFloat() / total) * 100f else 0f
                DistributionItem(label = label, minutes = minutes, percent = percent)
            }.sortedByDescending { it.minutes }
        }
    }

    fun getTopN(sort: TopNSort, limit: Int = 5): Flow<List<TopNBookItem>> {
        return when (sort) {
            TopNSort.DURATION -> bookDao.getAllBooksWithDuration().map { books ->
                books.sortedByDescending { it.totalDuration }
                    .take(limit)
                    .map { it.toTopNItem(it.totalDuration / 60) }
            }
            TopNSort.BOOKMARKS -> combine(
                bookDao.getAllBooksWithDuration(),
                bookDao.getBookmarkCounts(),
            ) { books, counts ->
                val countMap = counts.associate { it.bookId to it.count.toLong() }
                books.map { book ->
                    TopNBookItem(
                        bookId = book.id,
                        title = book.title,
                        author = book.author,
                        value = countMap[book.id] ?: 0L,
                    )
                }.sortedByDescending { it.value }
                    .take(limit)
            }
            TopNSort.NOTES -> combine(
                bookDao.getAllBooksWithDuration(),
                bookDao.getNoteCounts(),
            ) { books, counts ->
                val countMap = counts.associate { it.bookId to it.count.toLong() }
                books.map { book ->
                    TopNBookItem(
                        bookId = book.id,
                        title = book.title,
                        author = book.author,
                        value = countMap[book.id] ?: 0L,
                    )
                }.sortedByDescending { it.value }
                    .take(limit)
            }
            TopNSort.SPEED -> bookDao.getAllBooksWithDuration().map { books ->
                books.map { book ->
                    val wpm = if (book.totalDuration > 0 && book.estimatedTotalChars > 0) {
                        (book.estimatedTotalChars / (book.totalDuration / 60))
                    } else 0L
                    TopNBookItem(
                        bookId = book.id,
                        title = book.title,
                        author = book.author,
                        value = wpm,
                    )
                }.sortedByDescending { it.value }
                    .take(limit)
            }
        }
    }

    fun getReadingStatusDistribution(): Flow<List<StatusItem>> {
        return bookDao.getAllBooksWithDuration().map { books ->
            val grouped = books.groupBy { it.readingStatus }
            val total = books.size
            grouped.map { (status, list) ->
                StatusItem(
                    status = status,
                    count = list.size,
                    percent = if (total > 0) (list.size.toFloat() / total) * 100f else 0f,
                )
            }.sortedByDescending { it.count }
        }
    }

    fun getLongestStreak(dateKeys: List<Int>): Int {
        if (dateKeys.isEmpty()) return 0
        var maxStreak = 1
        var currentStreak = 1
        for (i in 1 until dateKeys.size) {
            if (nextDateKey(dateKeys[i - 1]) == dateKeys[i]) {
                currentStreak++
                maxStreak = maxOf(maxStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }
        return maxStreak
    }

    fun getCurrentStreak(dateKeys: List<Int>): Int {
        if (dateKeys.isEmpty()) return 0
        val today = todayDateKey()
        var streak = 0
        var checkKey = today
        val keySet = dateKeys.toSet()
        while (checkKey in keySet) {
            streak++
            checkKey = prevDateKey(checkKey)
        }
        return streak
    }

    fun getDailyNeededMinutes(goalMinutes: Long, totalMinutes: Long, remainingDays: Int): Long {
        if (remainingDays <= 0) return 0
        val remaining = goalMinutes - totalMinutes
        return if (remaining <= 0) 0L else (remaining / remainingDays).coerceAtLeast(0)
    }

    suspend fun getSpeedTrend(thisWeekWpm: Int, lastWeekWpm: Int): SpeedTrend {
        return when {
            thisWeekWpm > lastWeekWpm -> SpeedTrend.UP
            thisWeekWpm < lastWeekWpm -> SpeedTrend.DOWN
            else -> SpeedTrend.FLAT
        }
    }

    fun getSessionsForTimeline(start: Int, end: Int): Flow<List<com.shuli.reader.core.database.entity.ReadingSessionEntity>> {
        return readingSessionDao.getSessionsInRange(start, end)
    }

    fun getBookTitles(): Flow<Map<Long, String>> {
        return bookDao.getAllBooks().map { books ->
            books.associate { it.id to it.title }
        }
    }

    private fun bucketWords(chars: Long): String {
        return when {
            chars < 10_000 -> "<1万字"
            chars < 50_000 -> "1-5万字"
            chars < 100_000 -> "5-10万字"
            chars < 500_000 -> "10-50万字"
            else -> ">50万字"
        }
    }

    private fun buildDayList(startKey: Int): List<Int> {
        val days = mutableListOf<Int>()
        var current = startKey
        repeat(7) {
            days.add(current)
            current = nextDateKey(current)
        }
        return days
    }

    private fun countDaysInRange(start: Int, end: Int): Int {
        var count = 0
        var current = start
        while (current <= end) {
            count++
            current = nextDateKey(current)
        }
        return count
    }

    companion object {
        fun heatLevel(minutes: Long, maxMinutes: Long): HeatLevel {
            if (minutes == 0L) return HeatLevel.L0
            if (maxMinutes <= 0L) return HeatLevel.L1
            val ratio = kotlin.math.sqrt(minutes.toFloat() / maxMinutes.toFloat())
            return when {
                ratio < 0.17f -> HeatLevel.L1
                ratio < 0.33f -> HeatLevel.L2
                ratio < 0.50f -> HeatLevel.L3
                ratio < 0.67f -> HeatLevel.L4
                else -> HeatLevel.L5
            }
        }

        fun todayDateKey(): Int {
            val cal = java.util.Calendar.getInstance()
            return cal.get(java.util.Calendar.YEAR) * 10000 +
                (cal.get(java.util.Calendar.MONTH) + 1) * 100 +
                cal.get(java.util.Calendar.DAY_OF_MONTH)
        }

        fun nextDateKey(dateKey: Int): Int {
            val date = dateKeyToLocalDate(dateKey)
            return date.plusDays(1).toDateKey()
        }

        fun prevDateKey(dateKey: Int): Int {
            val date = dateKeyToLocalDate(dateKey)
            return date.minusDays(1).toDateKey()
        }
    }
}
