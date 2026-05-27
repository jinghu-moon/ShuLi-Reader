package com.shuli.reader.core.database.entity

data class BookShelfRow(
    val id: Long,
    val title: String,
    val author: String?,
    val filePath: String,
    val fileType: String,
    val fileSize: Long,
    val coverPath: String?,
    val lastReadTime: Long?,
    val readingProgress: Float,
    val isFavorite: Boolean,
    val customCoverPaletteIndex: Int?,
    val folderId: Long?,
    val pinnedSlot: Int?,
)
