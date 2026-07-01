package com.shuli.reader.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.repository.GlobalSearchBookOption
import com.shuli.reader.core.repository.GlobalSearchResult
import com.shuli.reader.core.repository.SearchIndexBackfillProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    viewModel: GlobalSearchViewModel,
    onNavigateBack: () -> Unit,
    onResultClick: (bookId: Long, chapterIndex: Int, byteOffset: Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var showBookPicker by remember { mutableStateOf(false) }
    var pickerSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (showBookPicker) {
        BookScopePickerDialog(
            books = state.bookOptions,
            selectedBookIds = pickerSelection,
            onToggleBook = { bookId ->
                pickerSelection = if (bookId in pickerSelection) {
                    pickerSelection - bookId
                } else {
                    pickerSelection + bookId
                }
            },
            onSelectAll = { pickerSelection = state.bookOptions.map { it.id }.toSet() },
            onClear = { pickerSelection = emptySet() },
            onDismiss = { showBookPicker = false },
            onConfirm = {
                viewModel.onSelectedBooksConfirmed(pickerSelection)
                showBookPicker = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = { Text("搜索全部书籍内容...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchScopeChips(
                scopeMode = state.scopeMode,
                currentGroupLabel = state.currentGroupLabel,
                currentGroupCount = state.currentGroupBookIds.size,
                selectedBookCount = state.selectedBookIds.size,
                onAllScopeClick = viewModel::onAllScopeSelected,
                onCurrentGroupScopeClick = viewModel::onCurrentGroupScopeSelected,
                onSelectedBooksClick = {
                    pickerSelection = if (state.scopeMode == SearchScopeMode.SELECTED_BOOKS) {
                        state.selectedBookIds
                    } else {
                        emptySet()
                    }
                    showBookPicker = true
                },
            )

            IndexBackfillPanel(
                coverage = state.indexCoverage,
                progress = state.backfillProgress,
                onStart = viewModel::startBackfillMissingIndexes,
                onCancel = viewModel::cancelBackfill,
            )

            AnimatedVisibility(
                visible = state.status == SearchStatus.SEARCHING,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    state.progress?.let { progress ->
                        Text(
                            text = "已扫描 ${progress.processedChapters} 章，找到 ${progress.resultsFound} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            when {
                state.status == SearchStatus.ERROR -> {
                    ErrorHint(
                        message = state.errorMessage.orEmpty(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.status == SearchStatus.IDLE -> {
                    if (state.query.isBlank() && state.searchHistory.isNotEmpty()) {
                        SearchHistoryList(
                            history = state.searchHistory,
                            onHistoryClick = viewModel::onHistoryQuerySelected,
                            onClearHistory = viewModel::clearSearchHistory,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        EmptySearchHint(
                            query = state.query,
                            coverage = state.indexCoverage,
                            onStartBackfill = viewModel::startBackfillMissingIndexes,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                state.status == SearchStatus.DONE && state.results.isEmpty() -> {
                    NoResultsHint(
                        query = state.query,
                        scopeBookCount = state.scopeBookCount,
                        indexedBookCountInScope = state.indexedBookCountInScope,
                        onStartBackfill = viewModel::startBackfillMissingIndexes,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    SearchResultList(
                        results = state.results,
                        query = state.query,
                        expandedBookIds = state.expandedBookIds,
                        totalResults = state.totalResults,
                        onToggleBookExpansion = viewModel::onResultBookExpansionToggled,
                        onResultClick = onResultClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchScopeChips(
    scopeMode: SearchScopeMode,
    currentGroupLabel: String,
    currentGroupCount: Int,
    selectedBookCount: Int,
    onAllScopeClick: () -> Unit,
    onCurrentGroupScopeClick: () -> Unit,
    onSelectedBooksClick: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = scopeMode == SearchScopeMode.ALL,
            onClick = onAllScopeClick,
            label = { Text("全部") },
            leadingIcon = selectedChipIcon(scopeMode == SearchScopeMode.ALL),
        )
        FilterChip(
            selected = scopeMode == SearchScopeMode.CURRENT_GROUP,
            enabled = currentGroupCount > 0,
            onClick = onCurrentGroupScopeClick,
            label = { Text("$currentGroupLabel $currentGroupCount 本") },
            leadingIcon = selectedChipIcon(scopeMode == SearchScopeMode.CURRENT_GROUP),
        )
        FilterChip(
            selected = scopeMode == SearchScopeMode.SELECTED_BOOKS,
            onClick = onSelectedBooksClick,
            label = {
                Text(if (selectedBookCount > 0) "指定 $selectedBookCount 本" else "指定书籍")
            },
            leadingIcon = selectedChipIcon(scopeMode == SearchScopeMode.SELECTED_BOOKS),
        )
    }
}

@Composable
private fun selectedChipIcon(selected: Boolean): (@Composable () -> Unit)? {
    if (!selected) return null
    return {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            modifier = Modifier.size(FilterChipDefaults.IconSize),
        )
    }
}

@Composable
private fun IndexBackfillPanel(
    coverage: SearchIndexCoverage,
    progress: SearchIndexBackfillProgress?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val running = progress?.isRunning == true
    val completed = progress?.isCompleted == true
    val fraction = when {
        progress == null -> 0f
        progress.totalBooks == 0 -> 1f
        else -> (progress.processedBooks.toFloat() / progress.totalBooks.toFloat()).coerceIn(0f, 1f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            running -> "正在索引：${progress.currentBookTitle ?: "准备中"}"
                            completed -> "索引检查完成"
                            progress != null -> "索引回填已停止"
                            coverage.totalBooks == 0 -> "旧书正文索引"
                            coverage.missingBooks == 0 -> "正文索引已完整"
                            else -> "旧书正文索引"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = progress?.let {
                            "已检查 ${it.processedBooks}/${it.totalBooks} · 新增 ${it.indexedBooks} · 跳过 ${it.skippedBooks} · 失败 ${it.failedBooks}"
                        } ?: when {
                            coverage.totalBooks == 0 -> "书架暂无书籍"
                            coverage.missingBooks == 0 -> "已索引 ${coverage.indexedBooks}/${coverage.totalBooks}"
                            else -> "已索引 ${coverage.indexedBooks}/${coverage.totalBooks} · 待补 ${coverage.missingBooks}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (running) {
                    TextButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("停止")
                    }
                } else {
                    TextButton(
                        onClick = onStart,
                        enabled = coverage.totalBooks > 0,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                completed -> "再次检查"
                                coverage.missingBooks == 0 -> "检查"
                                else -> "补全"
                            }
                        )
                    }
                }
            }

            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BookScopePickerDialog(
    books: List<GlobalSearchBookOption>,
    selectedBookIds: Set<Long>,
    onToggleBook: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("指定书籍") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onSelectAll, enabled = books.isNotEmpty()) {
                        Text("全选")
                    }
                    TextButton(onClick = onClear, enabled = selectedBookIds.isNotEmpty()) {
                        Text("清空")
                    }
                }

                if (books.isEmpty()) {
                    Text(
                        text = "书架暂无可选书籍",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                    ) {
                        items(
                            items = books,
                            key = { it.id },
                        ) { book ->
                            val checked = book.id in selectedBookIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleBook(book.id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onToggleBook(book.id) },
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp),
                                ) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildBookScopeSubtitle(book),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedBookIds.isNotEmpty(),
            ) {
                Text("搜索 ${selectedBookIds.size} 本")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun buildBookScopeSubtitle(book: GlobalSearchBookOption): String {
    val indexStatus = if (book.isIndexed) "已索引" else "未索引"
    return book.author?.takeIf { it.isNotBlank() }?.let { "$it · $indexStatus" } ?: indexStatus
}

@Composable
private fun SearchHistoryList(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "搜索历史",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearHistory) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("清空")
                }
            }
        }

        items(
            items = history,
            key = { it },
        ) { query ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHistoryClick(query) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchResultList(
    results: List<GlobalSearchResult>,
    query: String,
    expandedBookIds: Set<Long>,
    totalResults: Int,
    onToggleBookExpansion: (Long) -> Unit,
    onResultClick: (bookId: Long, chapterIndex: Int, byteOffset: Long) -> Unit,
) {
    val grouped = remember(results) {
        results.groupBy { it.bookId }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        grouped.forEach { (_, bookResults) ->
            val firstResult = bookResults.first()
            val expanded = firstResult.bookId in expandedBookIds
            val visibleResults = if (expanded) bookResults else bookResults.take(10)

            item(key = "header_${firstResult.bookId}") {
                BookResultHeader(
                    bookTitle = firstResult.bookTitle,
                    matchCount = bookResults.size,
                )
            }

            items(
                items = visibleResults,
                key = { "result_${it.bookId}_${it.chapterIndex}_${it.byteOffset}" },
            ) { result ->
                SearchResultItem(
                    result = result,
                    query = query,
                    onClick = { onResultClick(result.bookId, result.chapterIndex, result.byteOffset) },
                )
            }

            if (bookResults.size > 10) {
                item(key = "more_${firstResult.bookId}") {
                    TextButton(
                        onClick = { onToggleBookExpansion(firstResult.bookId) },
                        modifier = Modifier.padding(start = 48.dp, top = 2.dp, bottom = 8.dp),
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (expanded) "收起" else "查看本书全部 ${bookResults.size} 条")
                    }
                }
            }
        }

        if (totalResults >= 200) {
            item(key = "global_search_cap") {
                Text(
                    text = "已显示前 200 条结果",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun BookResultHeader(bookTitle: String, matchCount: Int) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = bookTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${matchCount}条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    result: GlobalSearchResult,
    query: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .padding(start = 40.dp),
    ) {
        Text(
            text = result.chapterTitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = highlightQuery(result.context, query),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
        )
    }
}

@Composable
private fun highlightQuery(context: String, query: String) = buildAnnotatedString {
    val lowerContext = context.lowercase()
    val lowerQuery = query.lowercase()
    var current = 0

    while (current < context.length) {
        val matchIndex = lowerContext.indexOf(lowerQuery, current)
        if (matchIndex == -1) {
            append(context.substring(current))
            break
        }
        append(context.substring(current, matchIndex))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
            append(context.substring(matchIndex, matchIndex + query.length))
        }
        current = matchIndex + query.length
    }
}

@Composable
private fun EmptySearchHint(
    query: String,
    coverage: SearchIndexCoverage,
    onStartBackfill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (query.isBlank()) "输入关键词搜索全部书籍正文" else "至少输入 2 个字符",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (coverage.totalBooks > 0 && coverage.indexedBooks == 0) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onStartBackfill) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("补全正文索引")
            }
        }
    }
}

@Composable
private fun ErrorHint(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message.ifBlank { "搜索失败" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun NoResultsHint(
    query: String,
    scopeBookCount: Int,
    indexedBookCountInScope: Int,
    onStartBackfill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noIndexedBookInScope = scopeBookCount > 0 && indexedBookCountInScope == 0

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (noIndexedBookInScope) {
                "当前范围还没有正文索引"
            } else {
                "未找到「$query」的匹配结果"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (noIndexedBookInScope) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onStartBackfill) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("补全正文索引")
            }
        }
    }
}
