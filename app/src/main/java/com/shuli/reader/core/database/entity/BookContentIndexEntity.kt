package com.shuli.reader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_content_index",
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["bookId", "chapterIndex"]),
    ],
)
data class BookContentIndexEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val chapterTitle: String,
    /** 章节正文（用于搜索匹配） */
    val content: String,
    /** 章节起点字节偏移（搜索命中后计算 byteOffset 用） */
    val byteStart: Long = 0L,
    /** 文件字符编码（解码用） */
    val charset: String = "UTF-8",
    /** utf16IndexToByte 映射表的 4-byte big-endian 编码（O(1) 搜索用） */
    val utf16ToByteBlob: ByteArray = ByteArray(0),
)
