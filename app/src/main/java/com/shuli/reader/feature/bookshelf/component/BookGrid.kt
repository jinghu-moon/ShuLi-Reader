package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.shuli.reader.core.i18n.LocalAppStrings
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shuli.reader.feature.bookshelf.model.BookItem

@Composable
fun BookGrid(
    books: List<BookItem>,
    searchQuery: String,
    gridState: LazyGridState,
    highlightedBookId: Long?,
    onBookClick: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onShowInfo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    unifiedCoverPaletteIndex: Int? = null,
    onCustomizeCover: ((Long) -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(books, key = { it.id }) { book ->
            BookGridItem(
                book = book,
                searchQuery = searchQuery,
                isHighlighted = book.id == highlightedBookId,
                onToggleFavorite = onToggleFavorite,
                onDelete = onDelete,
                onShowInfo = onShowInfo,
                onClick = { onBookClick(book.id) },
                unifiedCoverPaletteIndex = unifiedCoverPaletteIndex,
                onCustomizeCover = onCustomizeCover,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridItem(
    book: BookItem,
    searchQuery: String,
    isHighlighted: Boolean,
    onToggleFavorite: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onShowInfo: (Long) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unifiedCoverPaletteIndex: Int? = null,
    onCustomizeCover: ((Long) -> Unit)? = null,
) {
    val strings = LocalAppStrings.current
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { expanded = true },
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (book.readingProgress > 0f)
                    "${(book.readingProgress * 100).toInt()}%" else strings.unreadLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (book.readingDuration.isNotBlank()) {
                Text(
                    text = book.readingDuration,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
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
        
        BookActionMenu(
            book = if (expanded) book else null,
            onDismiss = { expanded = false },
            onToggleFavorite = onToggleFavorite,
            onDelete = onDelete,
            onShowInfo = onShowInfo,
            onCustomizeCover = onCustomizeCover,
        )
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
