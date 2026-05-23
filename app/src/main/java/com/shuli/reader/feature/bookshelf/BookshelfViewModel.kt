package com.shuli.reader.feature.bookshelf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.repository.BookAlreadyExistsException
import com.shuli.reader.core.repository.BookRepository
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.BookshelfUiState
import com.shuli.reader.feature.bookshelf.model.FilterType
import com.shuli.reader.feature.bookshelf.model.SortOrder
import com.shuli.reader.feature.bookshelf.model.ViewMode
import com.shuli.reader.feature.bookshelf.model.toBookItem
import com.shuli.reader.feature.bookshelf.model.toReadableDuration
import com.shuli.reader.core.i18n.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BookshelfViewModel(
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    private val _sortOrder = MutableStateFlow(SortOrder.LAST_READ)
    private val _filterType = MutableStateFlow(FilterType.ALL)
    private val _isAscending = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)

    private val _events = MutableSharedFlow<BookshelfEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<BookshelfUiState> = combine(
        bookRepository.getAllBooks(),
        bookRepository.getReadingDurations(),
        bookRepository.getTodayReadingTime(),
        _viewMode,
        _sortOrder,
        _filterType,
        _isAscending,
        _searchQuery,
        _isSearching,
    ) { values ->
        val books = values[0] as List<*>
        val durations = values[1] as Map<*, *>
        val todayTime = values[2] as Long?
        val viewMode = values[3] as ViewMode
        val sortOrder = values[4] as SortOrder
        val filterType = values[5] as FilterType
        val isAscending = values[6] as Boolean
        val searchQuery = values[7] as String
        val isSearching = values[8] as Boolean

        @Suppress("UNCHECKED_CAST")
        val bookEntities = books as List<com.shuli.reader.core.database.entity.BookEntity>

        @Suppress("UNCHECKED_CAST")
        val durationMap = durations as Map<Long, Long>

        val bookItems = bookEntities.map { entity ->
            val duration = durationMap[entity.id] ?: 0L
            entity.toBookItem(duration)
        }

        val filtered = bookItems.applyFilter(filterType)
        val searched = if (isSearching) {
            if (searchQuery.isNotBlank()) {
                filtered.filter { it.title.contains(searchQuery, ignoreCase = true) }
            } else {
                emptyList()
            }
        } else {
            filtered
        }
        val sorted = searched.applySorting(sortOrder, isAscending)

        BookshelfUiState(
            books = sorted,
            viewMode = viewMode,
            sortOrder = sortOrder,
            isAscending = isAscending,
            filterType = filterType,
            searchQuery = searchQuery,
            isSearching = isSearching,
            todayReadingTime = (todayTime ?: 0L).toReadableDuration().ifBlank { "0m" },
            todayReadingMinutes = todayTime ?: 0L,
            isLoading = false,
            isEmpty = sorted.isEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BookshelfUiState(),
    )

    fun onViewModeChanged(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun onSortOrderChanged(order: SortOrder) {
        _sortOrder.value = order
    }

    fun onSortDirectionChanged(isAscending: Boolean) {
        _isAscending.value = isAscending
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSearchActiveChanged(active: Boolean) {
        _isSearching.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }

    fun onFilterChanged(filter: FilterType) {
        _filterType.value = filter
    }

    fun onBookClick(bookId: Long) {
        viewModelScope.launch {
            bookRepository.updateLastReadTime(bookId)
            _events.emit(BookshelfEvent.NavigateToReader(bookId))
        }
    }

    fun onToggleFavorite(bookId: Long) {
        viewModelScope.launch {
            // TODO: 实现收藏功能
        }
    }

    fun onDeleteBook(bookId: Long) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookId)
            _events.emit(BookshelfEvent.ShowMessage { it.bookDeleted })
        }
    }

    private suspend fun importSingleBook(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取文件")

        val fileName = getFileName(context, uri)
        val tempFile = File(context.cacheDir, fileName)

        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        bookRepository.importBook(tempFile)
        tempFile.delete()
    }

    fun onImportBook(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                importSingleBook(context, uri)
                _events.emit(BookshelfEvent.ShowMessage { it.importSuccess })
            } catch (e: BookAlreadyExistsException) {
                _events.emit(BookshelfEvent.ShowMessage { strings -> e.message ?: strings.bookAlreadyInShelf })
                _events.emit(BookshelfEvent.HighlightBook(e.bookId))
            } catch (e: Exception) {
                _events.emit(BookshelfEvent.ShowMessage { strings -> strings.importFailed(e.message ?: "") })
            }
        }
    }

    fun onImportBooks(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            try {
                var successCount = 0
                var skippedCount = 0
                var failCount = 0
                var firstDuplicateId: Long? = null
                uris.forEach { uri ->
                    try {
                        importSingleBook(context, uri)
                        successCount++
                    } catch (e: BookAlreadyExistsException) {
                        skippedCount++
                        if (firstDuplicateId == null) {
                            firstDuplicateId = e.bookId
                        }
                    } catch (e: Exception) {
                        failCount++
                    }
                }
                val message: (AppStrings) -> String = when {
                    failCount == 0 && skippedCount == 0 -> { strings -> strings.importSuccessCount(successCount) }
                    failCount == 0 && skippedCount > 0 -> { strings -> strings.importSuccessWithSkipped(successCount, skippedCount) }
                    failCount > 0 && skippedCount == 0 -> { strings -> strings.importSuccessWithFailed(successCount, failCount) }
                    else -> { strings -> strings.importSuccessWithBoth(successCount, skippedCount, failCount) }
                }
                _events.emit(BookshelfEvent.ShowMessage(message))
                if (firstDuplicateId != null) {
                    _events.emit(BookshelfEvent.HighlightBook(firstDuplicateId))
                }
            } catch (e: Exception) {
                _events.emit(BookshelfEvent.ShowMessage { strings -> strings.importFailed(e.message ?: "") })
            }
        }
    }

    fun showToastMessage(message: String) {
        viewModelScope.launch {
            _events.emit(BookshelfEvent.ShowMessage { message })
        }
    }

    fun showToastMessage(messageLambda: (AppStrings) -> String) {
        viewModelScope.launch {
            _events.emit(BookshelfEvent.ShowMessage(messageLambda))
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "book.txt"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
        return name
    }

    private fun List<BookItem>.applyFilter(filter: FilterType): List<BookItem> {
        return when (filter) {
            FilterType.ALL -> this
            FilterType.RECENT -> filter { it.isRecent }
            FilterType.FINISHED -> filter { it.readingProgress >= 0.99f }
            FilterType.FAVORITE -> filter { it.isFavorite }
        }
    }

    private fun List<BookItem>.applySorting(order: SortOrder, isAscending: Boolean): List<BookItem> {
        return when (order) {
            SortOrder.LAST_READ -> if (isAscending) sortedBy { it.lastReadTime ?: 0L } else sortedByDescending { it.lastReadTime ?: 0L }
            SortOrder.ADD_TIME -> if (isAscending) sortedBy { it.id } else sortedByDescending { it.id }
            SortOrder.TITLE -> if (isAscending) sortedBy { it.title } else sortedByDescending { it.title }
            SortOrder.FILE_SIZE -> if (isAscending) sortedBy { it.readingDurationMinutes } else sortedByDescending { it.readingDurationMinutes }
            SortOrder.PROGRESS -> if (isAscending) sortedBy { it.readingProgress } else sortedByDescending { it.readingProgress }
        }
    }
}

sealed class BookshelfEvent {
    data class NavigateToReader(val bookId: Long) : BookshelfEvent()
    data class ShowMessage(val message: (AppStrings) -> String) : BookshelfEvent()
    data class HighlightBook(val bookId: Long) : BookshelfEvent()
}
