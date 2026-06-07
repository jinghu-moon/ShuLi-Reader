package com.shuli.reader.feature.bookshelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.bookshelf.component.BookDetailsSheet
import com.shuli.reader.feature.bookshelf.component.BookDetailsActions
import com.shuli.reader.feature.bookshelf.component.BookDetailsTagState
import com.shuli.reader.feature.bookshelf.component.BookDetailsTagActions
import com.shuli.reader.feature.bookshelf.component.SortBottomSheet
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.FolderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书架浮层控制器：管理所有对话框/底部表单的显示状态。
 *
 * 从 BookshelfScreen 拆出，SRP —— 只负责"浮层状态管理与渲染"这一变更轴。
 * 通过 [BookshelfOverlaysState] 暴露显示方法给 BookshelfScreen 调用。
 */
class BookshelfOverlaysState {
    var showSortSheet by mutableStateOf(false)
    var showImportOptionSheet by mutableStateOf(false)
    var showFolderImportDialog by mutableStateOf(false)
    var showStatisticsSheet by mutableStateOf(false)
    var selectedInfoBookId by mutableStateOf<Long?>(null)
    var selectedCoverColorBookId by mutableStateOf<Long?>(null)
    var showDeleteConfirmDialog by mutableStateOf(false)
    var showGroupDialog by mutableStateOf(false)
    var showMoreSheet by mutableStateOf(false)
    var folderFilesToImport by mutableStateOf<List<Pair<Uri, String>>>(emptyList())
}

@Composable
fun rememberBookshelfOverlaysState(): BookshelfOverlaysState = remember { BookshelfOverlaysState() }

@Composable
fun BookshelfOverlays(
    viewModel: BookshelfViewModel,
    state: BookshelfOverlaysState,
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val openFolderId by viewModel.openFolderId.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.onImportBooks(context, uris)
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let { treeUri ->
            scope.launch {
                val files = withContext(Dispatchers.IO) { getFilesFromTree(context, treeUri) }
                if (files.isNotEmpty()) {
                    state.folderFilesToImport = files
                    state.showFolderImportDialog = true
                } else {
                    viewModel.showToastMessage(strings.common.noBooksFound)
                }
            }
        }
    }

    // ── 排序 ──
    if (state.showSortSheet) {
        SortBottomSheet(
            selected = uiState.sortOrder,
            isAscending = uiState.isAscending,
            onSelect = viewModel::onSortOrderChanged,
            onDirectionSelect = viewModel::onSortDirectionChanged,
            onDismiss = { state.showSortSheet = false },
        )
    }

    // ── 导入 ──
    if (state.showImportOptionSheet) {
        ImportOptionBottomSheet(
            onImportFile = { filePicker.launch(arrayOf("text/plain", "application/epub+zip")) },
            onImportFolder = { folderPicker.launch(null) },
            onDismiss = { state.showImportOptionSheet = false },
        )
    }

    if (state.showFolderImportDialog && state.folderFilesToImport.isNotEmpty()) {
        FolderImportDialog(
            files = state.folderFilesToImport,
            onConfirm = { selectedUris -> viewModel.onImportBooks(context, selectedUris) },
            onDismiss = { state.showFolderImportDialog = false },
        )
    }

    // ── 统计 ──
    if (state.showStatisticsSheet) {
        val userPreferences = remember {
            (context.applicationContext as com.shuli.reader.ShuLiApplication).appContainer.userPreferences
        }
        val dailyTargetMinutes by userPreferences.readingDailyTarget.collectAsState(initial = 30)
        val allBooks = uiState.nodes.filterIsInstance<BookItem>()
        val statusDist = com.shuli.reader.core.reading.ReadingStatus.entries.associateWith { status ->
            allBooks.count { it.readingStatus == status }
        }
        val rereadBooks = allBooks.count { it.readCount > 1 }
        val allTags by viewModel.allTags.collectAsState()
        val topTagsList = allTags.take(5).map { it.name to it.usageCount }

        StatisticsBottomSheet(
            booksCount = uiState.nodes.size,
            todayReadingTime = uiState.todayReadingTime,
            todayReadingMinutes = uiState.todayReadingMinutes,
            dailyTargetMinutes = dailyTargetMinutes,
            onDismiss = { state.showStatisticsSheet = false },
            statusDistribution = statusDist,
            rereadCount = rereadBooks,
            topTags = topTagsList,
        )
    }

    // ── 书籍详情 ──
    state.selectedInfoBookId?.let { id ->
        val book = uiState.nodes.firstOrNull { it.id == id } as? BookItem
        book?.let { item ->
            val currentBookTags by viewModel.getBookTags(item.id).collectAsState(initial = emptyList())
            val allTags by viewModel.allTags.collectAsState()

            BookDetailsSheet(
                book = item,
                actions = BookDetailsActions(
                    onStatusChange = { newStatus -> viewModel.updateStatus(item.id, newStatus) },
                    onExportNotes = { /* P3: export notes */ },
                    onDeleteBook = { viewModel.onDeleteBook(item.id) },
                    onContinueReading = {
                        state.selectedInfoBookId = null
                        viewModel.onBookClick(item.id)
                    },
                ),
                tagState = BookDetailsTagState(
                    tags = currentBookTags,
                    suggestions = allTags,
                ),
                tagActions = BookDetailsTagActions(
                    onTagAdd = { tagName -> viewModel.addTag(item.id, tagName) },
                    onTagRemove = { tagId -> viewModel.removeTag(item.id, tagId) },
                    onTagClick = { tagName ->
                        state.selectedInfoBookId = null
                        viewModel.applyTagFilter(tagName)
                    },
                    onSearchTags = { prefix -> viewModel.searchTagSuggestions(prefix) },
                ),
                onDismiss = { state.selectedInfoBookId = null },
            )
        }
    }

    // ── 封面色盘 ──
    if (state.selectedCoverColorBookId != null) {
        val targetBook = uiState.nodes.firstOrNull { it.id == state.selectedCoverColorBookId } as? BookItem
        com.shuli.reader.feature.bookshelf.component.CoverColorPickerDialog(
            currentIndex = targetBook?.customCoverPaletteIndex,
            onSelected = { index ->
                state.selectedCoverColorBookId?.let { viewModel.setBookCoverPalette(it, index) }
                state.selectedCoverColorBookId = null
            },
            onDismiss = { state.selectedCoverColorBookId = null },
        )
    }

    // ── 编辑模式：删除确认 ──
    if (state.showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            count = uiState.selectedNodeIds.size,
            onConfirm = {
                viewModel.deleteNodes(uiState.selectedNodeIds)
                state.showDeleteConfirmDialog = false
            },
            onDismiss = { state.showDeleteConfirmDialog = false },
        )
    }

    // ── 编辑模式：分组 ──
    if (state.showGroupDialog) {
        val existingFolders = uiState.nodes.filterIsInstance<FolderItem>()
        GroupPickerDialog(
            folders = existingFolders,
            onMoveToFolder = { folderId ->
                viewModel.onMoveSelectedToFolder(folderId)
                state.showGroupDialog = false
            },
            onCreateNewFolder = { folderName ->
                val selectedIds = uiState.selectedNodeIds.toList()
                if (selectedIds.isNotEmpty()) viewModel.onMoveSelectedToNewFolder(folderName)
                state.showGroupDialog = false
            },
            onRemoveFromFolder = {
                viewModel.onMoveSelectedToFolder(null)
                state.showGroupDialog = false
            },
            onDismiss = { state.showGroupDialog = false },
        )
    }

    // ── 编辑模式：更多 ──
    if (state.showMoreSheet) {
        val selectedBooks = uiState.nodes
            .filterIsInstance<BookItem>()
            .filter { it.id in uiState.selectedNodeIds }
        MoreActionsSheet(
            selectedBooks = selectedBooks,
            onToggleFavorite = {
                selectedBooks.forEach { viewModel.onToggleFavorite(it.id) }
                state.showMoreSheet = false
            },
            onShowInfo = {
                selectedBooks.firstOrNull()?.let { state.selectedInfoBookId = it.id }
                state.showMoreSheet = false
            },
            onCustomizeCover = {
                selectedBooks.firstOrNull()?.let { state.selectedCoverColorBookId = it.id }
                state.showMoreSheet = false
            },
            onMoveOut = {
                viewModel.onMoveSelectedToFolder(null)
                state.showMoreSheet = false
            },
            onDismiss = { state.showMoreSheet = false },
            onBatchStatusChange = { newStatus ->
                viewModel.batchUpdateStatus(newStatus)
                state.showMoreSheet = false
            },
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
