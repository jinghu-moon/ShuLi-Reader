package com.shuli.reader.feature.reader.progress

import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter

/**
 * 把 [BookContent] 规范化为章节列表：
 * - 若 [BookContent.chapters] 已有内容，直接返回
 * - 否则若 [BookContent.content] 非空，返回一个代表全文的单章
 * - 否则返回空列表
 *
 * 原 [com.shuli.reader.feature.reader.ReaderViewModel.normalizedChapters] 的共享版本。
 */
fun BookContent.normalizedChapters(): List<Chapter> {
    if (chapters.isNotEmpty()) return chapters
    return if (content.isNotBlank()) {
        listOf(Chapter(title = "Full Text", byteStart = 0L, byteEnd = content.length.toLong()))
    } else {
        emptyList()
    }
}
