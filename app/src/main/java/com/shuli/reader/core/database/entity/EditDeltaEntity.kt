package com.shuli.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 编辑补丁持久化实体
 *
 * 用于崩溃恢复，保存未保存的编辑
 */
@Entity(tableName = "edit_delta")
data class EditDeltaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "book_id")
    val bookId: Long,
    @ColumnInfo(name = "chapter_index")
    val chapterIndex: Int,
    @ColumnInfo(name = "char_start")
    val charStart: Int,
    @ColumnInfo(name = "char_end")
    val charEnd: Int,
    @ColumnInfo(name = "new_text")
    val newText: String,
    @ColumnInfo(name = "original_text")
    val originalText: String = "",
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
)
