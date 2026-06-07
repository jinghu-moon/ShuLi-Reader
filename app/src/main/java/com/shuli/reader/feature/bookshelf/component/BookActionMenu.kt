package com.shuli.reader.feature.bookshelf.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reading.ReadingStatus
import com.shuli.reader.feature.bookshelf.model.BookItem

@Composable
fun BookActionMenu(
    book: BookItem?,
    onDismiss: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onShowInfo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onCustomizeCover: ((Long) -> Unit)? = null,
    onStatusChange: ((Long, ReadingStatus) -> Unit)? = null,
) {
    if (book == null) return

    val strings = LocalAppStrings.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStatusSubmenu by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = { Text(if (book.isFavorite) strings.bookshelf.removeFavorite else strings.bookshelf.addFavorite) },
            onClick = {
                onToggleFavorite(book.id)
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = if (book.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = strings.common.favoriteIconDesc,
                    tint = if (book.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            },
        )
        DropdownMenuItem(
            text = { Text(strings.bookshelf.bookInfo) },
            onClick = {
                onShowInfo(book.id)
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Outlined.Info, contentDescription = strings.common.infoIconDesc)
            },
        )
        if (onCustomizeCover != null) {
            DropdownMenuItem(
                text = { Text(strings.reader.customizeCover) },
                onClick = {
                    onCustomizeCover(book.id)
                    onDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Palette, contentDescription = strings.reader.customizeCover)
                },
            )
        }
        if (onStatusChange != null) {
            HorizontalDivider()
            ReadingStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = readingStatusLabel(status),
                            color = if (status == book.readingStatus) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onStatusChange(book.id, status)
                        onDismiss()
                    },
                )
            }
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(strings.bookshelf.deleteBook, color = MaterialTheme.colorScheme.error) },
            onClick = {
                showDeleteDialog = true
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = strings.common.deleteIconDesc,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(strings.bookshelf.deleteBookTitle) },
            text = { Text(strings.bookshelf.deleteBookConfirm(book.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(book.id)
                        showDeleteDialog = false
                        onDismiss()
                    },
                ) {
                    Text(strings.bookshelf.deleteBook, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(strings.common.cancel)
                }
            },
        )
    }
}

