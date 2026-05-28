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
    /** EPUB: spine 索引，用于定位 XHTML 文件 */
    val spineIndex: Int = -1,
    /** TXT: 字节偏移起点 */
    val byteStart: Long = 0L,
    /** TXT: 字节偏移终点（exclusive） */
    val byteEnd: Long = 0L,
    /** TXT: 文件编码，解码用 */
    val charset: String = "UTF-8",
    /** 章节字数（解析时顺手计算，UI 显示用） */
    val wordCount: Int = 0,
)
