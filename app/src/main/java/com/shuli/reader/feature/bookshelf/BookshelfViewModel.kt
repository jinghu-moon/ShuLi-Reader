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
import com.shuli.reader.core.database.entity.FolderEntity
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.BookshelfNode
import com.shuli.reader.feature.bookshelf.model.FolderItem
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

    init {
        viewModelScope.launch {
            userPreferences?.viewMode?.collect { modeStr ->
                val mode = try {
                    ViewMode.valueOf(modeStr)
                } catch (e: Exception) {
                    ViewMode.GRID
                }
                _viewMode.value = mode
            }
        }
    }

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    private val _sortOrder = MutableStateFlow(SortOrder.LAST_READ)
    private val _filterType = MutableStateFlow(FilterType.ALL)
    private val _isAscending = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)
    private val _isEditMode = MutableStateFlow(false)
    private val _selectedNodeIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _openFolderId = MutableStateFlow<Long?>(null)
    val openFolderId: StateFlow<Long?> = _openFolderId.asStateFlow()

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

    private val foldersFlow = bookRepository.getAllFolders()

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
        foldersFlow,
        _isEditMode,
        _selectedNodeIds,
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
        val folderEntities = values[10] as List<FolderEntity>
        val isEditMode = values[11] as Boolean
        
        @Suppress("UNCHECKED_CAST")
        val selectedNodeIds = values[12] as Set<Long>

        @Suppress("UNCHECKED_CAST")
        val bookRows = books as List<BookShelfRow>

        @Suppress("UNCHECKED_CAST")
        val durationMap = durations as Map<Long, Long>

        val bookItems = bookRows.map { row ->
            val duration = durationMap[row.id] ?: 0L
            row.toBookItem(duration)
        }

        // 按 folderId 分组
        val booksByFolder = bookItems.groupBy { it.folderId }
        val rootNodes = mutableListOf<BookshelfNode>()

        // 1. 组装 FolderItem
        folderEntities.forEach { folder ->
            val folderUiId = -folder.id
            val folderBooks = booksByFolder[folder.id] ?: emptyList()
            rootNodes.add(
                FolderItem(
                    id = folderUiId,
                    title = folder.name,
                    pinnedSlot = folder.pinnedSlot,
                    books = folderBooks.sortedBy { it.pinnedSlot ?: Int.MAX_VALUE }
                )
            )
        }

        // 2. 组装根目录下的 BookItem
        val rootBooks = booksByFolder[null] ?: emptyList()
        rootNodes.addAll(rootBooks)

        val filtered = rootNodes.applyFilter(filterType)
        val searched = if (isSearching && searchQuery.isBlank()) emptyList() else filtered
        // 分离固定项和自动项，自动项按当前排序方式排序，然后槽位合并
        val pinned = searched.filter { it.pinnedSlot != null }
        val autoSorted = searched.filter { it.pinnedSlot == null }.applySorting(sortOrder, isAscending)
        val sorted = mergePinnedSlots(pinned, autoSorted)

        BookshelfUiState(
            nodes = sorted,
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
            isEditMode = isEditMode,
            selectedNodeIds = selectedNodeIds,
            unifiedCoverPaletteIndex = unifiedPalette,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BookshelfUiState(),
    )

    fun onViewModeChanged(mode: ViewMode) {
        _viewMode.value = mode
        viewModelScope.launch {
            userPreferences?.setViewMode(mode.name)
        }
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

    // --- Edit Mode & Grouping Operations ---

    fun onFolderClick(folderId: Long) {
        _openFolderId.value = folderId
    }

    fun onFolderDismiss() {
        _openFolderId.value = null
    }

    fun onToggleEditMode(selectNodeId: Long? = null) {
        _isEditMode.value = !_isEditMode.value
        if (_isEditMode.value && selectNodeId != null) {
            _selectedNodeIds.value = setOf(selectNodeId)
        } else if (!_isEditMode.value) {
            _selectedNodeIds.value = emptySet()
        }
    }

    fun onToggleNodeSelection(nodeId: Long) {
        val current = _selectedNodeIds.value.toMutableSet()
        if (current.contains(nodeId)) {
            current.remove(nodeId)
        } else {
            current.add(nodeId)
        }
        _selectedNodeIds.value = current
    }

    fun onSelectAllNodes(nodes: List<BookshelfNode>) {
        if (_selectedNodeIds.value.size == nodes.size) {
            _selectedNodeIds.value = emptySet()
        } else {
            _selectedNodeIds.value = nodes.map { it.id }.toSet()
        }
    }

    fun onCreateFolderAndMove(folderName: String, sourceNodeId: Long, targetNodeId: Long) {
        viewModelScope.launch {
            val folderId = bookRepository.createFolder(folderName)
            bookRepository.moveBooksToFolder(listOf(sourceNodeId, targetNodeId), folderId)
            _events.emit(BookshelfEvent.ShowMessage { it.folderCreated })
        }
    }

    fun onMoveSelectedToFolder(folderId: Long?) {
        val selectedIds = _selectedNodeIds.value.toList()
        if (selectedIds.isEmpty()) return
        val bookIds = selectedIds.filter { it > 0 }
        val realFolderId = folderId?.let { if (it < 0) -it else it }

        viewModelScope.launch {
            if (bookIds.isNotEmpty()) bookRepository.moveBooksToFolder(bookIds, realFolderId)
            onToggleEditMode()
            _events.emit(BookshelfEvent.ShowMessage { if (folderId == null) it.removedFromFolder else it.addedToFolder })
        }
    }

    fun deleteNodes(nodeIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val nodesToDelete = uiState.value.nodes.filter { it.id in nodeIds }
            nodesToDelete.forEach { node ->
                if (node is BookItem) bookRepository.deleteBook(node.id)
                else if (node is FolderItem) bookRepository.deleteFolder(-node.id)
            }
        }
        _selectedNodeIds.value = emptySet()
        _isEditMode.value = false
    }

    fun onMoveSelectedToNewFolder(folderName: String) {
        val selectedIds = _selectedNodeIds.value.toList()
        if (selectedIds.isEmpty()) return
        val bookIds = selectedIds.filter { it > 0 }

        viewModelScope.launch {
            val folderId = bookRepository.createFolder(folderName)
            if (bookIds.isNotEmpty()) bookRepository.moveBooksToFolder(bookIds, folderId)
            onToggleEditMode()
            _events.emit(BookshelfEvent.ShowMessage { "已创建分组并移动" })
        }
    }

    fun mergeNodes(sourceId: Long, targetId: Long, sourceIsFolder: Boolean, targetIsFolder: Boolean) {
        val srcIsFolder = sourceIsFolder || sourceId < 0
        val tgtIsFolder = targetIsFolder || targetId < 0
        if (srcIsFolder || sourceId == targetId) return

        viewModelScope.launch(Dispatchers.IO) {
            if (tgtIsFolder) {
                bookRepository.moveBooksToFolder(listOf(sourceId), -targetId)
            } else {
                val newFolderId = bookRepository.createFolder("新建文件夹")
                bookRepository.moveBooksToFolder(listOf(sourceId, targetId), newFolderId)
            }
        }
    }

    /** 将节点固定到指定槽位（O(1) 写入） */
    fun pinNode(nodeId: Long, slot: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (nodeId > 0) {
                bookRepository.updateBookPinnedSlot(nodeId, slot)
            } else {
                bookRepository.updateFolderPinnedSlot(-nodeId, slot)
            }
        }
    }

    /** 取消节点固定（恢复自动排序） */
    fun unpinNode(nodeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (nodeId > 0) {
                bookRepository.updateBookPinnedSlot(nodeId, null)
            } else {
                bookRepository.updateFolderPinnedSlot(-nodeId, null)
            }
        }
    }

    /** 清除所有固定项（重置为自动排序） */
    fun clearAllPinnedSlots() {
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.clearAllPinnedSlots()
        }
    }

    /**
     * 拖拽排序后批量更新 pinnedSlot。
     * 对比新旧列表，找出位置变化的项，只更新变化的项（O(k) 写入，k = 变化数）。
     */
    fun commitDragResult(newNodes: List<BookshelfNode>) {
        viewModelScope.launch(Dispatchers.IO) {
            newNodes.forEachIndexed { index, node ->
                val oldSlot = node.pinnedSlot
                val newSlot = index
                if (oldSlot != newSlot) {
                    if (node is BookItem) {
                        bookRepository.updateBookPinnedSlot(node.id, newSlot)
                    } else if (node is FolderItem) {
                        bookRepository.updateFolderPinnedSlot(-node.id, newSlot)
                    }
                }
            }
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

    private fun List<BookshelfNode>.applyFilter(filter: FilterType): List<BookshelfNode> {
        return when (filter) {
            FilterType.ALL -> this
            FilterType.FINISHED -> filter {
                if (it is BookItem) it.readingProgress >= 0.99f else false
            }
            FilterType.FAVORITE -> filter {
                if (it is BookItem) it.isFavorite else false
            }
        }
    }

    private fun List<BookshelfNode>.applySorting(order: SortOrder, isAscending: Boolean): List<BookshelfNode> {
        return when (order) {
            SortOrder.LAST_READ -> {
                val selector: (BookshelfNode) -> Long = { if (it is BookItem) it.lastReadTime ?: 0L else 0L }
                if (isAscending) sortedBy(selector) else sortedByDescending(selector)
            }
            SortOrder.ADD_TIME -> if (isAscending) sortedBy { it.id } else sortedByDescending { it.id }
            SortOrder.TITLE -> if (isAscending) sortedBy { it.title } else sortedByDescending { it.title }
            SortOrder.FILE_SIZE -> {
                val selector: (BookshelfNode) -> Long = { if (it is BookItem) it.readingDurationMinutes else 0L }
                if (isAscending) sortedBy(selector) else sortedByDescending(selector)
            }
            SortOrder.PROGRESS -> {
                val selector: (BookshelfNode) -> Float = { if (it is BookItem) it.readingProgress else 0f }
                if (isAscending) sortedBy(selector) else sortedByDescending(selector)
            }
        }
    }

    /**
     * 槽位合并：固定项占据指定位置，自动项填充空隙。
     * 冲突处理：多个固定项指向同一槽位时，后者顺延到下一个空槽。
     */
    private fun mergePinnedSlots(
        pinned: List<BookshelfNode>,
        auto: List<BookshelfNode>,
    ): List<BookshelfNode> {
        if (pinned.isEmpty()) return auto
        if (auto.isEmpty()) return pinned.sortedBy { it.pinnedSlot }

        val total = pinned.size + auto.size
        val result = ArrayList<BookshelfNode>(total)

        // 解析固定槽位冲突：同槽位后者顺延
        val resolvedPinned = mutableMapOf<Int, BookshelfNode>()
        pinned.sortedBy { it.pinnedSlot }.forEach { node ->
            var slot = node.pinnedSlot!!
            while (resolvedPinned.containsKey(slot)) { slot++ }
            resolvedPinned[slot] = node
        }

        val autoIterator = auto.iterator()
        for (i in 0 until total) {
            val pinnedNode = resolvedPinned[i]
            if (pinnedNode != null) {
                result.add(pinnedNode)
            } else if (autoIterator.hasNext()) {
                result.add(autoIterator.next())
            }
        }
        return result
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
