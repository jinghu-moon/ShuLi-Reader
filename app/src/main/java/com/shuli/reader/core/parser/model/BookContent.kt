package com.shuli.reader.core.parser.model

data class BookContent(
    val title: String,
    val author: String?,
    val encoding: String,
    val totalLength: Long,
    val chapters: List<Chapter>,
    val content: String = "",
    /** 关联的书籍 ID，用于从 DB 查询章节字节偏移 */
    val bookId: Long = 0L,
)

data class Chapter(
    val title: String,
    val startIndex: Int,
    val endIndex: Int,
    /** EPUB spine 中的原始索引（用于按需加载章节内容），非 EPUB 格式时为 -1 */
    val spineIndex: Int = -1,
)
