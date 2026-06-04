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
 * 阅读器正文搜索：发起查询、管理结果列表、结果间跳转。
 *
 * 从 [com.shuli.reader.feature.reader.ReaderViewModel] 抽出，避免 ViewModel 继续膨胀。
 *
 * @param bookRepository 数据源（nullable 以支持测试/Preview）
 * @param uiState 与 ViewModel 共享的 UI 状态（MutableStateFlow 便于直接 copy() 更新）
 * @param scope 协程作用域；由 ViewModel 传入自己的 `viewModelScope`
 * @param jumpTo 跳转回调：由 ViewModel 提供，封装章节 + 字节偏移的跳转逻辑
 */
class TextSearchManager(
    private val bookRepository: BookRepository?,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val jumpTo: (chapterIndex: Int, byteOffset: Long) -> Unit,
) {

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
        jumpTo(result.chapterIndex, result.byteOffset)
    }
}
