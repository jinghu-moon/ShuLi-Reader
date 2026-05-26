package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.shuli.reader.core.i18n.LocalAppStrings
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyGridState

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
    var isDragging by remember { mutableStateOf(false) }
    var dragSourceId by remember { mutableStateOf<Long?>(null) }
    var hoverTargetId by remember { mutableStateOf<Long?>(null) }

    val reorderState = rememberReorderableLazyGridState(
        onMove = { from, to ->
            val mutable = books.toMutableList()
            val fromIndex = from.index
            val toIndex = to.index
            if (fromIndex in mutable.indices && toIndex in mutable.indices) {
                val item = mutable.removeAt(fromIndex)
                mutable.add(toIndex, item)
                onReorder(mutable)
            }
        },
        canDragOver = { from, to ->
            // 不允许拖拽到自身
            from.index != to.index
        },
        onDragEnd = { startIndex, endIndex ->
            isDragging = false
            dragSourceId = null
            hoverTargetId = null
            // 持久化排序到数据库
            onReorder(books)
        },
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = reorderState.gridState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(books, key = { it.id }) { node ->
            ReorderableItem(reorderState, key = node.id) { isDraggingNow ->
                val elevation = if (isDraggingNow) 8.dp else 0.dp
                val scale = if (isDraggingNow) 1.05f else 1f

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = elevation.toPx()
                        }
                        .zIndex(if (isDraggingNow) 1f else 0f)
                        // detectReorderAfterLongPress 必须在 combinedClickable 之前，拦截长按手势
                        .detectReorderAfterLongPress(reorderState)
                ) {
                    if (node is BookItem) {
                        BookGridItem(
                            book = node,
                            searchQuery = searchQuery,
                            isHighlighted = node.id == highlightedBookId,
                            onClick = {
                                if (isEditMode) onToggleSelection(node.id) else onBookClick(node.id)
                            },
                            onLongClick = { onLongPressToEdit(node.id) },
                            unifiedCoverPaletteIndex = unifiedCoverPaletteIndex,
                            isEditMode = isEditMode,
                            isSelected = selectedNodeIds.contains(node.id),
                        )
                    } else if (node is FolderItem) {
                        FolderGridItem(
                            folder = node,
                            isHighlighted = false,
                            onClick = { if (isEditMode) onToggleSelection(node.id) else onFolderClick(node.id) },
                            onLongClick = { onLongPressToEdit(node.id) },
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
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!isEditMode) onLongClick() },
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
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!isEditMode) onLongClick() },
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
