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
    /** UI 缓存：当前章节标题（目录就绪后异步刷新） */
    val durChapterTitle: String? = null,
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
    // === 字节模型字段（v4 重构，TXT 权威；EPUB 保持使用 durChapterIndex/durChapterPos）===
    /** 文件字符编码（一次探测永久保存，TXT 用） */
    val charset: String = "UTF-8",
    /** 当前阅读位置的字节偏移（TXT 唯一权威） */
    val durByteOffset: Long = 0L,
    /** 章节扫描完成后回填，UI 进度估算用 */
    val estimatedTotalChars: Long = 0L,
)
