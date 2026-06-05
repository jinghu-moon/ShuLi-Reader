package com.shuli.reader.feature.bookshelf

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shuli.reader.feature.bookshelf.component.BookCompactList
import com.shuli.reader.feature.bookshelf.component.BookGrid
import com.shuli.reader.feature.bookshelf.component.BookList
import com.shuli.reader.feature.bookshelf.model.BookshelfNode
import com.shuli.reader.feature.bookshelf.model.ViewMode

/**
 * 书架内容区域：根据 ViewMode 切换 Grid/List/CompactList。
 */
@Composable
fun BookContent(
    books: List<BookshelfNode>,
    viewMode: ViewMode,
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
    onDragToSlot: (Long, Int) -> Unit = { _, _ -> },
    onMerge: (Long, Long) -> Unit = { _, _ -> },
) {
    when (viewMode) {
        ViewMode.GRID -> BookGrid(
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
            onDragToSlot = onDragToSlot,
            onMerge = onMerge,
        )
        ViewMode.LIST -> BookList(
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
            onDragToSlot = onDragToSlot,
            onMerge = onMerge,
        )
        ViewMode.COMPACT_LIST -> BookCompactList(
            books = books,
            searchQuery = searchQuery,
            listState = listState,
            highlightedBookId = highlightedBookId,
            onBookClick = onBookClick,
            onFolderClick = onFolderClick,
            onShowInfo = onShowInfo,
            modifier = modifier,
            isEditMode = isEditMode,
            selectedNodeIds = selectedNodeIds,
            onToggleSelection = onToggleSelection,
            onLongPressToEdit = onLongPressToEdit,
            onDragToSlot = onDragToSlot,
            onMerge = onMerge,
        )
    }
}
