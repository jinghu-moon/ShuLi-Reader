package com.shuli.reader.feature.reader

import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.core.reader.animation.PageDelegateFactory

/**
 * 阅读器页面状态（中频变化）。
 *
 * AndroidView.update 只观察此 StateFlow + preferences + overlayState，
 * 不因 toolbar/搜索/预设列表变化触发页面设置。
 */
data class ReaderPageState(
    val bookId: Long = 0L,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val currentPage: TextPage? = null,
    val currentChapter: TextChapter? = null,
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val totalPages: Int = 0,
    val totalChapters: Int = 0,
    val pageAnimType: PageDelegateFactory.PageAnimType = PageDelegateFactory.PageAnimType.HORIZONTAL,
    val pageRenderMode: PageRenderMode = PageRenderMode.SEQUENTIAL,
    val chapterTitles: List<String> = emptyList(),
    val chapterWordCounts: List<Int> = emptyList(),
    val chapterPageCounts: Map<Int, Int> = emptyMap(),
    val layoutVersion: Int = 0,
    val isReflowing: Boolean = false,
)
