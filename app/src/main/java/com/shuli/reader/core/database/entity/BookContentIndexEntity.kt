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
    val chapterStart: Int,
    val content: String,
)
