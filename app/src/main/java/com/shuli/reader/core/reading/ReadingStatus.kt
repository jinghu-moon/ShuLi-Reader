package com.shuli.reader.core.reading

import com.shuli.reader.core.database.entity.BookEntity

enum class ReadingStatus {
    WANT_TO_READ,
    READING,
    PAUSED,
    FINISHED,
    ABANDONED;

    companion object {
        fun fromDb(value: String?): ReadingStatus =
            entries.firstOrNull { it.name == value } ?: WANT_TO_READ
    }
}

fun BookEntity.transitionTo(
    newStatus: ReadingStatus,
    now: Long = System.currentTimeMillis(),
): BookEntity {
    val currentStatus = ReadingStatus.fromDb(readingStatus)
    val shouldIncrementReadCount =
        currentStatus == ReadingStatus.FINISHED &&
            (newStatus == ReadingStatus.READING || newStatus == ReadingStatus.WANT_TO_READ)

    return this.copy(
        readingStatus = newStatus.name,
        readCount = if (shouldIncrementReadCount) readCount + 1 else readCount,
        isDirty = true,
        version = version + 1,
        updatedAt = now,
    )
}
