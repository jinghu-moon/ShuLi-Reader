package com.shuli.reader.feature.bookshelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.bookshelf.component.DefaultBookCover
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.FileType
import com.shuli.reader.feature.bookshelf.model.FolderItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsBottomSheet(
    booksCount: Int,
    todayReadingTime: String,
    todayReadingMinutes: Long,
    dailyTargetMinutes: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val progress = remember(todayReadingMinutes, dailyTargetMinutes) {
        if (dailyTargetMinutes > 0) {
            (todayReadingMinutes.toFloat() / dailyTargetMinutes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = strings.statsTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            Spacer(Modifier.height(16.dp))

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 8.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = todayReadingTime,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = strings.todayReadingProgress,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(strings.totalBooksCount, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("$booksCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(strings.totalReadingTime, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(todayReadingTime, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPickerDialog(
    folders: List<FolderItem>,
    onMoveToFolder: (Long) -> Unit,
    onCreateNewFolder: (String) -> Unit,
    onRemoveFromFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var newFolderName by remember { mutableStateOf("") }
    var showNewFolderInput by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分组") },
        text = {
            Column {
                if (folders.isNotEmpty()) {
                    Text("移动到现有分组：", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    folders.forEach { folder ->
                        ListItem(
                            headlineContent = { Text(folder.title) },
                            supportingContent = { Text("${folder.books.size}") },
                            leadingContent = {
                                Icon(Icons.Default.Folder, contentDescription = null)
                            },
                            modifier = Modifier.clickable { onMoveToFolder(folder.id) },
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                if (showNewFolderInput) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("新分组名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                onCreateNewFolder(newFolderName.trim())
                            }
                        },
                        enabled = newFolderName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("创建并移动")
                    }
                } else {
                    TextButton(
                        onClick = { showNewFolderInput = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("创建新分组")
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                TextButton(
                    onClick = onRemoveFromFolder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("从分组中移出")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreActionsSheet(
    onSelectAll: () -> Unit,
    onMoveOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            ListItem(
                headlineContent = { Text(strings.selectAll) },
                leadingContent = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                modifier = Modifier.clickable { onSelectAll() },
            )
            ListItem(
                headlineContent = { Text("从分组中移出") },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                modifier = Modifier.clickable { onMoveOut() },
            )
        }
    }
}

@Composable
fun FolderDetailSheet(
    folder: FolderItem,
    onBookClick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val configuration = LocalConfiguration.current
    val dialogHeight = (configuration.screenHeightDp * 0.75).dp
    val dialogWidth = (configuration.screenWidthDp * 0.90).dp

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(dialogWidth)
                .height(dialogHeight),
        ) {
            Column {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = folder.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${folder.books.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()

                if (folder.books.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "分组内暂无书籍",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(folder.books, key = { it.id }) { book ->
                            FolderGridCover(
                                book = book,
                                onClick = { onBookClick(book.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderGridCover(
    book: BookItem,
    onClick: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
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
                    readingProgress = book.readingProgress,
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

        Spacer(Modifier.height(4.dp))

        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
