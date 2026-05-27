package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.shuli.reader.core.i18n.LocalAppStrings
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shuli.reader.feature.bookshelf.model.BookshelfNode
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.FolderItem
import androidx.compose.material.icons.filled.Folder
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.runtime.toMutableStateList

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGrid(
    books: List<BookshelfNode>,
    searchQuery: String,
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
    onReorder: (List<BookshelfNode>) -> Unit = {},
    onMerge: (Long, Long) -> Unit = { _, _ -> },
    onFolderClick: (Long) -> Unit = {},
) {
    val lazyGridState = rememberLazyGridState()

    // 拖拽期间使用的本地列表副本（同步更新，保证跟手）
    val localBooks = remember(books) { books.toMutableStateList() }

    // 追踪当前被拖拽的节点 key（isItemDragging 是 internal，需自行维护）
    var draggingNodeKey by remember { mutableStateOf<Any?>(null) }
    // 标记拖拽过程中是否发生了实际的位置交换（swap），用于区分"长按松手"和"拖拽排序"
    var hasDraggedSwap by remember { mutableStateOf(false) }
    // 最近一次 swap 的时间戳，用于推断悬停
    var lastSwapTime by remember { mutableStateOf(0L) }
    var lastSwapTarget by remember { mutableStateOf<Any?>(null) }

    val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
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

    // 拖拽悬停触发合并：仅在拖拽已结束后检测（拖拽过程中不触发，避免排序时误合并）
    // 注意：当前实现中拖拽期间 draggingNodeKey != null，所以悬停合并暂时不会触发
    // 未来如需拖拽悬停合并，可在此增加更长的阈值和视觉反馈
    LaunchedEffect(draggingNodeKey, lastSwapTarget, lastSwapTime) {
        val draggedKey = draggingNodeKey
        val targetKey = lastSwapTarget
        if (draggedKey != null && targetKey != null && draggedKey != targetKey) {
            // 拖拽排序过程中不触发合并，避免意外合并
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = lazyGridState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(localBooks, key = { it.id }) { node ->
            ReorderableItem(reorderableLazyGridState, key = node.id) { isDraggingNow ->
                // 同步被拖拽节点 key
                if (isDraggingNow) draggingNodeKey = node.id
                val elevation by animateDpAsState(if (isDraggingNow) 8.dp else 0.dp)
                val scale by animateFloatAsState(if (isDraggingNow) 1.05f else 1f)

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = elevation.toPx()
                        }
                        .longPressDraggableHandle(
                            enabled = !isEditMode,
                            onDragStarted = { hasDraggedSwap = false },
                            onDragStopped = {
                                if (!hasDraggedSwap) {
                                    // 长按松手（未产生 swap）→ 进入编辑模式
                                    onLongPressToEdit(node.id)
                                } else {
                                    // 实际拖拽 → 提交最终排序到 ViewModel
                                    onReorder(localBooks.toList())
                                }
                                draggingNodeKey = null
                                lastSwapTarget = null
                            }
                        )
                ) {
                    if (node is BookItem) {
                        BookGridItem(
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
                        FolderGridItem(
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
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridItem(
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
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
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
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Box {
            if (book.coverUrl != null) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = strings.coverImageDesc,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
            } else {
                DefaultBookCover(
                    title = book.title,
                    fileType = book.fileType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f),
                    readingProgress = book.readingProgress,
                    paletteIndexOverride = unifiedCoverPaletteIndex ?: book.customCoverPaletteIndex,
                )
            }


            if (book.readingProgress > 0f) {
                LinearProgressIndicator(
                    progress = { book.readingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)),
                    trackColor = Color.Transparent,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = getHighlightedText(text = book.title, highlight = searchQuery),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (book.readingDuration.isNotBlank()) {
            Text(
                text = book.readingDuration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        }
        
        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
        
        if (isEditMode) {
            androidx.compose.material3.Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGridItem(
    folder: FolderItem,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
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
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FolderCover(
                books = folder.books,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f),
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "${folder.title} (${folder.books.size})",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }

        if (isEditMode) {
            androidx.compose.material3.Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun getHighlightedText(
    text: String,
    highlight: String,
    highlightColor: Color = MaterialTheme.colorScheme.primary
): AnnotatedString {
    return remember(text, highlight, highlightColor) {
        buildAnnotatedString {
            if (highlight.isBlank()) {
                append(text)
            } else {
                var startIdx = 0
                val lowerText = text.lowercase()
                val lowerHighlight = highlight.lowercase()

                while (true) {
                    val index = lowerText.indexOf(lowerHighlight, startIdx)
                    if (index == -1) {
                        append(text.substring(startIdx))
                        break
                    }

                    append(text.substring(startIdx, index))

                    withStyle(style = SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                        append(text.substring(index, index + highlight.length))
                    }

                    startIdx = index + highlight.length
                }
            }
        }
    }
}
