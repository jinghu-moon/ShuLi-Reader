package com.shuli.reader.feature.bookshelf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.shuli.reader.core.repository.BookImportRepository
import com.shuli.reader.core.repository.BookQueryRepository
import com.shuli.reader.core.repository.FolderRepository
import com.shuli.reader.core.repository.ReadingProgressRepository
import com.shuli.reader.core.repository.TagRepository
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
import com.shuli.reader.core.i18n.AppStrings
import com.shuli.reader.core.reading.ReadingStatus
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

@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfViewModel(
    private val bookQueryRepository: BookQueryRepository,
    private val folderRepository: FolderRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookImportRepository: BookImportRepository,
    private val tagRepository: TagRepository? = null,
    private val userPreferences: com.shuli.reader.core.data.UserPreferences? = null,
    private val readingSessionDao: com.shuli.reader.core.database.dao.ReadingSessionDao? = null,
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
    private val _activeTagFilter = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<BookshelfEvent>()
    val events = _events.asSharedFlow()

    // 导入管理器（从 BookshelfViewModel 拆出，SRP）
    private val bookImportManager: BookImportManager by lazy {
        BookImportManager(
            bookImportRepository = bookImportRepository,
            bookQueryRepository = bookQueryRepository,
            scope = viewModelScope,
            events = _events,
        )
    }

    // 编辑模式管理器（从 BookshelfViewModel 拆出，SRP）
    private val bookshelfEditManager: BookshelfEditManager by lazy {
        BookshelfEditManager(
            bookQueryRepository = bookQueryRepository,
            folderRepository = folderRepository,
            readingProgressRepository = readingProgressRepository,
            scope = viewModelScope,
            events = _events,
            selectedNodeIds = _selectedNodeIds,
            isEditMode = _isEditMode,
            openFolderId = _openFolderId,
            nodesProvider = { uiState.value.nodes },
            toggleEditMode = { onToggleEditMode() },
        )
    }

    /** 解析 UserPreferences.unifiedCoverPalette："auto" → null（自动散列）；数字字符串 → Int 索引。 */
    private val unifiedCoverPaletteFlow: kotlinx.coroutines.flow.Flow<Int?> =
        userPreferences?.unifiedCoverPalette?.map { value ->
            if (value == com.shuli.reader.core.data.COVER_PALETTE_AUTO) null
            else value.toIntOrNull()?.takeIf { it in 0..19 }
        } ?: flowOf(null)

    private val booksFlow = combine(
        combine(_isSearching, _searchQuery) { isSearching, query -> isSearching to query.trim() },
        _activeTagFilter,
    ) { searchPair, tagFilter ->
        Triple(searchPair.first, searchPair.second, tagFilter)
    }
        .distinctUntilChanged()
        .flatMapLatest { (isSearching, query, tagFilter) ->
            val parsedTag = parseTagQuery(query)
            when {
                tagFilter != null -> bookQueryRepository.getBooksByTagPage(tagFilter, INITIAL_PAGE_SIZE, 0)
                parsedTag != null -> bookQueryRepository.getBooksByTagPage(parsedTag, INITIAL_PAGE_SIZE, 0)
                isSearching && query.isBlank() -> flowOf(emptyList())
                isSearching -> bookQueryRepository.searchBooksPage(query, INITIAL_PAGE_SIZE, 0)
                else -> bookQueryRepository.getBookshelfPage(INITIAL_PAGE_SIZE, 0)
            }
        }

    private fun parseTagQuery(query: String): String? {
        if (query.startsWith("tag:", ignoreCase = true)) {
            val tag = query.substring(4).trim()
            return tag.ifEmpty { null }
        }
        if (query.startsWith("#") && query.length > 1) {
            val tag = query.substring(1).trim()
            return tag.ifEmpty { null }
        }
        return null
    }

    private val foldersFlow = folderRepository.getAllFolders()

    // ── 分层 combine 中间数据类（消除 14-way varargs 的类型擦除）──

    private data class SessionData(
        val durations: Map<Long, Long>,
        val todayTime: Long,
    )

    private data class SortConfig(
        val sortOrder: SortOrder,
        val filterType: FilterType,
        val isAscending: Boolean,
    )

    private data class DisplayConfig(
        val viewMode: ViewMode,
        val isEditMode: Boolean,
        val selectedNodeIds: Set<Long>,
        val unifiedPalette: Int?,
        val searchQuery: String,
        val isSearching: Boolean,
        val activeTagFilter: String?,
    )

    // ── Layer 1: 聚合相关 Flow 为类型安全的中间结构 ──

    private val sessionFlow: kotlinx.coroutines.flow.Flow<SessionData> = combine(
        readingSessionDao?.getBookTotals()?.map { tuples ->
            tuples.associate { it.bookId to it.totalDuration }
        } ?: flowOf(emptyMap<Long, Long>()),
        readingSessionDao?.getTodayTotal(todayDateKey()) ?: flowOf(0L),
    ) { durations, todayTime ->
        SessionData(durations, todayTime ?: 0L)
    }

    private val sortConfigFlow: kotlinx.coroutines.flow.Flow<SortConfig> = combine(
        _sortOrder, _filterType, _isAscending,
    ) { sortOrder, filterType, isAscending ->
        SortConfig(sortOrder, filterType, isAscending)
    }

    private val displayConfigFlow: kotlinx.coroutines.flow.Flow<DisplayConfig> = combine(
        _viewMode, _isEditMode, _selectedNodeIds, unifiedCoverPaletteFlow,
        combine(_searchQuery, _isSearching, _activeTagFilter) { q, s, t -> Triple(q, s, t) },
    ) { viewMode, isEditMode, selectedNodeIds, palette, searchTriple ->
        DisplayConfig(
            viewMode = viewMode,
            isEditMode = isEditMode,
            selectedNodeIds = selectedNodeIds,
            unifiedPalette = palette,
            searchQuery = searchTriple.first,
            isSearching = searchTriple.second,
            activeTagFilter = searchTriple.third,
        )
    }

    // ── Layer 2: 组合为最终 UI 状态 ──

    val uiState: StateFlow<BookshelfUiState> = combine(
        booksFlow,
        sessionFlow,
        sortConfigFlow,
        displayConfigFlow,
        foldersFlow,
    ) { books, session, sortConfig, display, folders ->
        @Suppress("UNCHECKED_CAST")
        val bookRows = books as List<BookShelfRow>
        @Suppress("UNCHECKED_CAST")
        val folderEntities = folders as List<FolderEntity>

        val bookItems = bookRows.map { row ->
            val duration = session.durations[row.id] ?: 0L
            row.toBookItem(duration)
        }

        val booksByFolder = bookItems.groupBy { it.folderId }
        val rootNodes = mutableListOf<BookshelfNode>()

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

        val rootBooks = booksByFolder[null] ?: emptyList()
        rootNodes.addAll(rootBooks)

        val filtered = with(BookshelfSorting) { rootNodes.applyFilter(sortConfig.filterType) }
        val searched = if (display.isSearching && display.searchQuery.isBlank()) emptyList() else filtered
        val pinned = searched.filter { it.pinnedSlot != null }
        val autoSorted = with(BookshelfSorting) { searched.filter { it.pinnedSlot == null }.applySorting(sortConfig.sortOrder, sortConfig.isAscending) }
        val sorted = BookshelfSorting.mergePinnedSlots(pinned, autoSorted)

        BookshelfUiState(
            nodes = sorted,
            viewMode = display.viewMode,
            sortOrder = sortConfig.sortOrder,
            isAscending = sortConfig.isAscending,
            filterType = sortConfig.filterType,
            searchQuery = display.searchQuery,
            isSearching = display.isSearching,
            todayReadingTime = com.shuli.reader.core.util.StatsFormatter.formatDuration(session.todayTime).ifBlank { "0m" },
            todayReadingMinutes = session.todayTime / 60,
            isLoading = false,
            isEmpty = sorted.isEmpty(),
            isEditMode = display.isEditMode,
            selectedNodeIds = display.selectedNodeIds,
            unifiedCoverPaletteIndex = display.unifiedPalette,
            activeTagFilter = display.activeTagFilter,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BookshelfUiState(),
    )

    private var cachedDateKeyDay: Int = -1
    private var cachedDateKey: Int = 0

    private fun todayDateKey(): Int {
        val cal = java.util.Calendar.getInstance()
        val dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR) + cal.get(java.util.Calendar.YEAR) * 1000
        if (dayOfYear == cachedDateKeyDay) return cachedDateKey
        cachedDateKeyDay = dayOfYear
        cachedDateKey = cal.get(java.util.Calendar.YEAR) * 10000 +
            (cal.get(java.util.Calendar.MONTH) + 1) * 100 +
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        return cachedDateKey
    }

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

    fun applyTagFilter(tagName: String) {
        _activeTagFilter.value = tagName
        _filterType.value = FilterType.ALL
    }

    fun clearTagFilter() {
        _activeTagFilter.value = null
    }

    fun onBookClick(bookId: Long) {
        viewModelScope.launch {
            readingProgressRepository.updateLastReadTime(bookId)
            readingProgressRepository.markOpenedForReading(bookId)
            _events.emit(BookshelfEvent.NavigateToReader(bookId))
        }
    }

    fun onToggleFavorite(bookId: Long) {
        viewModelScope.launch {
            readingProgressRepository.toggleFavorite(bookId)
            _events.emit(BookshelfEvent.ShowMessage { it.bookshelf.favoriteToggled })
        }
    }

    fun updateStatus(bookId: Long, newStatus: ReadingStatus) {
        viewModelScope.launch {
            readingProgressRepository.updateReadingStatus(bookId, newStatus)
        }
    }

    fun batchUpdateStatus(newStatus: ReadingStatus) {
        viewModelScope.launch {
            _selectedNodeIds.value.forEach { nodeId ->
                if (nodeId > 0) {
                    readingProgressRepository.updateReadingStatus(nodeId, newStatus)
                }
            }
        }
    }

    fun onDeleteBook(bookId: Long) {
        viewModelScope.launch {
            bookQueryRepository.deleteBook(bookId)
            _events.emit(BookshelfEvent.ShowMessage { it.bookshelf.bookDeleted })
        }
    }

    // --- Edit Mode & Grouping Operations（委托 BookshelfEditManager）---

    fun onFolderClick(folderId: Long) = bookshelfEditManager.onFolderClick(folderId)
    fun onFolderDismiss() = bookshelfEditManager.onFolderDismiss()
    fun onToggleEditMode(selectNodeId: Long? = null) = bookshelfEditManager.onToggleEditMode(selectNodeId)
    fun onToggleNodeSelection(nodeId: Long) = bookshelfEditManager.onToggleNodeSelection(nodeId)
    fun onSelectAllNodes(nodes: List<BookshelfNode>) = bookshelfEditManager.onSelectAllNodes(nodes)
    fun onCreateFolderAndMove(folderName: String, sourceNodeId: Long, targetNodeId: Long) = bookshelfEditManager.onCreateFolderAndMove(folderName, sourceNodeId, targetNodeId)
    fun onMoveSelectedToFolder(folderId: Long?) = bookshelfEditManager.onMoveSelectedToFolder(folderId)
    fun deleteNodes(nodeIds: Set<Long>) = bookshelfEditManager.deleteNodes(nodeIds)
    fun onMoveSelectedToNewFolder(folderName: String) = bookshelfEditManager.onMoveSelectedToNewFolder(folderName)
    fun mergeNodes(sourceId: Long, targetId: Long, sourceIsFolder: Boolean, targetIsFolder: Boolean, defaultFolderName: String = "New Folder") = bookshelfEditManager.mergeNodes(sourceId, targetId, sourceIsFolder, targetIsFolder, defaultFolderName)
    fun pinNode(nodeId: Long, slot: Int) = bookshelfEditManager.pinNode(nodeId, slot)
    fun unpinNode(nodeId: Long) = bookshelfEditManager.unpinNode(nodeId)
    fun clearAllPinnedSlots() = bookshelfEditManager.clearAllPinnedSlots()
    fun commitDragResult(newNodes: List<BookshelfNode>) = bookshelfEditManager.commitDragResult(newNodes)
    fun setBookCoverPalette(bookId: Long, paletteIndex: Int?) = bookshelfEditManager.setBookCoverPalette(bookId, paletteIndex)

    // ── 书籍导入（委托 BookImportManager）──────────────────

    fun onImportBook(context: Context, uri: Uri) = bookImportManager.onImportBook(context, uri)
    fun onImportBooks(context: Context, uris: List<Uri>) = bookImportManager.onImportBooks(context, uris)
    fun onImportFolder(context: Context, uri: Uri) = bookImportManager.onImportFolder(context, uri)

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

    // ── 标签操作（P1）───────────────────────────────────────

    private val _tagSearchQuery = MutableStateFlow("")

    val allTags = tagRepository?.getAllTagsWithCount()
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        ?: flowOf(emptyList<com.shuli.reader.core.database.dao.TagWithCount>())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getBookTags(bookId: Long) =
        tagRepository?.getTagsForBook(bookId) ?: flowOf(emptyList())

    fun addTag(bookId: Long, tagName: String) {
        viewModelScope.launch {
            tagRepository?.addTagToBook(bookId, tagName)
        }
    }

    fun removeTag(bookId: Long, tagId: Long) {
        viewModelScope.launch {
            tagRepository?.removeTagFromBook(bookId, tagId)
        }
    }

    fun searchTagSuggestions(prefix: String) {
        _tagSearchQuery.value = prefix
    }

    suspend fun getTagSuggestions(prefix: String) =
        tagRepository?.searchTagsByPrefix(prefix) ?: emptyList()
}

