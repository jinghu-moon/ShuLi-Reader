package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    fun getProgressByBookId(bookId: Long): Flow<ReadingProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ReadingProgressEntity): Long

    @Query("UPDATE reading_progress SET pageIndex = :pageIndex, position = :position, readTime = :readTime, updatedTime = :updatedTime WHERE bookId = :bookId")
    suspend fun updateProgress(
        bookId: Long,
        pageIndex: Int,
        position: Int,
        readTime: Long,
        updatedTime: Long,
    )

    @Query("SELECT SUM(readTime) FROM reading_progress WHERE bookId = :bookId")
    suspend fun getReadingDurationByBookId(bookId: Long): Long?

    @Query("SELECT bookId, SUM(readTime) as totalDuration FROM reading_progress GROUP BY bookId")
    fun getAllReadingDurations(): Flow<List<BookDurationTuple>>

    @Query("SELECT SUM(readTime) FROM reading_progress WHERE updatedTime >= :todayStart")
    fun getTodayTotalReadingTime(todayStart: Long): Flow<Long?>

    /** T-06: 查询脏进度（同步用） */
    @Query("SELECT * FROM reading_progress WHERE isDirty = 1 AND deleted = 0")
    suspend fun queryDirty(): List<ReadingProgressEntity>

    /** T-06: 查询所有未删除进度（同步用） */
    @Query("SELECT * FROM reading_progress WHERE deleted = 0")
    suspend fun queryAllActive(): List<ReadingProgressEntity>
}

data class BookDurationTuple(
    val bookId: Long,
    val totalDuration: Long,
)
