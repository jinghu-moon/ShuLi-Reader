package com.shuli.reader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["filePath"], unique = true)]
)
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
    // === 同步字段（T-01）===
    /** 远端唯一标识，UUID v4 格式 */
    val bookKey: String = "",
    /** 文件三点采样 hash */
    val fastHash: String = "",
    /** 后台计算的完整文件 hash，可为空 */
    val fullHash: String? = null,
    /** 本地修改标记 */
    val isDirty: Boolean = false,
    /** 逻辑版本号，每次本地修改递增 */
    val version: Int = 1,
    /** 上次同步成功时的版本号 */
    val syncedVersion: Int = 0,
    /** 本地修改时间戳 */
    val updatedAt: Long = 0L,
    /** 旧格式兼容映射 */
    val remoteBookKey: String? = null,
)
