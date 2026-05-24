package com.shuli.reader.feature.bookshelf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.shuli.reader.core.repository.BookAlreadyExistsException
import com.shuli.reader.core.repository.BookRepository
import com.shuli.reader.core.repository.ImportConfig
import com.shuli.reader.core.repository.ImportResult
import com.shuli.reader.core.database.entity.BookShelfRow
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfViewModel(
    private val bookRepository: BookRepository,
    private val userPreferences: com.shuli.reader.core.data.UserPreferences? = null,
) : ViewModel() {

    companion object {
        const val INITIAL_PAGE_SIZE = 100
    }

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    private val _sortOrder = MutableStateFlow(SortOrder.LAST_READ)
    private val _filterType = MutableStateFlow(FilterType.ALL)
    private val _isAscending = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)

    private val _events = MutableSharedFlow<BookshelfEvent>()
    val events = _events.asSharedFlow()

    /** 解析 UserPreferences.unifiedCoverPalette："auto" → null（自动散列）；数字字符串 → Int 索引。 */
    private val unifiedCoverPaletteFlow: kotlinx.coroutines.flow.Flow<Int?> =
        userPreferences?.unifiedCoverPalette?.map { value ->
            if (value == com.shuli.reader.core.data.COVER_PALETTE_AUTO) null
            else value.toIntOrNull()?.takeIf { it in 0..19 }
        } ?: flowOf(null)

    private val booksFlow = combine(_isSearching, _searchQuery) { isSearching, query ->
        isSearching to query.trim()
    }
        .distinctUntilChanged()
        .flatMapLatest { (isSearching, query) ->
            when {
                isSearching && query.isBlank() -> flowOf(emptyList())
                isSearching -> bookRepository.searchBooksPage(query, INITIAL_PAGE_SIZE, 0)
                else -> bookRepository.getBookshelfPage(INITIAL_PAGE_SIZE, 0)
            }
        }

    val uiState: StateFlow<BookshelfUiState> = combine(
        booksFlow,
        bookRepository.getReadingDurations(),
        bookRepository.getTodayReadingTime(),
        _viewMode,
        _sortOrder,
        _filterType,
        _isAscending,
        _searchQuery,
        _isSearching,
        unifiedCoverPaletteFlow,
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
        val unifiedPalette = values[9] as Int?

        @Suppress("UNCHECKED_CAST")
        val bookRows = books as List<BookShelfRow>

        @Suppress("UNCHECKED_CAST")
        val durationMap = durations as Map<Long, Long>

        val bookItems = bookRows.map { row ->
            val duration = durationMap[row.id] ?: 0L
            row.toBookItem(duration)
        }

        val filtered = bookItems.applyFilter(filterType)
        val searched = if (isSearching && searchQuery.isBlank()) emptyList() else filtered
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
            unifiedCoverPaletteIndex = unifiedPalette,
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
            bookRepository.toggleFavorite(bookId)
            _events.emit(BookshelfEvent.ShowMessage { it.favoriteToggled })
        }
    }

    fun onDeleteBook(bookId: Long) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookId)
            _events.emit(BookshelfEvent.ShowMessage { it.bookDeleted })
        }
    }

    /** 为单本书指定自定义封面色盘索引（0..19）；传 null 恢复自动散列。 */
    fun setBookCoverPalette(bookId: Long, paletteIndex: Int?) {
        viewModelScope.launch {
            bookRepository.setCustomCoverPaletteIndex(bookId, paletteIndex)
        }
    }

    private suspend fun importSingleBook(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw UnableToReadFileException()

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
                _events.emit(BookshelfEvent.ShowMessage { strings -> strings.bookAlreadyInShelf })
                _events.emit(BookshelfEvent.HighlightBook(e.bookId))
            } catch (e: Exception) {
                _events.emit(BookshelfEvent.ShowMessage { strings -> strings.importFailed(e.toImportErrorMessage(strings)) })
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
                val result = ImportResult(
                    successCount = successCount,
                    skippedCount = skippedCount,
                    failedCount = failCount,
                    firstDuplicateBookId = firstDuplicateId,
                )
                showImportResult(result)
            } catch (e: Exception) {
                _events.emit(BookshelfEvent.ShowMessage { strings -> strings.importFailed(e.toImportErrorMessage(strings)) })
            }
        }
    }

    fun onImportFolder(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val folder = java.io.File(uri.path ?: throw InvalidFolderPathException())
                if (!folder.isDirectory) {
                    _events.emit(BookshelfEvent.ShowMessage { it.importFailed(it.invalidFolder) })
                    return@launch
                }

                val config = ImportConfig()
                val files = bookRepository.scanFolderForBooks(folder, config)

                if (files.isEmpty()) {
                    _events.emit(BookshelfEvent.ShowMessage { it.importFailed(it.noImportableFiles) })
                    return@launch
                }

                val result = bookRepository.importBooks(files, config)
                showImportResult(result)
            } catch (e: Exception) {
                _events.emit(BookshelfEvent.ShowMessage { strings -> strings.importFailed(e.toImportErrorMessage(strings)) })
            }
        }
    }

    private suspend fun showImportResult(result: ImportResult) {
        val message: (AppStrings) -> String = when {
            result.isAllSuccess -> { strings -> strings.importSuccessCount(result.successCount) }
            result.hasSkipped && !result.hasFailed -> { strings -> strings.importSuccessWithSkipped(result.successCount, result.skippedCount) }
            result.hasFailed && !result.hasSkipped -> { strings -> strings.importSuccessWithFailed(result.successCount, result.failedCount) }
            else -> { strings -> strings.importSuccessWithBoth(result.successCount, result.skippedCount, result.failedCount) }
        }
        _events.emit(BookshelfEvent.ShowMessage(message))
        result.firstDuplicateBookId?.let {
            _events.emit(BookshelfEvent.HighlightBook(it))
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

private class UnableToReadFileException : IllegalStateException()
private class InvalidFolderPathException : IllegalArgumentException()

private fun Throwable.toImportErrorMessage(strings: AppStrings): String {
    return when (this) {
        is UnableToReadFileException -> strings.unableToReadFile
        is InvalidFolderPathException -> strings.invalidFolderPath
        else -> message.orEmpty()
    }
}
