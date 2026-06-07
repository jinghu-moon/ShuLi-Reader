package com.shuli.reader.feature.bookshelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.bookshelf.component.BookCompactList
import com.shuli.reader.feature.bookshelf.component.BookGrid
import com.shuli.reader.feature.bookshelf.component.BookList
import com.shuli.reader.feature.bookshelf.model.BookshelfNode
import com.shuli.reader.feature.bookshelf.model.ViewMode

/**
 * 书架内容区域：根据 ViewMode 切换 Grid/List/CompactList。
 * 从 BookshelfScreen 拆出。
 */
@Composable
internal fun BookContent(
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

/**
 * 空书架状态。
 * 从 BookshelfScreen 拆出。
 */
@Composable
internal fun EmptyState(
    isSearching: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isSearching) strings.common.noBooksFound else strings.common.emptyBookshelf,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 搜索引导状态。
 * 从 BookshelfScreen 拆出。
 */
@Composable
internal fun SearchGuideState(modifier: Modifier = Modifier) {
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
            text = strings.common.searchIconDesc,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = strings.common.searchPlaceholder,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}
