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

    @Query("SELECT * FROM word_book ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<WordBookEntity>>

    @Query("SELECT * FROM word_book ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(offset: Int, limit: Int = 50): List<WordBookEntity>

    @Query("SELECT * FROM word_book WHERE word LIKE :prefix || '%' ORDER BY word ASC LIMIT :limit")
    suspend fun searchByPrefix(prefix: String, limit: Int = 20): List<WordBookEntity>

    @Query("UPDATE word_book SET lastReviewAt = :timestamp, reviewCount = reviewCount + 1 WHERE id = :id")
    suspend fun markReviewed(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE word_book SET masteryLevel = :level WHERE id = :id")
    suspend fun setMasteryLevel(id: Long, level: Int)

    @Query("UPDATE word_book SET exportedToAnki = 1 WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>)

    @Query("SELECT * FROM word_book WHERE exportedToAnki = 0 ORDER BY addedAt DESC")
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
