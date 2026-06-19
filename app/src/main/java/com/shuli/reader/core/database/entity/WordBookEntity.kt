package com.shuli.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 生词本表
 *
 * 存储用户收藏的单词
 */
@Entity(tableName = "word_book")
data class WordBookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 单词 */
    val word: String,
    /** 单词释义（缓存，避免重复查词典） */
    val definition: String = "",
    /** 单词所在上下文句子 */
    val contextSentence: String = "",
    /** 来源书籍 ID */
    val bookId: Long = 0,
    /** 来源章节索引 */
    val chapterIndex: Int = 0,
    /** 来源字符偏移 */
    val charOffset: Int = 0,
    /** 添加时间 */
    val addedAt: Long = System.currentTimeMillis(),
    /** 最后复习时间 */
    val lastReviewAt: Long = 0L,
    /** 复习次数 */
    val reviewCount: Int = 0,
    /** 掌握程度（0-5） */
    val masteryLevel: Int = 0,
    /** 是否已导出到 Anki */
    val exportedToAnki: Boolean = false,
)
