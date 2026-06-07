package com.shuli.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_history",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("book_id"),
    ],
)
data class ReadingHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "book_id")
    val bookId: Long,

    @ColumnInfo(name = "read_count")
    val readCount: Int,

    @ColumnInfo(name = "finished_at")
    val finishedAt: Long,

    @ColumnInfo(name = "reading_progress")
    val readingProgress: Float = 1f,

    @ColumnInfo(name = "reading_duration_minutes")
    val readingDurationMinutes: Long = 0L,
)
