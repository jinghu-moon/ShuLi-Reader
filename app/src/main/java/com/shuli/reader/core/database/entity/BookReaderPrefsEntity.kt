package com.shuli.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每本书的阅读器偏好覆盖（本书作用域）。
 *
 * 仅存储与全局默认不同的字段（nullable = 有覆盖），
 * 解析时由 [com.shuli.reader.feature.reader.ReaderSettingsManager] 合并到全局默认。
 */
@Entity(tableName = "book_reader_prefs")
data class BookReaderPrefsEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val bookId: Long,

    /** 序列化的 BookReaderPrefsOverrides JSON（omitNulls） */
    @ColumnInfo(name = "config_json")
    val configJson: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
