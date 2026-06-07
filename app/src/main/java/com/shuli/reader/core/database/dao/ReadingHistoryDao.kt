package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.shuli.reader.core.database.entity.ReadingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {
    @Query("""
        SELECT * FROM reading_history
        WHERE book_id = :bookId
        ORDER BY finished_at ASC
    """)
    fun getHistoryForBook(bookId: Long): Flow<List<ReadingHistoryEntity>>

    @Query("""
        SELECT rh.* FROM reading_history rh
        INNER JOIN books b ON rh.book_id = b.id
        WHERE b.readCount > 1
        ORDER BY rh.finished_at DESC
        LIMIT :limit
    """)
    fun getRereadHistory(limit: Int = 20): Flow<List<ReadingHistoryEntity>>

    @Insert
    suspend fun insert(entry: ReadingHistoryEntity): Long

    @Query("DELETE FROM reading_history WHERE book_id = :bookId")
    suspend fun deleteForBook(bookId: Long)
}
