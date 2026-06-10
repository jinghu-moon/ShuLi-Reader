package com.shuli.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_session",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["date_key"]),
        Index(value = ["book_id"]),
        Index(value = ["book_id", "chapter_index"]),
        Index(value = ["started_at"]),
    ],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "book_id")
    val bookId: Long,

    @ColumnInfo(name = "chapter_index")
    val chapterIndex: Int,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "ended_at")
    val endedAt: Long,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long,

    @ColumnInfo(name = "date_key")
    val dateKey: Int,

    @ColumnInfo(name = "hour")
    val hour: Int,

    @ColumnInfo(name = "is_dirty")
    val isDirty: Boolean = true,

    @ColumnInfo(name = "version")
    val version: Int = 1,

    @ColumnInfo(name = "synced_version")
    val syncedVersion: Int = 0,

    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,

    @ColumnInfo(name = "merge_source")
    val mergeSource: String? = null,
)
