package com.shuli.reader.core.database.dao

import org.junit.Assert.assertEquals
import org.junit.Test

class TupleDataClassTest {

    @Test
    fun dailyTotalTupleFields() {
        val tuple = DailyTotalTuple(dateKey = 20260609, total = 3600L)
        assertEquals(20260609, tuple.dateKey)
        assertEquals(3600L, tuple.total)
    }

    @Test
    fun hourlyTotalTupleFields() {
        val tuple = HourlyTotalTuple(hour = 14, total = 1800L)
        assertEquals(14, tuple.hour)
        assertEquals(1800L, tuple.total)
    }

    @Test
    fun chapterTotalTupleFields() {
        val tuple = ChapterTotalTuple(
            chapterIndex = 3,
            totalSeconds = 900L,
            firstVisitedAt = 1000L,
            lastVisitedAt = 2000L,
        )
        assertEquals(3, tuple.chapterIndex)
        assertEquals(900L, tuple.totalSeconds)
        assertEquals(1000L, tuple.firstVisitedAt)
        assertEquals(2000L, tuple.lastVisitedAt)
    }

    @Test
    fun bookDurationTupleFields() {
        val tuple = BookDurationTuple(bookId = 1L, totalDuration = 7200L)
        assertEquals(1L, tuple.bookId)
        assertEquals(7200L, tuple.totalDuration)
    }

    @Test
    fun bookWithDurationTupleFields() {
        val tuple = BookWithDurationTuple(
            id = 42L,
            title = "测试书籍",
            author = "作者",
            fileType = "TXT",
            readingStatus = "READING",
            totalChapterNum = 100,
            estimatedTotalChars = 500_000L,
            totalDuration = 3600L,
        )
        assertEquals(42L, tuple.id)
        assertEquals("测试书籍", tuple.title)
        assertEquals("作者", tuple.author)
        assertEquals("TXT", tuple.fileType)
        assertEquals("READING", tuple.readingStatus)
        assertEquals(100, tuple.totalChapterNum)
        assertEquals(500_000L, tuple.estimatedTotalChars)
        assertEquals(3600L, tuple.totalDuration)
    }
}
