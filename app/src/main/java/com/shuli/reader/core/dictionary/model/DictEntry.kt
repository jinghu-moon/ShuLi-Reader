package com.shuli.reader.core.dictionary.model

/**
 * 词典查询结果条目
 */
data class DictEntry(
    /** 词条 */
    val word: String,
    /** 释义内容（HTML 或纯文本） */
    val definition: String,
    /** 来源词典标识 */
    val dictKey: String,
    /** 来源词典名称 */
    val dictName: String,
    /** 是否为 HTML 格式 */
    val isHtml: Boolean = false,
    /** 音标（如果有） */
    val phonetic: String? = null,
    /** 词性（如果有） */
    val partOfSpeech: String? = null,
)
