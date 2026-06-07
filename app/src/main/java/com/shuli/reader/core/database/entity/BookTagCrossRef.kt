package com.shuli.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "book_tag_cross_ref",
    primaryKeys = ["book_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("book_id"),
        Index("tag_id"),
    ],
)
data class BookTagCrossRef(
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
)
