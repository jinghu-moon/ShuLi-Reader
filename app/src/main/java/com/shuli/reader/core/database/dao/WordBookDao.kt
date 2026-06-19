package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shuli.reader.core.database.entity.WordBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordBookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: WordBookEntity): Long

    @Update
    suspend fun update(word: WordBookEntity)

    @Delete
    suspend fun delete(word: WordBookEntity)

    @Query("SELECT * FROM word_book WHERE id = :id")
    suspend fun getById(id: Long): WordBookEntity?

    @Query("SELECT * FROM word_book WHERE word = :word LIMIT 1")
    suspend fun getByWord(word: String): WordBookEntity?

    @Query("SELECT * FROM word_book WHERE word = :word LIMIT 1")
    fun getByWordFlow(word: String): Flow<WordBookEntity?>

    @Query("SELECT * FROM word_book ORDER BY added_at DESC")
    fun getAllFlow(): Flow<List<WordBookEntity>>

    @Query("SELECT * FROM word_book ORDER BY added_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(offset: Int, limit: Int = 50): List<WordBookEntity>

    @Query("SELECT * FROM word_book WHERE word LIKE :prefix || '%' ORDER BY word ASC LIMIT :limit")
    suspend fun searchByPrefix(prefix: String, limit: Int = 20): List<WordBookEntity>

    @Query("UPDATE word_book SET last_review_at = :timestamp, review_count = review_count + 1 WHERE id = :id")
    suspend fun markReviewed(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE word_book SET mastery_level = :level WHERE id = :id")
    suspend fun setMasteryLevel(id: Long, level: Int)

    @Query("UPDATE word_book SET exported_to_anki = 1 WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>)

    @Query("SELECT * FROM word_book WHERE exported_to_anki = 0 ORDER BY added_at DESC")
    suspend fun getUnexported(): List<WordBookEntity>

    @Query("DELETE FROM word_book")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM word_book")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM word_book")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM word_book WHERE word = :word)")
    suspend fun exists(word: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM word_book WHERE word = :word)")
    fun existsFlow(word: String): Flow<Boolean>
}
