package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shuli.reader.feature.bookshelf.model.BookItem

@Composable
fun FolderCover(
    books: List<BookItem>,
    modifier: Modifier = Modifier
) {
    val displayBooks = books.take(4)
    
    Box(
        modifier = modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp)
    ) {
        if (displayBooks.isEmpty()) {
            // 空文件夹样式
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    MiniCover(book = displayBooks.getOrNull(0), modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(2.dp))
                    MiniCover(book = displayBooks.getOrNull(1), modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(modifier = Modifier.weight(1f)) {
                    MiniCover(book = displayBooks.getOrNull(2), modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(2.dp))
                    MiniCover(book = displayBooks.getOrNull(3), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniCover(book: BookItem?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (book != null) {
            if (book.coverUrl != null) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                DefaultBookCover(
                    title = book.title,
                    fileType = book.fileType,
                    modifier = Modifier.fillMaxSize(),
                    isMini = true,
                    paletteIndexOverride = book.customCoverPaletteIndex
                )
            }
        }
    }
}
