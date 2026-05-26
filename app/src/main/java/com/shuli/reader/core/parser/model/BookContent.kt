package com.shuli.reader.core.parser.model

data class BookContent(
    val title: String,
    val author: String?,
    val encoding: String,
    val totalLength: Long,
    val chapters: List<Chapter>,
    val content: String = "",
)

data class Chapter(
    val title: String,
    val startIndex: Int,
    val endIndex: Int,
    /** EPUB spine 中的原始索引（用于按需加载章节内容），非 EPUB 格式时为 -1 */
    val spineIndex: Int = -1,
)
