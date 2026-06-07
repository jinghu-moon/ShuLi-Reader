package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFilterBar(
    activeFilters: List<String>,
    filterMode: TagFilterMode,
    onRemoveFilter: (String) -> Unit,
    onClearAll: () -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current

    if (activeFilters.isEmpty()) return

    FlowRow(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AssistChip(
            onClick = onToggleMode,
            label = {
                Text(
                    text = filterMode.label,
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        )

        activeFilters.forEach { tagName ->
            AssistChip(
                onClick = { onRemoveFilter(tagName) },
                label = {
                    Text(
                        text = "#$tagName",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = strings.bookshelf.removeTag,
                        modifier = Modifier.size(14.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }

        if (activeFilters.size > 1) {
            AssistChip(
                onClick = onClearAll,
                label = {
                    Text(
                        text = strings.bookshelf.clearFilter,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

enum class TagFilterMode(val label: String) {
    AND("AND"),
    OR("OR"),
}
