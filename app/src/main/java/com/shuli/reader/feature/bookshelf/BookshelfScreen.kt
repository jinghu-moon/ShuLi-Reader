package com.shuli.reader.feature.bookshelf

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.bookshelf.component.BookGrid
import com.shuli.reader.feature.bookshelf.component.BookInfoBottomSheet
import com.shuli.reader.feature.bookshelf.component.BookList
import com.shuli.reader.feature.bookshelf.component.BookshelfTopBar
import com.shuli.reader.feature.bookshelf.component.FilterTabs
import com.shuli.reader.feature.bookshelf.component.SortBottomSheet
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.BookshelfNode
import com.shuli.reader.feature.bookshelf.model.FolderItem
import com.shuli.reader.ui.testing.UiTestTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToReader: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val openFolderId by viewModel.openFolderId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val userPreferences = remember {
        (context.applicationContext as com.shuli.reader.ShuLiApplication).appContainer.userPreferences
    }
    val dailyTargetMinutes by userPreferences.readingDailyTarget.collectAsState(initial = 30)

    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var highlightedBookId by remember { mutableStateOf<Long?>(null) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showImportOptionSheet by remember { mutableStateOf(false) }
    var folderFilesToImport by remember { mutableStateOf<List<Pair<Uri, String>>>(emptyList()) }
    var showFolderImportDialog by remember { mutableStateOf(false) }
    var showStatisticsSheet by remember { mutableStateOf(false) }
    var selectedInfoBookId by remember { mutableStateOf<Long?>(null) }
    var selectedCoverColorBookId by remember { mutableStateOf<Long?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    // 搜索激活时返回先退出搜索，不退出 App
    BackHandler(enabled = uiState.isSearching) {
        viewModel.onSearchActiveChanged(false)
    }

    // 编辑模式下返回先退出编辑，不退出 App
    BackHandler(enabled = uiState.isEditMode) {
        viewModel.onToggleEditMode()
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onImportBooks(context, uris)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let { treeUri ->
            scope.launch {
                val files = withContext(Dispatchers.IO) {
                    getFilesFromTree(context, treeUri)
                }
                if (files.isNotEmpty()) {
                    folderFilesToImport = files
                    showFolderImportDialog = true
                } else {
                    viewModel.showToastMessage(strings.noBooksFound)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BookshelfEvent.NavigateToReader -> onNavigateToReader(event.bookId)
                is BookshelfEvent.ShowMessage -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(event.message(strings))
                    }
                }
                is BookshelfEvent.HighlightBook -> {
                    val currentNodes = viewModel.uiState.value.nodes
                    val index = currentNodes.indexOfFirst { it.id == event.bookId }
                    if (index != -1) {
                        highlightedBookId = event.bookId
                        scope.launch {
                            if (viewModel.uiState.value.viewMode == com.shuli.reader.feature.bookshelf.model.ViewMode.GRID) {
                                gridState.animateScrollToItem(index)
                            } else {
                                listState.animateScrollToItem(index)
                            }
                        }
                        scope.launch {
                            delay(2500)
                            if (highlightedBookId == event.bookId) {
                                highlightedBookId = null
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                if (uiState.isEditMode) {
                    EditModeTopBar(
                        selectedCount = uiState.selectedNodeIds.size,
                        onCancel = viewModel::onToggleEditMode,
                        onSelectAll = { viewModel.onSelectAllNodes(uiState.nodes) }
                    )
                } else {
                    BookshelfTopBar(
                        todayReadingTime = uiState.todayReadingTime,
                        viewMode = uiState.viewMode,
                        onViewModeToggle = {
                            val newMode = if (uiState.viewMode == com.shuli.reader.feature.bookshelf.model.ViewMode.GRID) com.shuli.reader.feature.bookshelf.model.ViewMode.LIST else com.shuli.reader.feature.bookshelf.model.ViewMode.GRID
                            viewModel.onViewModeChanged(newMode)
                        },
                        onSortClick = { showSortSheet = true },
                        isSearching = uiState.isSearching,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChanged,
                        onSearchActiveChange = viewModel::onSearchActiveChanged,
                        onStatisticsClick = { showStatisticsSheet = true },
                        onSettingsClick = onNavigateToSettings,
                    )
                    if (!uiState.isSearching) {
                        FilterTabs(
                            selected = uiState.filterType,
                            onSelect = viewModel::onFilterChanged,
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (uiState.isEditMode) {
                EditModeBottomBar(
                    selectedCount = uiState.selectedNodeIds.size,
                    onGroupClick = { showGroupDialog = true },
                    onDeleteClick = { showDeleteConfirmDialog = true },
                    onMoreClick = { showMoreSheet = true },
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isEditMode) {
                FloatingActionButton(
                    onClick = { showImportOptionSheet = true },
                    modifier = Modifier.testTag(UiTestTags.BOOKSHELF_IMPORT_FAB),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = strings.libraryImportSettings)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.testTag(UiTestTags.BOOKSHELF_SCREEN),
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                uiState.isEmpty -> {
                    if (uiState.isSearching && uiState.searchQuery.isBlank()) {
                        SearchGuideState(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        EmptyState(
                            isSearching = uiState.isSearching,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                else -> {
                    BookContent(
                        books = uiState.nodes,
                        viewMode = uiState.viewMode,
                        gridState = gridState,
                        listState = listState,
                        highlightedBookId = highlightedBookId,
                        onBookClick = viewModel::onBookClick,
                        onFolderClick = viewModel::onFolderClick,
                        onShowInfo = { selectedInfoBookId = it },
                        searchQuery = uiState.searchQuery,
                        unifiedCoverPaletteIndex = uiState.unifiedCoverPaletteIndex,
                        onCustomizeCover = { selectedCoverColorBookId = it },
                        isEditMode = uiState.isEditMode,
                        selectedNodeIds = uiState.selectedNodeIds,
                        onToggleSelection = viewModel::onToggleNodeSelection,
                        onLongPressToEdit = { nodeId -> viewModel.onToggleEditMode(nodeId) },
                        onReorder = { reorderedNodes -> viewModel.commitOrderToDatabase(reorderedNodes) },
                        onMerge = { sourceId, targetId -> viewModel.mergeNodes(sourceId, targetId, sourceIsFolder = false, targetIsFolder = false) },
                    )
                }
            }
        }
    }

    // ── 排序 ──
    if (showSortSheet) {
        SortBottomSheet(
            selected = uiState.sortOrder,
            isAscending = uiState.isAscending,
            onSelect = viewModel::onSortOrderChanged,
            onDirectionSelect = viewModel::onSortDirectionChanged,
            onDismiss = { showSortSheet = false },
        )
    }

    // ── 导入 ──
    if (showImportOptionSheet) {
        ImportOptionBottomSheet(
            onImportFile = {
                filePicker.launch(arrayOf("text/plain", "application/epub+zip"))
            },
            onImportFolder = {
                folderPicker.launch(null)
            },
            onDismiss = { showImportOptionSheet = false },
        )
    }

    if (showFolderImportDialog && folderFilesToImport.isNotEmpty()) {
        FolderImportDialog(
            files = folderFilesToImport,
            onConfirm = { selectedUris ->
                viewModel.onImportBooks(context, selectedUris)
            },
            onDismiss = { showFolderImportDialog = false },
        )
    }

    // ── 统计 ──
    if (showStatisticsSheet) {
        StatisticsBottomSheet(
            booksCount = uiState.nodes.size,
            todayReadingTime = uiState.todayReadingTime,
            todayReadingMinutes = uiState.todayReadingMinutes,
            dailyTargetMinutes = dailyTargetMinutes,
            onDismiss = { showStatisticsSheet = false },
        )
    }

    // ── 书籍信息 ──
    if (selectedInfoBookId != null) {
        BookInfoBottomSheet(
            book = uiState.nodes.firstOrNull { it.id == selectedInfoBookId } as? BookItem,
            onDismiss = { selectedInfoBookId = null },
        )
    }

    // ── 封面色盘 ──
    if (selectedCoverColorBookId != null) {
        val targetBook = uiState.nodes.firstOrNull { it.id == selectedCoverColorBookId } as? BookItem
        com.shuli.reader.feature.bookshelf.component.CoverColorPickerDialog(
            currentIndex = targetBook?.customCoverPaletteIndex,
            onSelected = { index ->
                selectedCoverColorBookId?.let { viewModel.setBookCoverPalette(it, index) }
                selectedCoverColorBookId = null
            },
            onDismiss = { selectedCoverColorBookId = null },
        )
    }

    // ── 编辑模式：删除确认 ──
    if (showDeleteConfirmDialog) {
        val count = uiState.selectedNodeIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 $count 项吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNodes(uiState.selectedNodeIds)
                    showDeleteConfirmDialog = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    // ── 编辑模式：分组 ──
    if (showGroupDialog) {
        val existingFolders = uiState.nodes.filterIsInstance<FolderItem>()
        GroupPickerDialog(
            folders = existingFolders,
            onMoveToFolder = { folderId ->
                viewModel.onMoveSelectedToFolder(folderId)
                showGroupDialog = false
            },
            onCreateNewFolder = { folderName ->
                val selectedIds = uiState.selectedNodeIds.toList()
                if (selectedIds.isNotEmpty()) {
                    viewModel.onMoveSelectedToNewFolder(folderName)
                }
                showGroupDialog = false
            },
            onRemoveFromFolder = {
                viewModel.onMoveSelectedToFolder(null)
                showGroupDialog = false
            },
            onDismiss = { showGroupDialog = false },
        )
    }

    // ── 编辑模式：更多 ──
    if (showMoreSheet) {
        MoreActionsSheet(
            onSelectAll = {
                viewModel.onSelectAllNodes(uiState.nodes)
                showMoreSheet = false
            },
            onMoveOut = {
                viewModel.onMoveSelectedToFolder(null)
                showMoreSheet = false
            },
            onDismiss = { showMoreSheet = false },
        )
    }

    // ── 文件夹详情 ──
    if (openFolderId != null) {
        val folder = uiState.nodes.filterIsInstance<FolderItem>()
            .firstOrNull { it.id == openFolderId }
        if (folder != null) {
            FolderDetailSheet(
                folder = folder,
                onBookClick = { bookId ->
                    viewModel.onFolderDismiss()
                    viewModel.onBookClick(bookId)
                },
                onDismiss = viewModel::onFolderDismiss,
            )
        }
    }
}

@Composable
private fun BookContent(
    books: List<BookshelfNode>,
    viewMode: com.shuli.reader.feature.bookshelf.model.ViewMode,
    gridState: LazyGridState,
    listState: LazyListState,
    highlightedBookId: Long?,
    onBookClick: (Long) -> Unit,
    onFolderClick: (Long) -> Unit = {},
    onShowInfo: (Long) -> Unit,
    searchQuery: String,
    modifier: Modifier = Modifier,
    unifiedCoverPaletteIndex: Int? = null,
    onCustomizeCover: ((Long) -> Unit)? = null,
    isEditMode: Boolean = false,
    selectedNodeIds: Set<Long> = emptySet(),
    onToggleSelection: (Long) -> Unit = {},
    onLongPressToEdit: (Long) -> Unit = {},
    onReorder: (List<BookshelfNode>) -> Unit = {},
    onMerge: (Long, Long) -> Unit = { _, _ -> },
) {
    when (viewMode) {
        com.shuli.reader.feature.bookshelf.model.ViewMode.GRID -> BookGrid(
            books = books,
            searchQuery = searchQuery,
            highlightedBookId = highlightedBookId,
            onBookClick = onBookClick,
            onFolderClick = onFolderClick,
            onShowInfo = onShowInfo,
            modifier = modifier,
            unifiedCoverPaletteIndex = unifiedCoverPaletteIndex,
            onCustomizeCover = onCustomizeCover,
            isEditMode = isEditMode,
            selectedNodeIds = selectedNodeIds,
            onToggleSelection = onToggleSelection,
            onLongPressToEdit = onLongPressToEdit,
            onReorder = onReorder,
            onMerge = onMerge,
        )
        com.shuli.reader.feature.bookshelf.model.ViewMode.LIST -> BookList(
            books = books,
            searchQuery = searchQuery,
            listState = listState,
            highlightedBookId = highlightedBookId,
            onBookClick = onBookClick,
            onFolderClick = onFolderClick,
            onShowInfo = onShowInfo,
            modifier = modifier,
            unifiedCoverPaletteIndex = unifiedCoverPaletteIndex,
            onCustomizeCover = onCustomizeCover,
            isEditMode = isEditMode,
            selectedNodeIds = selectedNodeIds,
            onToggleSelection = onToggleSelection,
            onLongPressToEdit = onLongPressToEdit,
            onReorder = onReorder,
            onMerge = onMerge,
        )
    }
}

@Composable
private fun EmptyState(
    isSearching: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isSearching) strings.noBooksFound else strings.emptyBookshelf,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchGuideState(modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = strings.searchIconDesc,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = strings.searchPlaceholder,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}
