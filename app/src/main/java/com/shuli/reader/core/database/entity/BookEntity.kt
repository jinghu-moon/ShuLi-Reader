package com.shuli.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String?,
    val filePath: String,
    val fileType: String,  // TXT, EPUB
    val fileSize: Long,
    val coverPath: String?,
    val lastReadTime: Long?,
    val addedTime: Long,
    val readingProgress: Float = 0f,
    // 字符偏移进度字段（权威位置）
    val durChapterIndex: Int = 0,
    val durChapterPos: Int = 0,
    val durChapterTitle: String? = null,
    val durChapterTime: Long = 0L,
    val totalChapterNum: Int = 0,
    // 收藏状态
    val isFavorite: Boolean = false,
    // 分组支持
    val folderId: Long? = null,
    // 固定槽位：null = 自动排序, 非 null = 固定在该位置
    val pinnedSlot: Int? = null,
    // 用户自定义封面色盘索引（0..19，对应 MorandiPalettes；null 走自动散列）
    val customCoverPaletteIndex: Int? = null,
    // 章节索引指纹：用于判断是否需要重建章节目录
    val chapterIndexFileSize: Long = 0L,
    val chapterIndexLastModified: Long = 0L,
    val chapterIndexBuiltAt: Long = 0L,
)
