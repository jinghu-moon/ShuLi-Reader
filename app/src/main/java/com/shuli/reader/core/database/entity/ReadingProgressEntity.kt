package com.shuli.reader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class ReadingProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val pageIndex: Int,
    val position: Int,
    val readTime: Long,  // 阅读时长（秒）
    val updatedTime: Long,
    // === 同步字段（T-06）===
    val isDirty: Boolean = true,
    val version: Int = 1,
    val syncedVersion: Int = 0,
    val deleted: Boolean = false,
    val updatedAt: Long = 0L,
    val mergeSource: String? = null,
)
