package com.shuli.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每章阅读统计实体。
 *
 * 记录每章的已读状态、累计阅读时长、访问时间戳。
 * 与 BookEntity 通过 bookId 外键关联，级联删除。
 */
@Entity(
    tableName = "chapter_reading_stats",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["book_id", "chapter_index"], unique = true),
        Index(value = ["book_id"]),
    ],
)
data class ChapterReadingStatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "book_id")
    val bookId: Long,
    @ColumnInfo(name = "chapter_index")
    val chapterIndex: Int,
    /** 是否已访问过 */
    @ColumnInfo(name = "visited")
    val visited: Boolean = false,
    /** 首次访问时间戳（毫秒），0 = 未访问 */
    @ColumnInfo(name = "first_visited_at")
    val firstVisitedAt: Long = 0,
    /** 末次访问时间戳（毫秒），0 = 未访问 */
    @ColumnInfo(name = "last_visited_at")
    val lastVisitedAt: Long = 0,
)
