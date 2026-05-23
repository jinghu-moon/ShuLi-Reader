package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shuli.reader.feature.bookshelf.model.BookItem

@Composable
fun BookList(
    books: List<BookItem>,
    searchQuery: String,
    listState: LazyListState,
    highlightedBookId: Long?,
    onBookClick: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onShowInfo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        items(books, key = { it.id }) { book ->
            BookListItem(
                book = book,
                searchQuery = searchQuery,
                isHighlighted = book.id == highlightedBookId,
                onToggleFavorite = onToggleFavorite,
                onDelete = onDelete,
                onShowInfo = onShowInfo,
                onClick = { onBookClick(book.id) },
            )
            if (book != books.last()) {
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
    onToggleFavorite: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onShowInfo: (Long) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { expanded = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (book.coverUrl != null) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = null,
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
                isSmall = true
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
                        "已读 ${(book.readingProgress * 100).toInt()}%" else "未开始",
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
                contentDescription = "已收藏",
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
        
        BookActionMenu(
            book = if (expanded) book else null,
            onDismiss = { expanded = false },
            onToggleFavorite = onToggleFavorite,
            onDelete = onDelete,
            onShowInfo = onShowInfo,
        )
    }
}
