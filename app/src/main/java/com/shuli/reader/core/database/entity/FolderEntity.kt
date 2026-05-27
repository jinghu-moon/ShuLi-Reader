package com.shuli.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    // 固定槽位：null = 自动排序, 非 null = 固定在该位置
    val pinnedSlot: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
