package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings

@Composable
fun BookmarkNotesPreview(
    bookmarkCount: Int,
    noteCount: Int,
    latestAnnotation: String?,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onViewAll,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = strings.bookshelf.bookmarksAndNotes,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = strings.bookshelf.viewAll,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = strings.bookshelf.bookmarkNoteCount(bookmarkCount, noteCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            latestAnnotation?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
