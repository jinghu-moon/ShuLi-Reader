package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shuli.reader.core.database.entity.BookChapterEntity

@Dao
interface BookChapterDao {

    @Query("SELECT * FROM book_chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    suspend fun getChapters(bookId: Long): List<BookChapterEntity>

    @Query("SELECT * FROM book_chapters WHERE bookId = :bookId AND chapterIndex = :index LIMIT 1")
    suspend fun getChapter(bookId: Long, index: Int): BookChapterEntity?

    @Query("DELETE FROM book_chapters WHERE bookId = :bookId")
    suspend fun deleteChapters(bookId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<BookChapterEntity>)

    @Transaction
    suspend fun replaceChapters(bookId: Long, chapters: List<BookChapterEntity>) {
        deleteChapters(bookId)
        if (chapters.isNotEmpty()) {
            insertChapters(chapters)
        }
    }

    @Query("SELECT COUNT(*) FROM book_chapters WHERE bookId = :bookId")
    suspend fun countChapters(bookId: Long): Int
}
