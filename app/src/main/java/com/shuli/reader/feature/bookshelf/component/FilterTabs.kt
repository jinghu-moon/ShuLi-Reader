package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        FilterType.ALL to strings.filterAll,
        FilterType.FINISHED to strings.filterFinished,
        FilterType.FAVORITE to strings.filterFavorite,
    )

    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selected },
        modifier = modifier.fillMaxWidth(),
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
