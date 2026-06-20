package com.shuli.reader.feature.stats.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.util.StatsFormatter
import com.shuli.reader.feature.stats.TopNBookItem
import com.shuli.reader.feature.stats.TopNSort

@Composable
fun TopNList(
    sort: TopNSort,
    books: List<TopNBookItem>,
    onSortChange: (TopNSort) -> Unit,
    onBookClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.stats

    val sorts = listOf(
        TopNSort.DURATION to strings.sortByDuration,
        TopNSort.BOOKMARKS to strings.sortByBookmarks,
        TopNSort.NOTES to strings.sortByNotes,
        TopNSort.SPEED to strings.sortBySpeed,
    )

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = strings.topNTitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        SegmentedControl(
            options = sorts.map { it.second },
            selectedIndex = sorts.indexOfFirst { it.first == sort },
            onSelectionChange = { index -> onSortChange(sorts[index].first) },
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (books.isEmpty()) {
            Text(
                text = strings.notRead,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            books.forEach { book ->
                TopNBookRow(
                    book = book,
                    sort = sort,
                    onClick = { onBookClick(book.bookId) },
                )
            }
        }
    }
}

@Composable
private fun TopNBookRow(
    book: TopNBookItem,
    sort: TopNSort,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val valueText = when (sort) {
        TopNSort.DURATION -> StatsFormatter.formatDuration(book.value * 60)
        TopNSort.BOOKMARKS -> "${book.value}"
        TopNSort.NOTES -> "${book.value}"
        TopNSort.SPEED -> "${book.value} WPM"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${book.title}, ${book.author ?: ""}, $valueText"
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            book.author?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
