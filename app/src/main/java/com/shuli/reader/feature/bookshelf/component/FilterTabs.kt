package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.bookshelf.model.FilterType

@Composable
fun FilterTabs(
    selected: FilterType,
    onSelect: (FilterType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val tabs = listOf(
        FilterType.ALL to strings.bookshelf.filterAll,
        FilterType.READING to strings.bookshelf.statusReading,
        FilterType.WANT_TO_READ to strings.bookshelf.statusWantToRead,
        FilterType.PAUSED to strings.bookshelf.statusPaused,
        FilterType.FINISHED to strings.bookshelf.statusFinished,
        FilterType.ABANDONED to strings.bookshelf.statusAbandoned,
        FilterType.FAVORITE to strings.bookshelf.filterFavorite,
    )

    ScrollableTabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        modifier = modifier.fillMaxWidth(),
        edgePadding = 12.dp,
    ) {
        tabs.forEach { (filter, label) ->
            Tab(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                text = { Text(label) },
            )
        }
    }
}
