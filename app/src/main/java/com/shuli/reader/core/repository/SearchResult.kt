package com.shuli.reader.core.repository

/**
 * 正文搜索结果
 */
data class SearchResult(
    val chapterIndex: Int,
    val chapterTitle: String,
    val charOffset: Int,
    val matchStart: Int,
    val matchEnd: Int,
    val context: String,
    val matchedText: String,
)
