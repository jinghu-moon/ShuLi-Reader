package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    fun getProgressByBookId(bookId: Long): Flow<ReadingProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ReadingProgressEntity): Long

    @Query("UPDATE reading_progress SET pageIndex = :pageIndex, position = :position, updatedTime = :updatedTime, chapterIndex = :chapterIndex, themeBackgroundColor = :themeBackgroundColor WHERE bookId = :bookId")
    suspend fun updateProgress(
        bookId: Long,
        pageIndex: Int,
        position: Int,
        updatedTime: Long,
        chapterIndex: Int = 0,
        themeBackgroundColor: Int = 0,
    )

    /** T-06: 查询脏进度（同步用） */
    @Query("SELECT * FROM reading_progress WHERE isDirty = 1 AND deleted = 0")
    suspend fun queryDirty(): List<ReadingProgressEntity>

    /** T-06: 查询所有未删除进度（同步用） */
    @Query("SELECT * FROM reading_progress WHERE deleted = 0")
    suspend fun queryAllActive(): List<ReadingProgressEntity>

    /** 清空所有阅读进度（导入用） */
    @Query("DELETE FROM reading_progress")
    suspend fun deleteAllProgress()

    /** Upsert 阅读进度（导入合并用） */
    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    /** §11.1.1.1: 加载 SnapshotDigest（T0 fallback 用） */
    @Query("SELECT bookId, chapterIndex, pageIndex, position, themeBackgroundColor, updatedTime FROM reading_progress WHERE bookId = :bookId AND deleted = 0")
    suspend fun loadSnapshotDigest(bookId: Long): SnapshotDigestTuple?
}

/** §11.1.1.1: SnapshotDigest 查询结果 */
data class SnapshotDigestTuple(
    val bookId: Long,
    val chapterIndex: Int,
    val pageIndex: Int,
    val position: Int,
    val themeBackgroundColor: Int,
    val updatedTime: Long,
)
