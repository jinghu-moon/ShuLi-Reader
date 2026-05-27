package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.shuli.reader.core.i18n.LocalAppStrings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shuli.reader.feature.bookshelf.model.BookshelfNode
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.FolderItem
import androidx.compose.material.icons.filled.Folder
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.runtime.toMutableStateList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookList(
    books: List<BookshelfNode>,
    searchQuery: String,
    listState: LazyListState,
    highlightedBookId: Long?,
    onBookClick: (Long) -> Unit,
    onShowInfo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    unifiedCoverPaletteIndex: Int? = null,
    onCustomizeCover: ((Long) -> Unit)? = null,
    isEditMode: Boolean = false,
    selectedNodeIds: Set<Long> = emptySet(),
    onToggleSelection: (Long) -> Unit = {},
    onLongPressToEdit: (Long) -> Unit = {},
    onDragToSlot: (Long, Int) -> Unit = { _, _ -> },
    onMerge: (Long, Long) -> Unit = { _, _ -> },
    onFolderClick: (Long) -> Unit = {},
) {
    // 拖拽进行中标记，阻断 LaunchedEffect 同步（必须在 LaunchedEffect 之前声明）
    var isDragging by remember { mutableStateOf(false) }
    // 本地可变列表：引用始终不变，通过 clear+addAll 原地更新
    // 避免 remember(books) 重建列表导致拖拽中 onMove 闭包捕获过期引用
    val localBooks = remember { books.toMutableStateList() }
    // 仅在非拖拽时从 ViewModel 同步最新数据
    val scope = rememberCoroutineScope()
    LaunchedEffect(books) {
        if (!isDragging) {
            localBooks.clear()
            localBooks.addAll(books)
        }
    }

    // 追踪当前被拖拽的节点 key
    var draggingNodeKey by remember { mutableStateOf<Any?>(null) }
    // 标记拖拽过程中是否发生了实际的位置交换（swap），用于区分"长按松手"和"拖拽排序"
    var hasDraggedSwap by remember { mutableStateOf(false) }
    var lastSwapTime by remember { mutableStateOf(0L) }
    var lastSwapTarget by remember { mutableStateOf<Any?>(null) }

    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = from.index
        val toIndex = to.index
        if (fromIndex in localBooks.indices && toIndex in localBooks.indices) {
            // 直接操作本地可变列表，Compose 立即感知变化 → 拖拽跟手
            val item = localBooks.removeAt(fromIndex)
            localBooks.add(toIndex, item)
            hasDraggedSwap = true
            // 记录 swap 事件用于悬停检测
            lastSwapTime = System.currentTimeMillis()
            lastSwapTarget = to.key
        }
    }

    // 拖拽悬停触发合并：拖拽过程中不触发，避免排序时误合并
    LaunchedEffect(draggingNodeKey, lastSwapTarget, lastSwapTime) {
        val draggedKey = draggingNodeKey
        val targetKey = lastSwapTarget
        if (draggedKey != null && targetKey != null && draggedKey != targetKey) {
            // 拖拽排序过程中不触发合并，避免意外合并
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        items(localBooks, key = { it.id }) { node ->
            ReorderableItem(reorderableLazyListState, key = node.id) { isDraggingNow ->
                if (isDraggingNow) draggingNodeKey = node.id
                val scale by animateFloatAsState(if (isDraggingNow) 1.02f else 1f)
                val elevation by animateDpAsState(if (isDraggingNow) 4.dp else 0.dp)

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = elevation.toPx()
                        }
                        .longPressDraggableHandle(
                            enabled = !isEditMode,
                            onDragStarted = {
                                isDragging = true
                                hasDraggedSwap = false
                            },
                            onDragStopped = {
                                draggingNodeKey = null
                                lastSwapTarget = null
                                if (!hasDraggedSwap) {
                                    // 长按松手（未产生 swap）→ 进入编辑模式
                                    isDragging = false
                                    onLongPressToEdit(node.id)
                                } else {
                                    // 实际拖拽 → 计算目标槽位，O(1) 写入
                                    val targetSlot = localBooks.indexOfFirst { it.id == node.id }
                                    onDragToSlot(node.id, targetSlot)
                                    scope.launch {
                                        delay(500)
                                        isDragging = false
                                    }
                                }
                            }
                        )
                ) {
                    if (node is BookItem) {
                        BookListItem(
                            book = node,
                            searchQuery = searchQuery,
                            isHighlighted = node.id == highlightedBookId,
                            onClick = {
                                if (isEditMode) onToggleSelection(node.id) else onBookClick(node.id)
                            },
                            onLongClick = { onToggleSelection(node.id) },
                            unifiedCoverPaletteIndex = unifiedCoverPaletteIndex,
                            isEditMode = isEditMode,
                            isSelected = selectedNodeIds.contains(node.id),
                        )
                    } else if (node is FolderItem) {
                        FolderListItem(
                            folder = node,
                            isHighlighted = false,
                            onClick = { if (isEditMode) onToggleSelection(node.id) else onFolderClick(node.id) },
                            onLongClick = { onToggleSelection(node.id) },
                            isEditMode = isEditMode,
                            isSelected = selectedNodeIds.contains(node.id),
                        )
                    }
                }
            }
            if (node != books.last()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListItem(
    book: BookItem,
    searchQuery: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    unifiedCoverPaletteIndex: Int? = null,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
) {
    val strings = LocalAppStrings.current

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isEditMode) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    } else {
                        // 正常模式：只用 clickable（不识别长按），避免与外层 longPressDraggableHandle 冲突
                        Modifier.clickable(onClick = onClick)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (book.coverUrl != null) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = strings.coverImageDesc,
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
        } else {
            DefaultBookCover(
                title = book.title,
                fileType = book.fileType,
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp),
                isSmall = true,
                isFavorite = book.isFavorite,
                paletteIndexOverride = unifiedCoverPaletteIndex ?: book.customCoverPaletteIndex,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = getHighlightedText(text = book.title, highlight = searchQuery),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    book.author?.let { append(it); append(" · ") }
                    append(book.fileSize)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (book.readingProgress > 0f)
                        strings.readProgress((book.readingProgress * 100).toInt()) else strings.notStartedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (book.readingDuration.isNotBlank()) {
                    Text(
                        text = " · ${book.readingDuration}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        if (book.isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = strings.favoritedDesc,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        }
        
        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
        
        if (isEditMode) {
            androidx.compose.material3.Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderListItem(
    folder: FolderItem,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isEditMode) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    } else {
                        // 正常模式：只用 clickable，避免与外层 longPressDraggableHandle 冲突
                        Modifier.clickable(onClick = onClick)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp, 64.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (folder.books.isNotEmpty()) {
                    val firstBook = folder.books.first()
                    if (firstBook.coverUrl != null) {
                        AsyncImage(
                            model = firstBook.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        DefaultBookCover(
                            title = firstBook.title,
                            fileType = firstBook.fileType,
                            modifier = Modifier.fillMaxSize(),
                            isSmall = true,
                            isFavorite = firstBook.isFavorite,
                            paletteIndexOverride = firstBook.customCoverPaletteIndex,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${folder.books.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }

        if (isEditMode) {
            androidx.compose.material3.Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )
        }
    }
}
