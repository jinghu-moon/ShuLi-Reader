package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.bookshelf.model.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    selected: SortOrder,
    isAscending: Boolean,
    onSelect: (SortOrder) -> Unit,
    onDirectionSelect: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = strings.bookshelf.sortTitle,
                style = MaterialTheme.typography.titleMedium,
            )

            // 高雅的升降序 Segmented Control
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(2.dp)
            ) {
                listOf(false to strings.bookshelf.sortDescending, true to strings.bookshelf.sortAscending).forEach { (asc, label) ->
                    val isSelected = isAscending == asc
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onDirectionSelect(asc) }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val options = listOf(
            SortOrder.LAST_READ to strings.bookshelf.sortLastRead,
            SortOrder.ADD_TIME to strings.bookshelf.sortAddTime,
            SortOrder.TITLE to strings.bookshelf.sortBookTitle,
            SortOrder.FILE_SIZE to strings.bookshelf.sortReadingTime,
            SortOrder.PROGRESS to strings.bookshelf.sortReadingProgress,
            SortOrder.READING_STATUS to strings.bookshelf.sortReadingStatus,
            SortOrder.READ_COUNT to strings.bookshelf.sortReadCount,
        )

        LazyColumn(
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            items(options) { (order, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(order)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected == order) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
