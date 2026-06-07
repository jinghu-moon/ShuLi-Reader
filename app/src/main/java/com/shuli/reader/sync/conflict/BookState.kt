package com.shuli.reader.sync.conflict

/**
 * 书籍同步状态（T-18）
 *
 * 用于冲突解决时比较本地和远端的阅读进度。
 */
data class BookState(
    val version: Int = 0,
    val updatedAt: Long = 0L,
    val byteOffset: Long = 0L,
    val chapterIndex: Int = 0,
    val chapterPos: Int = 0,
    val fileType: String = "",
    val totalSize: Long = 0L,
    val readingStatus: String = "WANT_TO_READ",
    val readCount: Int = 1,
    val tags: List<String> = emptyList(),
)
