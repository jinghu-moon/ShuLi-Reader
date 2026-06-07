package com.shuli.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color_index")
    val colorIndex: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
