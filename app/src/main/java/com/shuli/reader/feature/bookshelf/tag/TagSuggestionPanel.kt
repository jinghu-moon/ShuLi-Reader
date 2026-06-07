package com.shuli.reader.feature.bookshelf.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings

data class TagSuggestion(
    val tagName: String,
    val reason: String,
)

@Composable
fun TagSuggestionPanel(
    suggestions: List<TagSuggestion>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onAcceptAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.bookshelf.tagSuggestions,
                style = MaterialTheme.typography.titleSmall,
            )
            if (suggestions.size > 1) {
                Button(onClick = onAcceptAll) {
                    Text(strings.bookshelf.acceptAllSuggestions)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (suggestions.isEmpty()) {
            Text(
                text = strings.bookshelf.noSuggestions,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(suggestions) { suggestion ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = suggestion.tagName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = suggestion.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onAccept(suggestion.tagName) }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = strings.bookshelf.acceptSuggestion,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { onReject(suggestion.tagName) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = strings.bookshelf.rejectSuggestion,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.common.cancel)
        }
    }
}
