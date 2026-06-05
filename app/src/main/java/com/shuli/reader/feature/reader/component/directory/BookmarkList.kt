package com.shuli.reader.feature.reader.component.directory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 目录书签列表。
 */
@Composable
fun BookmarkList(
    bookmarks: List<BookmarkEntity>,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onBookmarkDelete: (BookmarkEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var bookmarkToDelete by remember { mutableStateOf<BookmarkEntity?>(null) }

    if (bookmarks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Bookmark,
                    contentDescription = null,
                    tint = readerColors.textTertiary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.noBookmarks,
                    style = MaterialTheme.typography.bodyMedium,
                    color = readerColors.textSecondary,
                )
            }
        }
        return
    }

    LazyColumn(modifier = modifier.then(Modifier.heightIn(max = 400.dp))) {
        items(bookmarks, key = { it.id }) { bookmark ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBookmarkClick(bookmark) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (!bookmark.chapterTitle.isNullOrBlank()) {
                        Text(
                            text = bookmark.chapterTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = readerColors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = bookmark.selectedText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = readerColors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(Date(bookmark.createdTime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.textTertiary,
                    )
                }
                IconButton(onClick = { bookmarkToDelete = bookmark }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = strings.deleteIconDesc,
                        tint = readerColors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = readerColors.divider,
            )
        }
    }

    // 删除确认对话框
    bookmarkToDelete?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { bookmarkToDelete = null },
            title = { Text(strings.deleteBookmarkTitle) },
            text = { Text(strings.deleteBookmarkConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBookmarkDelete(bookmark)
                        bookmarkToDelete = null
                    },
                ) { Text(strings.deleteAction) }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkToDelete = null }) { Text(strings.cancelAction) }
            },
        )
    }
}
