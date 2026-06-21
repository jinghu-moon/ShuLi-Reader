package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuli.reader.core.database.entity.EditDeltaEntity

@Dao
interface EditDeltaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(delta: EditDeltaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deltas: List<EditDeltaEntity>)

    @Query("SELECT * FROM edit_delta WHERE book_id = :bookId ORDER BY timestamp ASC")
    suspend fun getByBookId(bookId: Long): List<EditDeltaEntity>

    @Query("DELETE FROM edit_delta WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: Long)

    @Query("DELETE FROM edit_delta WHERE book_id = :bookId AND chapter_index = :chapterIndex AND char_start = :charStart AND timestamp = :timestamp")
    suspend fun deleteByPosition(bookId: Long, chapterIndex: Int, charStart: Int, timestamp: Long)

    @Query("DELETE FROM edit_delta")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM edit_delta WHERE book_id = :bookId")
    suspend fun countByBookId(bookId: Long): Int
}
