package com.shuli.reader.feature.bookshelf.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reading.ReadingStatus
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.BookshelfNode
import com.shuli.reader.feature.bookshelf.model.FileType
import com.shuli.reader.feature.bookshelf.model.FolderItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCompactList(
    books: List<BookshelfNode>,
    searchQuery: String,
    listState: LazyListState,
    highlightedBookId: Long?,
    onBookClick: (Long) -> Unit,
    onShowInfo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    selectedNodeIds: Set<Long> = emptySet(),
    onToggleSelection: (Long) -> Unit = {},
    onLongPressToEdit: (Long) -> Unit = {},
    onDragToSlot: (Long, Int) -> Unit = { _, _ -> },
    onMerge: (Long, Long) -> Unit = { _, _ -> },
    onFolderClick: (Long) -> Unit = {},
) {
    // 本地副本数据结构，确保拖拽流畅
    val localBooks = remember(books) { books.toMutableStateList() }
    var isDragging by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    LaunchedEffect(books) {
        if (!isDragging) {
            localBooks.clear()
            localBooks.addAll(books)
        }
    }

    // 追踪拖拽状态
    var draggingNodeKey by remember { mutableStateOf<Any?>(null) }
    var hasDraggedSwap by remember { mutableStateOf(false) }
    var lastSwapTime by remember { mutableLongStateOf(0L) }
    var lastSwapTarget by remember { mutableStateOf<Any?>(null) }
    var isLongPressActive by remember { mutableStateOf(false) }
    var suppressClickUntilMillis by remember { mutableLongStateOf(0L) }

    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = from.index
        val toIndex = to.index
        if (fromIndex in localBooks.indices && toIndex in localBooks.indices) {
            val item = localBooks.removeAt(fromIndex)
            localBooks.add(toIndex, item)
            hasDraggedSwap = true
            lastSwapTime = System.currentTimeMillis()
            lastSwapTarget = to.key
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
                        .animateItem(
                            fadeInSpec = androidx.compose.animation.core.tween(300),
                            fadeOutSpec = androidx.compose.animation.core.tween(300),
                            placementSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                            ),
                        )
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
                                isLongPressActive = true
                            },
                            onDragStopped = {
                                suppressClickUntilMillis = nextPostLongPressClickDeadline()
                                draggingNodeKey = null
                                lastSwapTarget = null
                                if (!hasDraggedSwap) {
                                    isDragging = false
                                    onLongPressToEdit(node.id)
                                } else {
                                    val targetSlot = localBooks.indexOfFirst { it.id == node.id }
                                    onDragToSlot(node.id, targetSlot)
                                    scope.launch {
                                        delay(500)
                                        isDragging = false
                                    }
                                }
                                isLongPressActive = false
                            }
                        )
                ) {
                    if (node is BookItem) {
                        BookCompactListItem(
                            book = node,
                            searchQuery = searchQuery,
                            isHighlighted = node.id == highlightedBookId,
                            onClick = {
                                if (shouldSuppressPostLongPressClick(isLongPressActive, suppressClickUntilMillis)) return@BookCompactListItem
                                if (isEditMode) onToggleSelection(node.id) else onBookClick(node.id)
                            },
                            onLongClick = { onToggleSelection(node.id) },
                            isEditMode = isEditMode,
                            isSelected = selectedNodeIds.contains(node.id),
                        )
                    } else if (node is FolderItem) {
                        FolderCompactListItem(
                            folder = node,
                            searchQuery = searchQuery,
                            isHighlighted = false,
                            onClick = {
                                if (shouldSuppressPostLongPressClick(isLongPressActive, suppressClickUntilMillis)) return@FolderCompactListItem
                                if (isEditMode) onToggleSelection(node.id) else onFolderClick(node.id)
                            },
                            onLongClick = { onToggleSelection(node.id) },
                            isEditMode = isEditMode,
                            isSelected = selectedNodeIds.contains(node.id),
                        )
                    }
                }
            }
            if (node != localBooks.last()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCompactListItem(
    book: BookItem,
    searchQuery: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
) {
    val strings = LocalAppStrings.current

    BoxWithConstraints(modifier = modifier) {
        val titleMaxWidth = maxWidth * 0.65f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(
                    if (isEditMode) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier.clickable(onClick = onClick)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧阅读状态小圆点
            val dotColor = readingStatusColor(book.readingStatus)

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape)
            )

            if (book.readCount > 1) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${book.readCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(16.dp))

            // 中间标题容器
            Box(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = getHighlightedText(text = book.title, highlight = searchQuery),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = titleMaxWidth)
                )
            }

            Spacer(Modifier.width(12.dp))

            // 右侧信息：进度与格式新设计（弱化/精简）
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isTxt = book.fileType == FileType.TXT
                val hasProgress = book.readingProgress > 0f
                val isFinished = book.readingProgress >= 1.0f

                // 1. 进度显示逻辑
                if (hasProgress) {
                    val progressText = if (isFinished) {
                        "100%"
                    } else {
                        String.format(java.util.Locale.US, "%.1f%%", book.readingProgress * 100)
                    }
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // 2. 格式胶囊显示逻辑
                if (!isTxt) {
                    if (hasProgress) {
                        Spacer(Modifier.width(8.dp))
                    }
                    FormatCapsule(format = book.fileType.name)
                }
            }

            // 编辑状态下的 Checkbox
            if (isEditMode) {
                Spacer(Modifier.width(16.dp))
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null
                )
            }
        }

        // 高亮选中效果
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCompactListItem(
    folder: FolderItem,
    searchQuery: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
) {
    val strings = LocalAppStrings.current

    BoxWithConstraints(modifier = modifier) {
        val titleMaxWidth = maxWidth * 0.65f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(
                    if (isEditMode) {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier.clickable(onClick = onClick)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧文件夹图标
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(12.dp))

            // 中间文件夹名容器
            Box(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = getHighlightedText(text = folder.title, highlight = searchQuery),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = titleMaxWidth)
                )
            }

            Spacer(Modifier.width(12.dp))

            // 右侧信息：本数与箭头
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${folder.books.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            // 编辑状态下的 Checkbox
            if (isEditMode) {
                Spacer(Modifier.width(16.dp))
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null
                )
            }
        }

        // 高亮选中效果
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
    }
}

@Composable
private fun getHighlightedText(text: String, highlight: String): AnnotatedString {
    if (highlight.isBlank()) return AnnotatedString(text)
    val index = text.indexOf(highlight, ignoreCase = true)
    if (index == -1) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text.substring(0, index))
        withStyle(style = SpanStyle(background = Color.Yellow, color = Color.Black)) {
            append(text.substring(index, index + highlight.length))
        }
        append(text.substring(index + highlight.length))
    }
}

@Composable
private fun FormatCapsule(
    format: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = format,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
    }
}

