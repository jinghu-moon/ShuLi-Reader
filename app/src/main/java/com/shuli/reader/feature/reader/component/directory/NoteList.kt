package com.shuli.reader.feature.reader.component.directory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 目录弹窗 - 笔记列表。
 * 从 DirectoryDialog 拆出，独立演进笔记编辑/着色交互。
 */
@Composable
internal fun NoteList(
    notes: List<NoteEntity>,
    onNoteClick: (NoteEntity) -> Unit,
    onNoteDelete: (NoteEntity) -> Unit,
    onNoteEdit: (NoteEntity, String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var noteToEdit by remember { mutableStateOf<NoteEntity?>(null) }
    var editText by remember { mutableStateOf("") }
    var editColor by remember { mutableStateOf<String?>(null) }

    if (notes.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Note,
                    contentDescription = null,
                    tint = readerColors.textTertiary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.reader.noNotes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = readerColors.textSecondary,
                )
            }
        }
        return
    }

    val noteColors = listOf("#FFEB3B", "#FF9800", "#F44336", "#4CAF50", "#2196F3", "#9C27B0", null)

    LazyColumn(modifier = modifier.then(Modifier.heightIn(max = 400.dp))) {
        items(notes, key = { it.id }) { note ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNoteClick(note) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (note.color != null) {
                    Box(
                        modifier = Modifier
                            .size(4.dp, 36.dp)
                            .background(
                                color = runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(note.color)) }.getOrDefault(androidx.compose.ui.graphics.Color.Gray),
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.noteText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = readerColors.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = strings.reader.notePosition(note.byteStart.toInt(), note.byteEnd.toInt(), dateFormat.format(Date(note.createdTime))),
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.textTertiary,
                    )
                }
                IconButton(onClick = {
                    noteToEdit = note
                    editText = note.noteText
                    editColor = note.color
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = strings.reader.editValue,
                        tint = readerColors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { noteToDelete = note }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = strings.common.deleteIconDesc,
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

    // 编辑笔记对话框
    noteToEdit?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToEdit = null },
            title = { Text(strings.reader.editValue) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        maxLines = 5,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        noteColors.forEach { color ->
                            val isSelected = editColor == color
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        color = if (color != null) {
                                            runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(color)) }.getOrDefault(androidx.compose.ui.graphics.Color.Gray)
                                        } else {
                                            readerColors.textTertiary.copy(alpha = 0.3f)
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, readerColors.textPrimary, RoundedCornerShape(16.dp))
                                        else Modifier,
                                    )
                                    .clickable { editColor = color },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onNoteEdit(note, editText, editColor)
                        noteToEdit = null
                    },
                ) { Text(strings.reader.saveAction) }
            },
            dismissButton = {
                TextButton(onClick = { noteToEdit = null }) { Text(strings.reader.cancelAction) }
            },
        )
    }

    // 删除确认对话框
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text(strings.reader.deleteNoteTitle) },
            text = { Text(strings.reader.deleteNoteConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onNoteDelete(note)
                        noteToDelete = null
                    },
                ) { Text(strings.reader.deleteAction) }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text(strings.reader.cancelAction) }
            },
        )
    }
}
