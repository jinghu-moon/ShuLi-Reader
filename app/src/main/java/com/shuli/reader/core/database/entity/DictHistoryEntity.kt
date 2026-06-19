package com.shuli.reader.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 查词历史表
 *
 * 记录用户查询过的单词
 */
@Entity(tableName = "dict_history")
data class DictHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 查询的单词 */
    val word: String,
    /** 单词所在上下文句子 */
    @ColumnInfo(name = "context_sentence")
    val contextSentence: String = "",
    /** 查询来源（bookId） */
    @ColumnInfo(name = "book_id")
    val bookId: Long = 0,
    /** 章节索引 */
    @ColumnInfo(name = "chapter_index")
    val chapterIndex: Int = 0,
    /** 字符偏移 */
    @ColumnInfo(name = "char_offset")
    val charOffset: Int = 0,
    /** 查询时间 */
    @ColumnInfo(name = "queried_at")
    val queriedAt: Long = System.currentTimeMillis(),
)
