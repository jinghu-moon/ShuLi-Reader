package com.shuli.reader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_chapters",
    indices = [
        Index(value = ["bookId", "chapterIndex"], unique = true),
    ],
)
data class BookChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val title: String,
    // EPUB: spine 索引，用于定位 XHTML 文件
    val spineIndex: Int = -1,
    // TXT: 字符偏移范围（用于旧逻辑兼容）
    val charStart: Int = 0,
    val charEnd: Int = 0,
    // TXT: 字节偏移范围（用于 RandomAccessFile 按需读取）
    val byteStart: Long = 0L,
    val byteEnd: Long = 0L,
    // TXT: 文件编码，解码用
    val charset: String = "UTF-8",
)
