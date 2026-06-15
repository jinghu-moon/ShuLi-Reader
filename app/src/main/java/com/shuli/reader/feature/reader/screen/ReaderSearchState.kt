package com.shuli.reader.feature.reader.screen
import com.shuli.reader.feature.reader.screen.ReaderSearchState

import com.shuli.reader.core.repository.SearchResult

/**
 * 阅读器搜索状态（按需收集）。
 *
 * 不参与 Canvas 首帧渲染，AndroidView.update 不观察此 StateFlow。
 */
data class ReaderSearchState(
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val currentSearchResultIndex: Int = -1,
)
