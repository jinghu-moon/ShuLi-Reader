package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shuli.reader.core.database.entity.DictMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DictMetaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dict: DictMetaEntity): Long

    @Update
    suspend fun update(dict: DictMetaEntity)

    @Delete
    suspend fun delete(dict: DictMetaEntity)

    @Query("SELECT * FROM dict_meta WHERE id = :id")
    suspend fun getById(id: Long): DictMetaEntity?

    @Query("SELECT * FROM dict_meta WHERE dict_key = :dictKey")
    suspend fun getByKey(dictKey: String): DictMetaEntity?

    @Query("SELECT * FROM dict_meta ORDER BY priority ASC, display_name ASC")
    fun getAllFlow(): Flow<List<DictMetaEntity>>

    @Query("SELECT * FROM dict_meta WHERE is_enabled = 1 ORDER BY priority ASC")
    suspend fun getEnabledDicts(): List<DictMetaEntity>

    @Query("SELECT * FROM dict_meta WHERE is_enabled = 1 ORDER BY priority ASC")
    fun getEnabledDictsFlow(): Flow<List<DictMetaEntity>>

    @Query("UPDATE dict_meta SET is_enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE dict_meta SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: Long, priority: Int)

    @Query("UPDATE dict_meta SET last_used_at = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM dict_meta")
    suspend fun getCount(): Int

    @Query("DELETE FROM dict_meta WHERE dict_key = :dictKey")
    suspend fun deleteByKey(dictKey: String)
}
