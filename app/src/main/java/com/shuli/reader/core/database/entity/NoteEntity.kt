package com.shuli.reader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
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
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val createdTime: Long,
    /** 字节区间起点（TXT 唯一权威位置） */
    val byteStart: Long = 0L,
    /** 字节区间终点（exclusive） */
    val byteEnd: Long = 0L,
    /** 笔记文本 */
    val noteText: String = "",
    /** 高亮颜色 */
    val color: String? = null,
)
