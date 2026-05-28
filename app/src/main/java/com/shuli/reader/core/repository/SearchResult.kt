package com.shuli.reader.core.repository

/**
 * 正文搜索结果（字节偏移模型）
 */
data class SearchResult(
    val chapterIndex: Int,
    val chapterTitle: String,
    /** 匹配位置的文件字节偏移 */
    val byteOffset: Long,
    val context: String,
    val matchedText: String,
)
