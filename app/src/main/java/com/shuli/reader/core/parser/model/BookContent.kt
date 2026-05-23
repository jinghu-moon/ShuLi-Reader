package com.shuli.reader.core.parser.model

data class BookContent(
    val title: String,
    val author: String?,
    val encoding: String,
    val totalLength: Long,
    val chapters: List<Chapter>,
    val content: String,
)

data class Chapter(
    val title: String,
    val startIndex: Int,
    val endIndex: Int,
)
