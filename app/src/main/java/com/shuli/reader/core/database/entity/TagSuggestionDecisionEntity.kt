package com.shuli.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tag_suggestion_decision",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("book_id"),
        Index(value = ["book_id", "tag_name"], unique = true),
    ],
)
data class TagSuggestionDecisionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "book_id")
    val bookId: Long,

    @ColumnInfo(name = "tag_name")
    val tagName: String,

    @ColumnInfo(name = "accepted")
    val accepted: Boolean,

    @ColumnInfo(name = "decided_at")
    val decidedAt: Long = System.currentTimeMillis(),
)
