package com.shuli.reader.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.repository.GlobalSearchBookOption
import com.shuli.reader.core.repository.GlobalSearchEvent
import com.shuli.reader.core.repository.GlobalSearchRepository
import com.shuli.reader.core.repository.GlobalSearchResult
import com.shuli.reader.core.repository.GlobalSearchScope
import com.shuli.reader.core.repository.SearchIndexBackfillManager
import com.shuli.reader.core.repository.SearchIndexBackfillProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GlobalSearchViewModel(
    private val globalSearchRepository: GlobalSearchRepository,
    private val searchIndexBackfillManager: SearchIndexBackfillManager,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var bookOptionsJob: Job? = null
    private var lastCompletedBackfillKey: String? = null

    init {
        loadBookOptions()
        observeSearchHistory()
        observeBackfillProgress()
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    expandedBookIds = emptySet(),
                    status = SearchStatus.IDLE,
                    progress = null,
                    totalResults = 0,
                    errorMessage = null,
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            executeSearch(trimmed)
        }
    }

    fun onHistoryQuerySelected(query: String) {
        onQueryChanged(query)
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            userPreferences.clearGlobalSearchHistory()
        }
    }

    fun configureScopeContext(
        currentGroupBookIds: List<Long>,
        currentGroupLabel: String = "当前书架",
    ) {
        val normalizedBookIds = currentGroupBookIds.distinct()
        val shouldRestart = _uiState.value.scopeMode == SearchScopeMode.CURRENT_GROUP &&
            _uiState.value.currentGroupBookIds != normalizedBookIds

        _uiState.update { state ->
            state.copy(
                currentGroupBookIds = normalizedBookIds,
                currentGroupLabel = currentGroupLabel,
                scope = if (state.scopeMode == SearchScopeMode.CURRENT_GROUP) {
                    GlobalSearchScope.Books(normalizedBookIds)
                } else {
                    state.scope
                },
            )
        }
        loadBookOptions()
        if (shouldRestart) restartSearchIfReady()
    }

    fun onAllScopeSelected() {
        _uiState.update {
            it.copy(
                scopeMode = SearchScopeMode.ALL,
                scope = GlobalSearchScope.All,
            )
        }
        restartSearchIfReady()
    }

    fun onCurrentGroupScopeSelected() {
        val bookIds = _uiState.value.currentGroupBookIds
        if (bookIds.isEmpty()) return
        _uiState.update {
            it.copy(
                scopeMode = SearchScopeMode.CURRENT_GROUP,
                scope = GlobalSearchScope.Books(bookIds),
            )
        }
        restartSearchIfReady()
    }

    fun onSelectedBooksConfirmed(bookIds: Set<Long>) {
        val normalizedBookIds = bookIds.toList().distinct()
        if (normalizedBookIds.isEmpty()) return

        _uiState.update {
            it.copy(
                scopeMode = SearchScopeMode.SELECTED_BOOKS,
                selectedBookIds = normalizedBookIds.toSet(),
                scope = GlobalSearchScope.Books(normalizedBookIds),
            )
        }
        restartSearchIfReady()
    }

    fun onResultBookExpansionToggled(bookId: Long) {
        _uiState.update { state ->
            val expanded = if (bookId in state.expandedBookIds) {
                state.expandedBookIds - bookId
            } else {
                state.expandedBookIds + bookId
            }
            state.copy(expandedBookIds = expanded)
        }
    }

    fun startBackfillMissingIndexes() {
        searchIndexBackfillManager.start()
    }

    fun cancelBackfill() {
        searchIndexBackfillManager.cancel()
    }

    private fun observeSearchHistory() {
        viewModelScope.launch {
            userPreferences.globalSearchHistory.collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }
    }

    private fun observeBackfillProgress() {
        viewModelScope.launch {
            searchIndexBackfillManager.progress.collect { progress ->
                _uiState.update { it.copy(backfillProgress = progress) }

                if (progress?.isCompleted == true && !progress.isRunning) {
                    val completedKey = "${progress.processedBooks}:${progress.indexedBooks}:${progress.skippedBooks}:${progress.failedBooks}"
                    if (completedKey != lastCompletedBackfillKey) {
                        lastCompletedBackfillKey = completedKey
                        loadBookOptions()
                        restartSearchIfReady()
                    }
                }
            }
        }
    }

    private suspend fun executeSearch(query: String) {
        val state = _uiState.value
        if (state.scopeBookCount > 0 && state.indexedBookCountInScope == 0) {
            _uiState.update {
                it.copy(
                    status = SearchStatus.DONE,
                    results = emptyList(),
                    expandedBookIds = emptySet(),
                    progress = null,
                    totalResults = 0,
                    errorMessage = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                status = SearchStatus.SEARCHING,
                results = emptyList(),
                expandedBookIds = emptySet(),
                progress = null,
                totalResults = 0,
                errorMessage = null,
            )
        }

        globalSearchRepository.search(
            query = query,
            scope = _uiState.value.scope,
        ).collect { event ->
            when (event) {
                is GlobalSearchEvent.Result -> {
                    _uiState.update { current ->
                        current.copy(results = current.results + event.item)
                    }
                }
                is GlobalSearchEvent.Progress -> {
                    _uiState.update {
                        it.copy(
                            progress = SearchProgress(
                                processedChapters = event.processedChapters,
                                resultsFound = event.resultsFound,
                            )
                        )
                    }
                }
                is GlobalSearchEvent.Completed -> {
                    userPreferences.addGlobalSearchHistory(query)
                    _uiState.update {
                        it.copy(
                            status = SearchStatus.DONE,
                            progress = null,
                            totalResults = event.totalResults,
                        )
                    }
                }
                is GlobalSearchEvent.Error -> {
                    _uiState.update {
                        it.copy(
                            status = SearchStatus.ERROR,
                            progress = null,
                            errorMessage = event.message,
                        )
                    }
                }
            }
        }
    }

    private fun loadBookOptions() {
        bookOptionsJob?.cancel()
        bookOptionsJob = viewModelScope.launch {
            val books = globalSearchRepository.getSearchableBooks()
            val validBookIds = books.map { it.id }.toSet()
            val coverage = SearchIndexCoverage(
                totalBooks = books.size,
                indexedBooks = books.count { it.isIndexed },
                missingBooks = books.count { !it.isIndexed },
            )

            _uiState.update { state ->
                val selectedBookIds = state.selectedBookIds.intersect(validBookIds)
                val selectedModeWithoutBooks = state.scopeMode == SearchScopeMode.SELECTED_BOOKS &&
                    selectedBookIds.isEmpty()
                val nextScopeMode = if (selectedModeWithoutBooks) SearchScopeMode.ALL else state.scopeMode
                val nextScope = when (nextScopeMode) {
                    SearchScopeMode.ALL -> GlobalSearchScope.All
                    SearchScopeMode.CURRENT_GROUP -> GlobalSearchScope.Books(state.currentGroupBookIds)
                    SearchScopeMode.SELECTED_BOOKS -> GlobalSearchScope.Books(selectedBookIds.toList())
                }

                state.copy(
                    bookOptions = books,
                    selectedBookIds = selectedBookIds,
                    indexCoverage = coverage,
                    scopeMode = nextScopeMode,
                    scope = nextScope,
                )
            }
        }
    }

    private fun restartSearchIfReady() {
        val trimmed = _uiState.value.query.trim()
        if (trimmed.length < 2) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch { executeSearch(trimmed) }
    }
}

data class GlobalSearchUiState(
    val query: String = "",
    val scope: GlobalSearchScope = GlobalSearchScope.All,
    val scopeMode: SearchScopeMode = SearchScopeMode.ALL,
    val currentGroupBookIds: List<Long> = emptyList(),
    val currentGroupLabel: String = "当前书架",
    val bookOptions: List<GlobalSearchBookOption> = emptyList(),
    val selectedBookIds: Set<Long> = emptySet(),
    val indexCoverage: SearchIndexCoverage = SearchIndexCoverage(),
    val searchHistory: List<String> = emptyList(),
    val results: List<GlobalSearchResult> = emptyList(),
    val expandedBookIds: Set<Long> = emptySet(),
    val status: SearchStatus = SearchStatus.IDLE,
    val progress: SearchProgress? = null,
    val totalResults: Int = 0,
    val errorMessage: String? = null,
    val backfillProgress: SearchIndexBackfillProgress? = null,
) {
    val scopeBookIds: Set<Long>
        get() = when (scopeMode) {
            SearchScopeMode.ALL -> bookOptions.map { it.id }.toSet()
            SearchScopeMode.CURRENT_GROUP -> currentGroupBookIds.toSet()
            SearchScopeMode.SELECTED_BOOKS -> selectedBookIds
        }

    val scopeBookCount: Int
        get() = when (scopeMode) {
            SearchScopeMode.ALL -> bookOptions.size
            SearchScopeMode.CURRENT_GROUP -> currentGroupBookIds.size
            SearchScopeMode.SELECTED_BOOKS -> selectedBookIds.size
        }

    val indexedBookCountInScope: Int
        get() {
            val ids = scopeBookIds
            return bookOptions.count { it.isIndexed && (scopeMode == SearchScopeMode.ALL || it.id in ids) }
        }

    val missingBookCountInScope: Int
        get() = (scopeBookCount - indexedBookCountInScope).coerceAtLeast(0)
}

data class SearchIndexCoverage(
    val totalBooks: Int = 0,
    val indexedBooks: Int = 0,
    val missingBooks: Int = 0,
)

data class SearchProgress(
    val processedChapters: Int,
    val resultsFound: Int,
)

enum class SearchStatus {
    IDLE, SEARCHING, DONE, ERROR
}

enum class SearchScopeMode {
    ALL, CURRENT_GROUP, SELECTED_BOOKS
}
