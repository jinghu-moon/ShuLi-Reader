package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.database.dao.TagWithCount
import com.shuli.reader.core.database.entity.TagEntity
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reading.ReadingStatus
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.FileType

data class BookDetailsActions(
    val onStatusChange: (ReadingStatus) -> Unit,
    val onExportNotes: () -> Unit,
    val onDeleteBook: () -> Unit,
    val onContinueReading: (() -> Unit)? = null,
)

data class BookDetailsTagState(
    val tags: List<TagEntity>,
    val suggestions: List<TagWithCount>,
)

data class BookDetailsTagActions(
    val onTagAdd: (String) -> Unit,
    val onTagRemove: (Long) -> Unit,
    val onTagClick: (String) -> Unit,
    val onSearchTags: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsSheet(
    book: BookItem,
    actions: BookDetailsActions,
    tagState: BookDetailsTagState,
    tagActions: BookDetailsTagActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState()
    var showStatusMenu by remember { mutableStateOf(false) }
    var showTagInput by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DefaultBookCover(
                    title = book.title,
                    fileType = book.fileType,
                    modifier = Modifier.size(width = 80.dp, height = 120.dp),
                    isFavorite = book.isFavorite,
                    readingProgress = book.readingProgress,
                    paletteIndexOverride = book.customCoverPaletteIndex,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Serif,
                    )
                    Text(
                        text = book.author ?: strings.bookshelf.unknownAuthor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box {
                        ReadingStatusBadge(
                            status = book.readingStatus,
                            readCount = book.readCount,
                            onClick = { showStatusMenu = true },
                        )
                        if (showStatusMenu) {
                            StatusDropdownMenu(
                                currentStatus = book.readingStatus,
                                onStatusSelected = { status ->
                                    actions.onStatusChange(status)
                                    showStatusMenu = false
                                },
                                onDismiss = { showStatusMenu = false },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TagFlow(
                        tags = tagState.tags,
                        onTagClick = { tag -> tagActions.onTagClick(tag.name) },
                        onAddClick = { showTagInput = !showTagInput },
                        onRemoveClick = { tagId -> tagActions.onTagRemove(tagId) },
                    )
                }
            }

            if (showTagInput) {
                Spacer(Modifier.height(8.dp))
                TagInputField(
                    suggestions = tagState.suggestions,
                    onTagSubmit = { name ->
                        tagActions.onTagAdd(name)
                        showTagInput = false
                    },
                    onSuggestionClick = { suggestion ->
                        tagActions.onTagAdd(suggestion.name)
                        showTagInput = false
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            actions.onContinueReading?.let { onContinue ->
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = when (book.readingStatus) {
                            ReadingStatus.WANT_TO_READ -> strings.bookshelf.startReading
                            ReadingStatus.FINISHED -> strings.bookshelf.rereadBook
                            ReadingStatus.ABANDONED -> strings.bookshelf.restartBook
                            else -> strings.bookshelf.continueReading
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // P2: 阅读数据网格
            ReadingDataGrid(
                totalDuration = book.readingDuration,
                readingDays = 0,
                readingProgress = book.readingProgress,
            )

            Spacer(Modifier.height(4.dp))

            // P2: 书籍信息
            InfoItem(label = strings.bookshelf.bookFormatLabel, value = if (book.fileType == FileType.TXT) "TXT" else "EPUB")
            InfoItem(label = strings.bookshelf.bookSizeLabel, value = book.fileSize)
            InfoItem(label = strings.bookshelf.bookProgressLabel, value = "${(book.readingProgress * 100).toInt()}%")
            InfoItem(label = strings.bookshelf.readingDurationLabel, value = book.readingDuration.ifEmpty { strings.bookshelf.notReadYet })
            InfoItem(label = strings.bookshelf.filePathLabel, value = book.filePath)

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ReadingStatusBadge(
    status: ReadingStatus,
    readCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val statusColor = readingStatusColor(status)
    val statusLabel = readingStatusLabel(status)

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(statusColor, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
        )
        if (readCount > 1) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = strings.bookshelf.rereadCountLabel(readCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "▼",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun StatusDropdownMenu(
    currentStatus: ReadingStatus,
    onStatusSelected: (ReadingStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalAppStrings.current

    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
    ) {
        ReadingStatus.entries.forEach { status ->
            val statusColor = readingStatusColor(status)
            val statusLabel = readingStatusLabel(status)
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(statusLabel)
                        if (status == currentStatus) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    }
                },
                onClick = { onStatusSelected(status) },
            )
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun readingStatusColor(status: ReadingStatus): Color {
    return when (status) {
        ReadingStatus.WANT_TO_READ -> Color(0xFF9C9082)
        ReadingStatus.READING -> Color(0xFF8B5E3C)
        ReadingStatus.PAUSED -> Color(0xFF9A6500)
        ReadingStatus.FINISHED -> Color(0xFF2D7A52)
        ReadingStatus.ABANDONED -> Color(0xFF9B3525)
    }
}

@Composable
fun readingStatusLabel(status: ReadingStatus): String {
    val strings = LocalAppStrings.current
    return when (status) {
        ReadingStatus.WANT_TO_READ -> strings.bookshelf.statusWantToRead
        ReadingStatus.READING -> strings.bookshelf.statusReading
        ReadingStatus.PAUSED -> strings.bookshelf.statusPaused
        ReadingStatus.FINISHED -> strings.bookshelf.statusFinished
        ReadingStatus.ABANDONED -> strings.bookshelf.statusAbandoned
    }
}
