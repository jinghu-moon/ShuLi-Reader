package com.shuli.reader.feature.bookshelf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shuli.reader.feature.bookshelf.component.BookGrid
import com.shuli.reader.feature.bookshelf.component.BookList
import com.shuli.reader.feature.bookshelf.component.BookshelfTopBar
import com.shuli.reader.feature.bookshelf.component.FilterTabs
import com.shuli.reader.feature.bookshelf.component.SortBottomSheet
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.ViewMode

@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    onNavigateToReader: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    var highlightedBookId by remember { mutableStateOf<Long?>(null) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showImportOptionSheet by remember { mutableStateOf(false) }
    var folderFilesToImport by remember { mutableStateOf<List<Pair<Uri, String>>>(emptyList()) }
    var showFolderImportDialog by remember { mutableStateOf(false) }

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
                    viewModel.showToastMessage("该文件夹下未找到 TXT 或 EPUB 文件")
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
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
                is BookshelfEvent.HighlightBook -> {
                    val currentBooks = viewModel.uiState.value.books
                    val index = currentBooks.indexOfFirst { it.id == event.bookId }
                    if (index != -1) {
                        highlightedBookId = event.bookId
                        scope.launch {
                            if (viewModel.uiState.value.viewMode == ViewMode.GRID) {
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
                BookshelfTopBar(
                    todayReadingTime = uiState.todayReadingTime,
                    viewMode = uiState.viewMode,
                    onViewModeToggle = {
                        val newMode = if (uiState.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                        viewModel.onViewModeChanged(newMode)
                    },
                    onSortClick = { showSortSheet = true },
                    isSearching = uiState.isSearching,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChanged,
                    onSearchActiveChange = viewModel::onSearchActiveChanged,
                )
                if (!uiState.isSearching) {
                    FilterTabs(
                        selected = uiState.filterType,
                        onSelect = viewModel::onFilterChanged,
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showImportOptionSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "导入书籍")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
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
                        books = uiState.books,
                        viewMode = uiState.viewMode,
                        gridState = gridState,
                        listState = listState,
                        highlightedBookId = highlightedBookId,
                        onBookClick = viewModel::onBookClick,
                        onToggleFavorite = viewModel::onToggleFavorite,
                        onDelete = viewModel::onDeleteBook,
                        onShowInfo = { /* TODO: 显示书籍信息 */ },
                        searchQuery = uiState.searchQuery,
                    )
                }
            }
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            selected = uiState.sortOrder,
            isAscending = uiState.isAscending,
            onSelect = viewModel::onSortOrderChanged,
            onDirectionSelect = viewModel::onSortDirectionChanged,
            onDismiss = { showSortSheet = false },
        )
    }

    if (showImportOptionSheet) {
        ImportOptionBottomSheet(
            onImportFile = {
                filePicker.launch(arrayOf("text/plain", "application/epub+zip"))
            },
            onImportFolder = {
                folderPicker.launch(null)
            },
            onDismiss = { showImportOptionSheet = false }
        )
    }

    if (showFolderImportDialog && folderFilesToImport.isNotEmpty()) {
        FolderImportDialog(
            files = folderFilesToImport,
            onConfirm = { selectedUris ->
                viewModel.onImportBooks(context, selectedUris)
            },
            onDismiss = { showFolderImportDialog = false }
        )
    }
}

@Composable
private fun BookContent(
    books: List<BookItem>,
    viewMode: ViewMode,
    gridState: LazyGridState,
    listState: LazyListState,
    highlightedBookId: Long?,
    onBookClick: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onShowInfo: (Long) -> Unit,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    when (viewMode) {
        ViewMode.GRID -> BookGrid(
            books = books,
            searchQuery = searchQuery,
            gridState = gridState,
            highlightedBookId = highlightedBookId,
            onBookClick = onBookClick,
            onToggleFavorite = onToggleFavorite,
            onDelete = onDelete,
            onShowInfo = onShowInfo,
            modifier = modifier,
        )
        ViewMode.LIST -> BookList(
            books = books,
            searchQuery = searchQuery,
            listState = listState,
            highlightedBookId = highlightedBookId,
            onBookClick = onBookClick,
            onToggleFavorite = onToggleFavorite,
            onDelete = onDelete,
            onShowInfo = onShowInfo,
            modifier = modifier,
        )
    }
}

@Composable
private fun EmptyState(
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isSearching) "未找到相关书籍" else "书架空空如也",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isSearching) "请尝试输入其他关键词" else "点击右下角 + 导入 TXT 或 EPUB 文件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SearchGuideState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "搜索书籍",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "支持在整个书架中秒级检索书名",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

private fun getFilesFromTree(context: Context, treeUri: Uri): List<Pair<Uri, String>> {
    val files = mutableListOf<Pair<Uri, String>>()
    try {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childQueryUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        
        context.contentResolver.query(childQueryUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIndex)
                val displayName = cursor.getString(nameIndex) ?: ""
                val mimeType = cursor.getString(mimeIndex) ?: ""
                
                val isText = displayName.endsWith(".txt", ignoreCase = true) || mimeType == "text/plain"
                val isEpub = displayName.endsWith(".epub", ignoreCase = true) || mimeType == "application/epub+zip"
                
                if (isText || isEpub) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    files.add(fileUri to displayName)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return files
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportOptionBottomSheet(
    onImportFile: () -> Unit,
    onImportFolder: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "选择导入方式",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onImportFile()
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("导入书籍文件", style = MaterialTheme.typography.bodyLarge)
                    Text("支持多选导入 TXT 或 EPUB 文件", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onImportFolder()
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("选择文件夹导入", style = MaterialTheme.typography.bodyLarge)
                    Text("自动扫描文件夹内所有可导入的书籍", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun FolderImportDialog(
    files: List<Pair<Uri, String>>,
    onConfirm: (List<Uri>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedStates = remember(files) {
        mutableStateMapOf<Uri, Boolean>().apply {
            files.forEach { this[it.first] = true }
        }
    }

    val selectedCount = selectedStates.values.count { it }
    val isAllSelected = selectedCount == files.size

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择要导入的书籍",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val target = !isAllSelected
                            files.forEach { selectedStates[it.first] = target }
                        }
                    ) {
                        Text(if (isAllSelected) "取消全选" else "全选")
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 350.dp)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(files) { (uri, name) ->
                            val isSelected = selectedStates[uri] ?: false
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedStates[uri] = !isSelected }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (name.endsWith(".epub", ignoreCase = true)) 
                                        Icons.Default.Book else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                androidx.compose.material3.Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { selectedStates[uri] = it }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            val selectedUris = files.map { it.first }.filter { selectedStates[it] == true }
                            onConfirm(selectedUris)
                            onDismiss()
                        },
                        enabled = selectedCount > 0
                    ) {
                        Text("导入所选 ($selectedCount)")
                    }
                }
            }
        }
    }
}
