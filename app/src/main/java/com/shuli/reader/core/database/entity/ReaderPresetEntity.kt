package com.shuli.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 阅读器预设实体
 */
@Entity(tableName = "reader_preset")
data class ReaderPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** 序列化的 ReaderPreferences JSON */
    val configJson: String,
)
