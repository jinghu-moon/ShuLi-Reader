package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.ReadingProgressDao
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.TxtParser
import com.shuli.reader.core.parser.model.BookContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val txtParser: TxtParser,
    private val epubParser: EpubParser,
    private val booksDir: java.io.File,
) {
    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    fun getBookById(id: Long): Flow<BookEntity?> = bookDao.getBookById(id)

    fun searchBooks(query: String): Flow<List<BookEntity>> = bookDao.searchBooks(query)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun deleteBook(id: Long) = bookDao.deleteBookById(id)

    suspend fun updateLastReadTime(bookId: Long) {
        bookDao.updateLastReadTime(bookId, System.currentTimeMillis())
    }

    suspend fun updateReadingProgress(bookId: Long, progress: Float) {
        bookDao.updateReadingProgress(bookId, progress)
    }

    suspend fun getReadingDuration(bookId: Long): Long {
        return readingProgressDao.getReadingDurationByBookId(bookId) ?: 0L
    }

    fun getReadingDurations(): Flow<Map<Long, Long>> {
        return readingProgressDao.getAllReadingDurations().map { tuples ->
            tuples.associate { it.bookId to it.totalDuration }
        }
    }

    fun getTodayReadingTime(): Flow<Long?> {
        val todayStart = getTodayStartTimestamp()
        return readingProgressDao.getTodayTotalReadingTime(todayStart)
    }

    suspend fun parseBookContent(file: File): BookContent {
        return when {
            file.name.endsWith(".txt", ignoreCase = true) -> txtParser.parse(file)
            file.name.endsWith(".epub", ignoreCase = true) -> epubParser.parse(file)
            else -> throw IllegalArgumentException("Unsupported file format: ${file.name}")
        }
    }

    suspend fun importBook(file: File, copyToAppDir: Boolean = true): Long = withContext(Dispatchers.IO) {
        val targetFilePath = if (copyToAppDir) {
            File(booksDir, file.name).absolutePath
        } else {
            file.absolutePath
        }

        val existingBook = bookDao.getBookByFilePath(targetFilePath)
        if (existingBook != null) {
            throw BookAlreadyExistsException(existingBook.id, existingBook.title)
        }

        var bookCoverPath: String? = null
        val (title, author) = when {
            file.name.endsWith(".txt", ignoreCase = true) -> {
                txtParser.parseMetadata(file)
            }
            file.name.endsWith(".epub", ignoreCase = true) -> {
                val (t, a, coverPathInZip) = epubParser.parseMetadata(file)
                if (coverPathInZip != null) {
                    try {
                        java.util.zip.ZipFile(file).use { zip ->
                            val coverEntry = zip.getEntry(coverPathInZip)
                            if (coverEntry != null) {
                                val coversDir = File(booksDir.parentFile, "covers")
                                coversDir.mkdirs()
                                val ext = coverPathInZip.substringAfterLast(".", "jpg")
                                val coverFile = File(coversDir, "${file.nameWithoutExtension}_cover.$ext")
                                zip.getInputStream(coverEntry).use { input ->
                                    coverFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                bookCoverPath = coverFile.absolutePath
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                t to a
            }
            else -> throw IllegalArgumentException("Unsupported file format: ${file.name}")
        }

        val targetFile = if (copyToAppDir) {
            booksDir.mkdirs()
            val target = File(booksDir, file.name)
            file.copyTo(target, overwrite = true)
            target
        } else {
            file
        }

        val bookEntity = BookEntity(
            title = title,
            author = author,
            filePath = targetFile.absolutePath,
            fileType = if (file.name.endsWith(".epub", ignoreCase = true)) "EPUB" else "TXT",
            fileSize = file.length(),
            coverPath = bookCoverPath,
            lastReadTime = null,
            addedTime = System.currentTimeMillis(),
            readingProgress = 0f,
        )

        bookDao.insertBook(bookEntity)
    }

    private fun getTodayStartTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

class BookAlreadyExistsException(val bookId: Long, val bookTitle: String) : Exception("书籍《$bookTitle》已在书架中")
