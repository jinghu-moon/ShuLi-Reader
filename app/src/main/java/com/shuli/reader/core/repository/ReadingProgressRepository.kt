package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.ReadingProgressDao
import com.shuli.reader.core.reading.ReadingStatus
import com.shuli.reader.core.reading.transitionTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 阅读进度 + 时长统计 + 收藏 + 元数据 + 阅读状态迁移。
 * Actor: 阅读进度数据工程师、书架产品
 */
class ReadingProgressRepository(
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readingHistoryDao: com.shuli.reader.core.database.dao.ReadingHistoryDao? = null,
) {
    suspend fun updateBookPinnedSlot(bookId: Long, slot: Int?) {
        bookDao.updateBookPinnedSlot(bookId, slot)
    }

    suspend fun updateFolderPinnedSlot(folderId: Long, slot: Int?) {
        bookDao.updateFolderPinnedSlot(folderId, slot)
    }

    suspend fun clearAllPinnedSlots() {
        bookDao.clearAllBookPinnedSlots()
        bookDao.clearAllFolderPinnedSlots()
    }

    suspend fun updateLastReadTime(bookId: Long) {
        bookDao.updateLastReadTime(bookId, System.currentTimeMillis())
    }

    suspend fun updateReadingProgress(bookId: Long, progress: Float) {
        bookDao.updateReadingProgress(bookId, progress)
    }

    suspend fun updateReadingPosition(
        bookId: Long,
        byteOffset: Long,
        chapterTitle: String?,
        progress: Float,
    ) {
        bookDao.updateReadingPosition(
            bookId = bookId,
            byteOffset = byteOffset,
            chapterTitle = chapterTitle,
            progress = progress,
        )
    }

    suspend fun toggleFavorite(bookId: Long) {
        val book = bookDao.getBookById(bookId).first()
        book?.let {
            bookDao.updateFavoriteStatus(bookId, !it.isFavorite)
        }
    }

    suspend fun setFavorite(bookId: Long, isFavorite: Boolean) {
        bookDao.updateFavoriteStatus(bookId, isFavorite)
    }

    suspend fun setCustomCoverPaletteIndex(bookId: Long, paletteIndex: Int?) {
        bookDao.updateCustomCoverPaletteIndex(bookId, paletteIndex)
    }

    suspend fun getReadingDuration(bookId: Long): Long {
        return readingProgressDao.getReadingDurationByBookId(bookId) ?: 0L
    }

    fun getReadingDurations(): Flow<Map<Long, Long>> {
        return readingProgressDao.getAllReadingDurations().map { tuples ->
            tuples.associate { it.bookId to it.totalDuration }
        }.flowOn(Dispatchers.Default)
    }

    fun getTodayReadingTime(): Flow<Long?> {
        val todayStart = getTodayStartTimestamp()
        return readingProgressDao.getTodayTotalReadingTime(todayStart)
    }

    private fun getTodayStartTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // ── 阅读状态迁移（P0）──────────────────────────────────

    suspend fun updateReadingStatus(
        bookId: Long,
        newStatus: ReadingStatus,
    ): ReadingStatusUpdateResult? {
        val book = bookDao.getBookByIdSync(bookId) ?: return null
        val previousStatus = ReadingStatus.fromDb(book.readingStatus)
        val updatedBook = book.transitionTo(newStatus)
        bookDao.updateBook(updatedBook)
        if (newStatus == ReadingStatus.FINISHED && previousStatus != ReadingStatus.FINISHED) {
            readingHistoryDao?.insert(
                com.shuli.reader.core.database.entity.ReadingHistoryEntity(
                    bookId = bookId,
                    readCount = updatedBook.readCount,
                    finishedAt = System.currentTimeMillis(),
                ),
            )
        }
        return ReadingStatusUpdateResult(
            previousStatus = previousStatus,
            updatedBook = updatedBook,
        )
    }

    suspend fun markOpenedForReading(bookId: Long) {
        val book = bookDao.getBookByIdSync(bookId) ?: return
        if (ReadingStatus.fromDb(book.readingStatus) == ReadingStatus.WANT_TO_READ) {
            bookDao.updateBook(book.transitionTo(ReadingStatus.READING))
        }
    }

    suspend fun updateReadingPositionAndMaybeFinish(
        bookId: Long,
        byteOffset: Long,
        chapterTitle: String?,
        progress: Float,
    ) {
        bookDao.updateReadingPosition(
            bookId = bookId,
            byteOffset = byteOffset,
            chapterTitle = chapterTitle,
            progress = progress,
        )

        if (progress >= 0.99f) {
            val book = bookDao.getBookByIdSync(bookId) ?: return
            if (ReadingStatus.fromDb(book.readingStatus) != ReadingStatus.FINISHED) {
                val updatedBook = book.transitionTo(ReadingStatus.FINISHED)
                bookDao.updateBook(updatedBook)
                readingHistoryDao?.insert(
                    com.shuli.reader.core.database.entity.ReadingHistoryEntity(
                        bookId = bookId,
                        readCount = updatedBook.readCount,
                        finishedAt = System.currentTimeMillis(),
                        readingProgress = progress,
                    ),
                )
            }
        }
    }

    suspend fun recordReadingHistory(bookId: Long, readCount: Int) {
        readingHistoryDao?.insert(
            com.shuli.reader.core.database.entity.ReadingHistoryEntity(
                bookId = bookId,
                readCount = readCount,
                finishedAt = System.currentTimeMillis(),
            ),
        )
    }
}

data class ReadingStatusUpdateResult(
    val previousStatus: ReadingStatus,
    val updatedBook: com.shuli.reader.core.database.entity.BookEntity,
)
