package com.shuli.reader.feature.bookshelf.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shuli.reader.feature.bookshelf.model.BookItem

@Composable
fun BookActionMenu(
    book: BookItem?,
    onDismiss: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onShowInfo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (book == null) return

    var showDeleteDialog by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = book != null,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = { Text(if (book.isFavorite) "取消收藏" else "收藏") },
            onClick = {
                onToggleFavorite(book.id)
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = if (book.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (book.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            },
        )
        DropdownMenuItem(
            text = { Text("书籍信息") },
            onClick = {
                onShowInfo(book.id)
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Outlined.Info, contentDescription = null)
            },
        )
        DropdownMenuItem(
            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
            onClick = {
                showDeleteDialog = true
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除书籍") },
            text = { Text("确定要删除《${book.title}》吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(book.id)
                        showDeleteDialog = false
                        onDismiss()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}
