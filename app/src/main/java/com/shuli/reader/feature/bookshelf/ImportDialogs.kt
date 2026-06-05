package com.shuli.reader.feature.bookshelf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings

fun getFilesFromTree(context: Context, treeUri: Uri): List<Pair<Uri, String>> {
    val files = mutableListOf<Pair<Uri, String>>()
    try {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childQueryUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        context.contentResolver.query(childQueryUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIndex)
                val displayName = cursor.getString(nameIndex) ?: ""
                val mimeType = cursor.getString(mimeIndex) ?: ""

                val isText = displayName.endsWith(".txt", ignoreCase = true) || mimeType == "text/plain"
                val isEpub = displayName.endsWith(".epub", ignoreCase = true) || mimeType == "application/epub+zip"

                if (isText || isEpub) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    files.add(fileUri to displayName)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return files
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportOptionBottomSheet(
    onImportFile: () -> Unit,
    onImportFolder: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = strings.bookshelf.libraryImportSettings,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onImportFile()
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(strings.bookshelf.importCopy, style = MaterialTheme.typography.bodyLarge)
                    Text(strings.bookshelf.importCopyDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onImportFolder()
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(strings.bookshelf.libraryImportSettings + " (Dir)", style = MaterialTheme.typography.bodyLarge)
                    Text(strings.bookshelf.folderImportDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun FolderImportDialog(
    files: List<Pair<Uri, String>>,
    onConfirm: (List<Uri>) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val selectedStates = remember(files) {
        mutableStateMapOf<Uri, Boolean>().apply {
            files.forEach { this[it.first] = true }
        }
    }

    val selectedCount = selectedStates.values.count { it }
    val isAllSelected = selectedCount == files.size

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.bookshelf.libraryImportSettings,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    androidx.compose.material3.TextButton(
                        onClick = {
                            val target = !isAllSelected
                            files.forEach { selectedStates[it.first] = target }
                        }
                    ) {
                        Text(if (isAllSelected) strings.bookshelf.deselectAll else strings.bookshelf.selectAll)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 350.dp)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(files) { (uri, name) ->
                            val isSelected = selectedStates[uri] ?: false
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedStates[uri] = !isSelected }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (name.endsWith(".epub", ignoreCase = true))
                                        Icons.Default.Book else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                androidx.compose.material3.Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { selectedStates[uri] = it }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text(strings.common.backIconDesc)
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            val selectedUris = files.map { it.first }.filter { selectedStates[it] == true }
                            onConfirm(selectedUris)
                            onDismiss()
                        },
                        enabled = selectedCount > 0
                    ) {
                        Text(strings.bookshelf.importSelected(selectedCount))
                    }
                }
            }
        }
    }
}
