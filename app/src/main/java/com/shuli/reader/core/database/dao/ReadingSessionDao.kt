package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.shuli.reader.core.database.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {

    @Insert
    suspend fun insert(session: ReadingSessionEntity): Long

    @Query(
        "SELECT date_key AS dateKey, SUM(duration_seconds) AS total " +
            "FROM reading_session " +
            "WHERE date_key BETWEEN :start AND :end " +
            "GROUP BY date_key " +
            "ORDER BY date_key ASC",
    )
    fun getDailyTotals(start: Int, end: Int): Flow<List<DailyTotalTuple>>

    @Query(
        "SELECT hour, SUM(duration_seconds) AS total " +
            "FROM reading_session " +
            "WHERE date_key = :dateKey " +
            "GROUP BY hour",
    )
    suspend fun getHourlyTotals(dateKey: Int): List<HourlyTotalTuple>

    @Query(
        "SELECT DISTINCT date_key " +
            "FROM reading_session " +
            "WHERE date_key BETWEEN :start AND :end " +
            "ORDER BY date_key ASC",
    )
    suspend fun getActiveDateKeys(start: Int, end: Int): List<Int>

    @Query("SELECT SUM(duration_seconds) FROM reading_session WHERE date_key = :todayKey")
    fun getTodayTotal(todayKey: Int): Flow<Long?>

    @Query(
        "SELECT book_id AS bookId, SUM(duration_seconds) AS totalDuration " +
            "FROM reading_session " +
            "GROUP BY book_id",
    )
    fun getBookTotals(): Flow<List<BookDurationTuple>>

    @Query(
        "SELECT chapter_index AS chapterIndex, " +
            "SUM(duration_seconds) AS totalSeconds, " +
            "MIN(started_at) AS firstVisitedAt, " +
            "MAX(ended_at) AS lastVisitedAt " +
            "FROM reading_session " +
            "WHERE book_id = :bookId " +
            "GROUP BY chapter_index " +
            "ORDER BY chapter_index ASC",
    )
    fun getChapterTotals(bookId: Long): Flow<List<ChapterTotalTuple>>

    @Query(
        "SELECT * FROM reading_session " +
            "WHERE date_key BETWEEN :start AND :end " +
            "ORDER BY started_at DESC",
    )
    fun getSessionsInRange(start: Int, end: Int): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_session WHERE deleted = 0")
    suspend fun getAllSessions(): List<ReadingSessionEntity>

    @androidx.room.Upsert
    suspend fun upsertSession(session: ReadingSessionEntity)

    @Query("DELETE FROM reading_session")
    suspend fun deleteAllSessions()
}

data class DailyTotalTuple(val dateKey: Int, val total: Long)
data class HourlyTotalTuple(val hour: Int, val total: Long)
data class ChapterTotalTuple(
    val chapterIndex: Int,
    val totalSeconds: Long,
    val firstVisitedAt: Long,
    val lastVisitedAt: Long,
)
data class BookDurationTuple(val bookId: Long, val totalDuration: Long)
