package com.shuli.reader.feature.bookshelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditModeTopBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val strings = LocalAppStrings.current
    TopAppBar(
        title = {
            Text(
                if (selectedCount > 0) strings.selectedItemCount(selectedCount) else strings.selectItems,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = strings.cancelAction)
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = strings.selectAll)
            }
        }
    )
}

@Composable
fun EditModeBottomBar(
    selectedCount: Int,
    onGroupClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val strings = LocalAppStrings.current
    BottomAppBar(
        actions = {
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .clickable(enabled = selectedCount > 0) { onGroupClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Folder, contentDescription = strings.folderLabel, tint = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings.folderLabel, style = MaterialTheme.typography.labelSmall, color = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .clickable(enabled = selectedCount > 0) { onDeleteClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Delete, contentDescription = strings.deleteAction, tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings.deleteAction, style = MaterialTheme.typography.labelSmall, color = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .clickable(enabled = selectedCount > 0) { onMoreClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = strings.moreLabel, tint = if (selectedCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings.moreLabel, style = MaterialTheme.typography.labelSmall, color = if (selectedCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    )
}
