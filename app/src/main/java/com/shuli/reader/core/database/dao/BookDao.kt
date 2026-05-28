package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.shuli.reader.core.database.entity.BookContentIndexEntity
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookShelfRow
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("""
        SELECT * FROM books
        ORDER BY COALESCE(lastReadTime, addedTime) DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getBooksPage(limit: Int, offset: Int): Flow<List<BookEntity>>

    @Query("""
        SELECT
            id,
            title,
            author,
            filePath,
            fileType,
            fileSize,
            coverPath,
            lastReadTime,
            readingProgress,
            isFavorite,
            customCoverPaletteIndex,
            folderId,
            pinnedSlot
        FROM books
        ORDER BY COALESCE(lastReadTime, addedTime) DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getBookRowsPage(limit: Int, offset: Int): Flow<List<BookShelfRow>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE filePath = :filePath LIMIT 1")
    suspend fun getBookByFilePath(filePath: String): BookEntity?

    @Query("SELECT * FROM books WHERE filePath LIKE '%' || :fileName AND fileSize = :fileSize")
    suspend fun getBooksByFileNameAndSize(fileName: String, fileSize: Long): List<BookEntity>

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    /**
     * 使用 FTS4 全文搜索书籍（标题和作者）
     * 比 LIKE 查询更高效，支持中文分词
     */
    @Query("""
        SELECT books.* FROM books
        JOIN books_fts ON books.id = books_fts.rowid
        WHERE books_fts MATCH :query
    """)
    fun searchBooksFts(query: String): Flow<List<BookEntity>>

    @Query("""
        SELECT books.* FROM books
        JOIN books_fts ON books.id = books_fts.rowid
        WHERE books_fts MATCH :query
        ORDER BY COALESCE(books.lastReadTime, books.addedTime) DESC
        LIMIT :limit OFFSET :offset
    """)
    fun searchBooksFtsPage(query: String, limit: Int, offset: Int): Flow<List<BookEntity>>

    @Query("""
        SELECT
            books.id,
            books.title,
            books.author,
            books.filePath,
            books.fileType,
            books.fileSize,
            books.coverPath,
            books.lastReadTime,
            books.readingProgress,
            books.isFavorite,
            books.customCoverPaletteIndex,
            books.folderId,
            books.pinnedSlot
        FROM books
        JOIN books_fts ON books.id = books_fts.rowid
        WHERE books_fts MATCH :query
        ORDER BY COALESCE(books.lastReadTime, books.addedTime) DESC
        LIMIT :limit OFFSET :offset
    """)
    fun searchBookRowsFtsPage(query: String, limit: Int, offset: Int): Flow<List<BookShelfRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)

    @Query("UPDATE books SET lastReadTime = :time WHERE id = :bookId")
    suspend fun updateLastReadTime(bookId: Long, time: Long)

    @Query("UPDATE books SET readingProgress = :progress WHERE id = :bookId")
    suspend fun updateReadingProgress(bookId: Long, progress: Float)

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :bookId")
    suspend fun updateFavoriteStatus(bookId: Long, isFavorite: Boolean)

    @Query("UPDATE books SET customCoverPaletteIndex = :paletteIndex WHERE id = :bookId")
    suspend fun updateCustomCoverPaletteIndex(bookId: Long, paletteIndex: Int?)

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY lastReadTime DESC")
    fun getFavoriteBooks(): Flow<List<BookEntity>>

    @Query("""
        UPDATE books SET
            durByteOffset = :byteOffset,
            durChapterTitle = :chapterTitle,
            readingProgress = :progress
        WHERE id = :bookId
    """)
    suspend fun updateReadingPosition(
        bookId: Long,
        byteOffset: Long,
        chapterTitle: String?,
        progress: Float,
    )

    @Query("UPDATE books SET totalChapterNum = :totalChapters WHERE id = :bookId")
    suspend fun updateTotalChapters(bookId: Long, totalChapters: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookContentIndex(rows: List<BookContentIndexEntity>)

    @Query("DELETE FROM book_content_index WHERE bookId = :bookId")
    suspend fun deleteBookContentIndex(bookId: Long)

    @Transaction
    suspend fun replaceBookContentIndex(bookId: Long, rows: List<BookContentIndexEntity>) {
        deleteBookContentIndex(bookId)
        if (rows.isNotEmpty()) {
            insertBookContentIndex(rows)
        }
    }

    @Query("SELECT COUNT(*) FROM book_content_index WHERE bookId = :bookId")
    suspend fun countBookContentIndex(bookId: Long): Int

    @Query("""
        SELECT * FROM book_content_index
        WHERE bookId = :bookId
            AND content LIKE '%' || :query || '%'
        ORDER BY chapterIndex ASC
    """)
    suspend fun searchBookContentIndex(bookId: Long, query: String): List<BookContentIndexEntity>

    // --- Folder & Grouping Support ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: com.shuli.reader.core.database.entity.FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: com.shuli.reader.core.database.entity.FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: com.shuli.reader.core.database.entity.FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): com.shuli.reader.core.database.entity.FolderEntity?

    @Query("SELECT * FROM folders ORDER BY id ASC")
    fun getAllFolders(): Flow<List<com.shuli.reader.core.database.entity.FolderEntity>>

    @Query("UPDATE books SET folderId = :folderId WHERE id IN (:bookIds)")
    suspend fun moveBooksToFolder(bookIds: List<Long>, folderId: Long?)

    @Query("UPDATE books SET pinnedSlot = :slot WHERE id = :bookId")
    suspend fun updateBookPinnedSlot(bookId: Long, slot: Int?)

    @Query("UPDATE folders SET pinnedSlot = :slot WHERE id = :folderId")
    suspend fun updateFolderPinnedSlot(folderId: Long, slot: Int?)

    @Query("UPDATE books SET pinnedSlot = NULL")
    suspend fun clearAllBookPinnedSlots()

    @Query("UPDATE folders SET pinnedSlot = NULL")
    suspend fun clearAllFolderPinnedSlots()
}
