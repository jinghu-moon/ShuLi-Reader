package com.shuli.reader.feature.reader

import com.shuli.reader.core.repository.SearchIndexRepository
import com.shuli.reader.core.repository.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 阅读器正文搜索管理：搜索、结果导航、清除。
 *
 * 从 ReaderViewModel 拆出，SRP —— 只负责"搜索交互"这一变更轴。
 */
internal class ReaderSearchManager(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val searchIndexRepository: SearchIndexRepository?,
    // ── 回调 ──
    private val jumpToChapterPosition: (Int, Long) -> Unit,
) {

    fun searchInCurrentBook(query: String) {
        val searchRepo = searchIndexRepository
        val bookId = uiState.value.bookId
        if (searchRepo == null || bookId == 0L || query.isBlank()) {
            clearSearchResults(query)
            return
        }

        scope.launch {
            val results = withContext(Dispatchers.IO) {
                searchRepo.searchInBook(bookId, query)
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
        jumpToChapterPosition(result.chapterIndex, result.byteOffset)
    }
}
