package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.shuli.reader.core.database.entity.ReaderPresetEntity
import kotlinx.coroutines.flow.Flow

/**
 * 阅读器预设 DAO
 */
@Dao
interface ReaderPresetDao {
    @Query("SELECT * FROM reader_preset ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReaderPresetEntity>>

    @Query("SELECT * FROM reader_preset ORDER BY createdAt DESC")
    suspend fun getAll(): List<ReaderPresetEntity>

    @Query("SELECT * FROM reader_preset WHERE id = :id")
    suspend fun getById(id: Long): ReaderPresetEntity?

    @Insert
    suspend fun insert(entity: ReaderPresetEntity): Long

    @Delete
    suspend fun delete(entity: ReaderPresetEntity)

    @Update
    suspend fun update(entity: ReaderPresetEntity)

    @Query("DELETE FROM reader_preset WHERE id = :id")
    suspend fun deleteById(id: Long)
}
