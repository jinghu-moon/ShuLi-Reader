package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuli.reader.core.database.entity.BookReaderPrefsEntity

/**
 * 本书级偏好 DAO。
 */
@Dao
interface BookReaderPrefsDao {

    @Query("SELECT * FROM book_reader_prefs WHERE book_id = :bookId")
    suspend fun getByBookId(bookId: Long): BookReaderPrefsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookReaderPrefsEntity)

    @Query("DELETE FROM book_reader_prefs WHERE book_id = :bookId")
    suspend fun deleteByBookId(bookId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM book_reader_prefs WHERE book_id = :bookId)")
    suspend fun exists(bookId: Long): Boolean
}
