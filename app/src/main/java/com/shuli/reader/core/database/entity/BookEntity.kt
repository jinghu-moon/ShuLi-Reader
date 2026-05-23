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
)
