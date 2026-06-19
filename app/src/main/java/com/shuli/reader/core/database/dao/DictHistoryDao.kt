package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuli.reader.core.database.entity.DictHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DictHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: DictHistoryEntity): Long

    @Delete
    suspend fun delete(history: DictHistoryEntity)

    @Query("SELECT * FROM dict_history WHERE id = :id")
    suspend fun getById(id: Long): DictHistoryEntity?

    @Query("SELECT * FROM dict_history ORDER BY queried_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<DictHistoryEntity>

    @Query("SELECT * FROM dict_history ORDER BY queried_at DESC LIMIT :limit")
    fun getRecentFlow(limit: Int = 100): Flow<List<DictHistoryEntity>>

    @Query("SELECT DISTINCT word FROM dict_history ORDER BY queried_at DESC LIMIT :limit")
    suspend fun getRecentWords(limit: Int = 50): List<String>

    @Query("DELETE FROM dict_history")
    suspend fun clearAll()

    @Query("DELETE FROM dict_history WHERE queried_at < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM dict_history")
    suspend fun getCount(): Int
}
