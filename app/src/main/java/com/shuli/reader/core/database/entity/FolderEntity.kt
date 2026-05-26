package com.shuli.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val orderIndex: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
