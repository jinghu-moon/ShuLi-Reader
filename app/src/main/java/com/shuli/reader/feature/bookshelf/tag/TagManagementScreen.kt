package com.shuli.reader.feature.bookshelf.tag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.database.dao.TagWithCount
import com.shuli.reader.core.i18n.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(
    tags: List<TagWithCount>,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onMerge: (Long, Long) -> Unit,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var renameDialogTagId by remember { mutableStateOf<Long?>(null) }
    var deleteDialogTagId by remember { mutableStateOf<Long?>(null) }
    var mergeSourceId by remember { mutableStateOf<Long?>(null) }
    var mergeTargetId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(searchQuery) {
        onSearch(searchQuery)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${strings.bookshelf.tagManagement} · ${strings.bookshelf.tagTotalCount(tags.size)}",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(strings.bookshelf.searchTagHint) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tags, key = { it.id }) { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (mergeSourceId != null) {
                                    mergeTargetId = tag.id
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = strings.bookshelf.tagCountLabel(tag.usageCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        IconButton(onClick = { renameDialogTagId = tag.id }) {
                            Icon(Icons.Filled.Edit, contentDescription = strings.bookshelf.renameTag)
                        }
                        IconButton(onClick = { deleteDialogTagId = tag.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = strings.bookshelf.deleteTag)
                        }
                        IconButton(onClick = {
                            if (mergeSourceId == null) {
                                mergeSourceId = tag.id
                            } else if (mergeSourceId != tag.id) {
                                mergeTargetId = tag.id
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.MergeType,
                                contentDescription = strings.bookshelf.mergeTag,
                                tint = if (mergeSourceId == tag.id) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    renameDialogTagId?.let { tagId ->
        val tag = tags.firstOrNull { it.id == tagId }
        if (tag != null) {
            var newName by remember { mutableStateOf(tag.name) }
            AlertDialog(
                onDismissRequest = { renameDialogTagId = null },
                title = { Text(strings.bookshelf.renameTag) },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onRename(tagId, newName)
                        renameDialogTagId = null
                    }) {
                        Text(strings.common.confirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameDialogTagId = null }) {
                        Text(strings.common.cancel)
                    }
                },
            )
        }
    }

    deleteDialogTagId?.let { tagId ->
        val tag = tags.firstOrNull { it.id == tagId }
        if (tag != null) {
            AlertDialog(
                onDismissRequest = { deleteDialogTagId = null },
                title = { Text(strings.bookshelf.deleteTag) },
                text = { Text(strings.bookshelf.confirmDeleteTag(tag.name, tag.usageCount)) },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete(tagId)
                        deleteDialogTagId = null
                    }) {
                        Text(strings.common.confirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteDialogTagId = null }) {
                        Text(strings.common.cancel)
                    }
                },
            )
        }
    }

    if (mergeSourceId != null && mergeTargetId != null) {
        val source = tags.firstOrNull { it.id == mergeSourceId }
        val target = tags.firstOrNull { it.id == mergeTargetId }
        if (source != null && target != null) {
            AlertDialog(
                onDismissRequest = {
                    mergeSourceId = null
                    mergeTargetId = null
                },
                title = { Text(strings.bookshelf.mergeTag) },
                text = { Text(strings.bookshelf.confirmMergeTag(source.name, target.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        onMerge(mergeSourceId!!, mergeTargetId!!)
                        mergeSourceId = null
                        mergeTargetId = null
                    }) {
                        Text(strings.common.confirm)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        mergeSourceId = null
                        mergeTargetId = null
                    }) {
                        Text(strings.common.cancel)
                    }
                },
            )
        }
    }
}
