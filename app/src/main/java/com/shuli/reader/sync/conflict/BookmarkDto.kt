package com.shuli.reader.sync.conflict

/**
 * 书签 DTO（T-19）
 *
 * 用于书签同步和冲突解决。
 */
data class BookmarkDto(
    val id: String,
    val byteOffset: Int = 0,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)
