package com.shuli.reader.feature.bookshelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.bookshelf.model.BookshelfNode
import com.shuli.reader.feature.bookshelf.model.ViewMode
import com.shuli.reader.feature.bookshelf.component.BookshelfTopBar
import com.shuli.reader.feature.bookshelf.component.FilterTabs
import com.shuli.reader.ui.testing.UiTestTags
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToReader: (Long) -> Unit,
    onNavigateToStats: () -> Unit = {},
    onNavigateToGlobalSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var highlightedBookId by remember { mutableStateOf<Long?>(null) }
    val overlaysState = rememberBookshelfOverlaysState()

    // 搜索/编辑模式返回拦截
    BackHandler(enabled = uiState.isSearching) { viewModel.onSearchActiveChanged(false) }
    BackHandler(enabled = uiState.isEditMode) { viewModel.onToggleEditMode() }

    // 事件处理
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BookshelfEvent.NavigateToReader -> onNavigateToReader(event.bookId)
                is BookshelfEvent.ShowMessage -> {
                    scope.launch { snackbarHostState.showSnackbar(event.message(strings)) }
                }
                is BookshelfEvent.HighlightBook -> {
                    val index = viewModel.uiState.value.nodes.indexOfFirst { it.id == event.bookId }
                    if (index != -1) {
                        highlightedBookId = event.bookId
                        scope.launch {
                            if (viewModel.uiState.value.viewMode == ViewMode.GRID) gridState.animateScrollToItem(index)
                            else listState.animateScrollToItem(index)
                        }
                        scope.launch {
                            delay(2500)
                            if (highlightedBookId == event.bookId) highlightedBookId = null
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
                        onSelectAll = { viewModel.onSelectAllNodes(uiState.nodes) },
                    )
                } else {
                    BookshelfTopBar(
                        todayReadingTime = uiState.todayReadingTime,
                        viewMode = uiState.viewMode,
                        onViewModeChange = viewModel::onViewModeChanged,
                        onSortClick = { overlaysState.showSortSheet = true },
                        isSearching = uiState.isSearching,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = viewModel::onSearchQueryChanged,
                        onSearchActiveChange = viewModel::onSearchActiveChanged,
                        onFullTextSearchClick = onNavigateToGlobalSearch,
                        onStatisticsClick = onNavigateToStats,
                        onSettingsClick = onNavigateToSettings,
                    )
                    if (!uiState.isSearching) {
                        FilterTabs(selected = uiState.filterType, onSelect = viewModel::onFilterChanged)
                        uiState.activeTagFilter?.let { tagName ->
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                AssistChip(
                                    onClick = { viewModel.clearTagFilter() },
                                    label = {
                                        Text(
                                            text = "#$tagName",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = strings.bookshelf.clearFilter,
                                            modifier = Modifier.padding(0.dp),
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (uiState.isEditMode) {
                EditModeBottomBar(
                    selectedCount = uiState.selectedNodeIds.size,
                    onGroupClick = { overlaysState.showGroupDialog = true },
                    onDeleteClick = { overlaysState.showDeleteConfirmDialog = true },
                    onMoreClick = { overlaysState.showMoreSheet = true },
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isEditMode) {
                FloatingActionButton(
                    onClick = { overlaysState.showImportOptionSheet = true },
                    modifier = Modifier.testTag(UiTestTags.BOOKSHELF_IMPORT_FAB),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = strings.bookshelf.libraryImportSettings) }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.testTag(UiTestTags.BOOKSHELF_SCREEN),
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.isEmpty -> {
                    if (uiState.isSearching && uiState.searchQuery.isBlank()) {
                        SearchGuideState(modifier = Modifier.align(Alignment.Center))
                    } else {
                        EmptyState(isSearching = uiState.isSearching, modifier = Modifier.align(Alignment.Center))
                    }
                }
                else -> BookContent(
                    books = uiState.nodes,
                    viewMode = uiState.viewMode,
                    gridState = gridState,
                    listState = listState,
                    highlightedBookId = highlightedBookId,
                    onBookClick = viewModel::onBookClick,
                    onFolderClick = viewModel::onFolderClick,
                    onShowInfo = { overlaysState.selectedInfoBookId = it },
                    searchQuery = uiState.searchQuery,
                    unifiedCoverPaletteIndex = uiState.unifiedCoverPaletteIndex,
                    onCustomizeCover = { overlaysState.selectedCoverColorBookId = it },
                    isEditMode = uiState.isEditMode,
                    selectedNodeIds = uiState.selectedNodeIds,
                    onToggleSelection = viewModel::onToggleNodeSelection,
                    onLongPressToEdit = { nodeId -> viewModel.onToggleEditMode(nodeId) },
                    onDragToSlot = { nodeId, slot -> viewModel.pinNode(nodeId, slot) },
                    onMerge = { sourceId, targetId -> viewModel.mergeNodes(sourceId, targetId, sourceIsFolder = false, targetIsFolder = false, defaultFolderName = strings.bookshelf.newFolder) },
                )
            }
        }
    }

    // 浮层（对话框/底部表单）
    BookshelfOverlays(viewModel = viewModel, state = overlaysState)
}
