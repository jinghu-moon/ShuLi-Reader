package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.shuli.reader.core.database.entity.BookContentIndexEntity
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookShelfRow
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    suspend fun getAllBooksSync(): List<BookEntity>

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
            pinnedSlot,
            readingStatus,
            readCount
        FROM books
        ORDER BY COALESCE(lastReadTime, addedTime) DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getBookRowsPage(limit: Int, offset: Int): Flow<List<BookShelfRow>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookByIdSync(id: Long): BookEntity?

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
            books.pinnedSlot,
            books.readingStatus,
            books.readCount
        FROM books
        JOIN books_fts ON books.id = books_fts.rowid
        WHERE books_fts MATCH :query
        ORDER BY COALESCE(books.lastReadTime, books.addedTime) DESC
        LIMIT :limit OFFSET :offset
    """)
    fun searchBookRowsFtsPage(query: String, limit: Int, offset: Int): Flow<List<BookShelfRow>>

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
            books.pinnedSlot,
            books.readingStatus,
            books.readCount
        FROM books
        INNER JOIN book_tag_cross_ref r ON books.id = r.book_id
        INNER JOIN tags t ON r.tag_id = t.id
        WHERE t.name = :tagName
        ORDER BY COALESCE(books.lastReadTime, books.addedTime) DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getBookRowsByTagPage(tagName: String, limit: Int, offset: Int): Flow<List<BookShelfRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    /** Upsert 书籍（导入合并用） */
    @Upsert
    suspend fun upsertBook(book: BookEntity)

    /** 清空所有书籍（导入用） */
    @Query("DELETE FROM books")
    suspend fun deleteAllBooks()

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

    @Query("""
        SELECT b.id, b.title, b.author, b.fileType, b.readingStatus,
               b.totalChapterNum, b.estimatedTotalChars,
               COALESCE(rs.totalDuration, 0) AS totalDuration
        FROM books b
        LEFT JOIN (
            SELECT book_id, SUM(duration_seconds) AS totalDuration
            FROM reading_session GROUP BY book_id
        ) rs ON b.id = rs.book_id
    """)
    fun getAllBooksWithDuration(): Flow<List<BookWithDurationTuple>>

    @Query("SELECT bookId AS bookId, COUNT(*) AS count FROM bookmarks WHERE deleted = 0 GROUP BY bookId")
    fun getBookmarkCounts(): Flow<List<BookCountTuple>>

    @Query("SELECT bookId AS bookId, COUNT(*) AS count FROM notes WHERE deleted = 0 GROUP BY bookId")
    fun getNoteCounts(): Flow<List<BookCountTuple>>

    // --- Global Full-text Search ---

    @Query("""
        SELECT ci.id, ci.bookId, ci.chapterIndex, ci.chapterTitle, ci.content,
               ci.byteStart, ci.charset, ci.utf16ToByteBlob,
               b.title AS bookTitle,
               b.author AS author
        FROM book_content_index ci
        INNER JOIN books b ON ci.bookId = b.id
        WHERE ci.content LIKE '%' || :query || '%'
        ORDER BY COALESCE(b.lastReadTime, b.addedTime) DESC, ci.chapterIndex ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchGlobalContent(query: String, limit: Int, offset: Int): List<GlobalContentMatch>

    @Query("""
        SELECT ci.id, ci.bookId, ci.chapterIndex, ci.chapterTitle, ci.content,
               ci.byteStart, ci.charset, ci.utf16ToByteBlob,
               b.title AS bookTitle,
               b.author AS author
        FROM book_content_index ci
        INNER JOIN books b ON ci.bookId = b.id
        WHERE ci.bookId IN (:bookIds)
            AND ci.content LIKE '%' || :query || '%'
        ORDER BY COALESCE(b.lastReadTime, b.addedTime) DESC, ci.chapterIndex ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchGlobalContentInBooks(query: String, bookIds: List<Long>, limit: Int, offset: Int): List<GlobalContentMatch>

    @Query("""
        SELECT COUNT(*) FROM book_content_index ci
        INNER JOIN books b ON ci.bookId = b.id
        WHERE ci.content LIKE '%' || :query || '%'
    """)
    suspend fun countGlobalContentMatches(query: String): Int

    @Query("SELECT DISTINCT bookId FROM book_content_index")
    suspend fun getIndexedBookIds(): List<Long>

    @Query("SELECT id FROM books")
    suspend fun getAllBookIds(): List<Long>
}

data class BookWithDurationTuple(
    val id: Long,
    val title: String,
    val author: String?,
    val fileType: String,
    val readingStatus: String,
    val totalChapterNum: Int,
    val estimatedTotalChars: Long,
    val totalDuration: Long,
)

data class BookCountTuple(
    val bookId: Long,
    val count: Int,
)

data class GlobalContentMatch(
    val id: Long,
    val bookId: Long,
    val chapterIndex: Int,
    val chapterTitle: String,
    val content: String,
    val byteStart: Long,
    val charset: String,
    val utf16ToByteBlob: ByteArray,
    val bookTitle: String,
    val author: String?,
)
