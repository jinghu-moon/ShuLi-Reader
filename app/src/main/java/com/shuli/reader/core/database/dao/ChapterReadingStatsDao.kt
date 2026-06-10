package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuli.reader.core.database.entity.ChapterReadingStatsEntity
import kotlinx.coroutines.flow.Flow

/**
 * 章节阅读统计 DAO。
 */
@Dao
interface ChapterReadingStatsDao {

    /** 获取某书所有章节的阅读统计（响应式） */
    @Query("SELECT * FROM chapter_reading_stats WHERE book_id = :bookId ORDER BY chapter_index ASC")
    fun getStatsByBookId(bookId: Long): Flow<List<ChapterReadingStatsEntity>>

    /** 获取某书某章的阅读统计 */
    @Query("SELECT * FROM chapter_reading_stats WHERE book_id = :bookId AND chapter_index = :chapterIndex")
    suspend fun getStat(bookId: Long, chapterIndex: Int): ChapterReadingStatsEntity?

    /** 标记章节已访问（首次访问时调用） */
    @Query("""
        UPDATE chapter_reading_stats
        SET visited = 1, last_visited_at = :now
        WHERE book_id = :bookId AND chapter_index = :chapterIndex
    """)
    suspend fun markVisited(bookId: Long, chapterIndex: Int, now: Long)

    /** 插入或更新（UPSERT） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: ChapterReadingStatsEntity)

    /** 确保章节统计行存在（不存在则插入默认值） */
    suspend fun ensureExists(bookId: Long, chapterIndex: Int) {
        val existing = getStat(bookId, chapterIndex)
        if (existing == null) {
            upsert(
                ChapterReadingStatsEntity(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                )
            )
        }
    }

    /** 标记章节已访问，不存在则先创建 */
    suspend fun markVisitedOrCreate(bookId: Long, chapterIndex: Int) {
        ensureExists(bookId, chapterIndex)
        val now = System.currentTimeMillis()
        // 首次访问：同时设置 firstVisitedAt
        val existing = getStat(bookId, chapterIndex)
        if (existing != null && existing.firstVisitedAt == 0L) {
            upsert(existing.copy(visited = true, firstVisitedAt = now, lastVisitedAt = now))
        } else {
            markVisited(bookId, chapterIndex, now)
        }
    }

    /** 删除某书所有章节统计 */
    @Query("DELETE FROM chapter_reading_stats WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}
