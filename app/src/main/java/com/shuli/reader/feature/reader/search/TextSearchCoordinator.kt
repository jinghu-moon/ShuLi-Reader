package com.shuli.reader.feature.reader.search

import com.shuli.reader.core.repository.BookRepository
import com.shuli.reader.core.repository.SearchResult
import com.shuli.reader.feature.reader.ReaderUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文本搜索协调器。
 *
 * 职责：正文搜索、搜索结果导航、搜索结果清除。
 *
 * 通过 [uiState] 读写共享状态，不反向依赖 ViewModel。
 */
class TextSearchCoordinator(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val bookRepository: BookRepository?,
    private val scope: CoroutineScope,
) {
    // ── 回调（由 ViewModel 注入）────────────────────────────────────

    /** 跳转到搜索结果位置（章节索引 + 字节偏移） */
    var onJumpToChapterPosition: ((Int, Long) -> Unit)? = null

    // ── 搜索操作 ──────────────────────────────────────────────────

    fun searchInCurrentBook(query: String) {
        val repository = bookRepository
        val bookId = uiState.value.bookId
        if (repository == null || bookId == 0L || query.isBlank()) {
            clearSearchResults(query)
            return
        }

        scope.launch {
            val results = withContext(Dispatchers.IO) {
                repository.searchInBook(bookId, query)
            }
            setSearchResults(query, results)
        }
    }

    fun setSearchResults(query: String, results: List<SearchResult>) {
        val nextIndex = if (results.isEmpty()) -1 else 0
        uiState.value = uiState.value.copy(
            searchQuery = query,
            searchResults = results,
            currentSearchResultIndex = nextIndex,
        )
        results.firstOrNull()?.let(::navigateToSearchResult)
    }

    fun goToNextSearchResult() {
        val state = uiState.value
        if (state.searchResults.isEmpty()) return

        val nextIndex = if (state.currentSearchResultIndex < state.searchResults.lastIndex) {
            state.currentSearchResultIndex + 1
        } else {
            0
        }
        uiState.value = state.copy(currentSearchResultIndex = nextIndex)
        navigateToSearchResult(state.searchResults[nextIndex])
    }

    fun goToPreviousSearchResult() {
        val state = uiState.value
        if (state.searchResults.isEmpty()) return

        val previousIndex = if (state.currentSearchResultIndex > 0) {
            state.currentSearchResultIndex - 1
        } else {
            state.searchResults.lastIndex
        }
        uiState.value = state.copy(currentSearchResultIndex = previousIndex)
        navigateToSearchResult(state.searchResults[previousIndex])
    }

    fun clearSearchResults(query: String = "") {
        uiState.value = uiState.value.copy(
            searchQuery = query,
            searchResults = emptyList(),
            currentSearchResultIndex = -1,
        )
    }

    private fun navigateToSearchResult(result: SearchResult) {
        onJumpToChapterPosition?.invoke(result.chapterIndex, result.byteOffset)
    }
}
