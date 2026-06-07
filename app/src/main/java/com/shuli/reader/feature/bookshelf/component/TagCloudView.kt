package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.core.database.dao.TagWithCount
import com.shuli.reader.core.i18n.LocalAppStrings
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagCloudView(
    tags: List<TagWithCount>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    if (tags.isEmpty()) {
        Text(
            text = strings.bookshelf.noTags,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp),
        )
        return
    }

    val maxCount = tags.maxOfOrNull { it.usageCount } ?: 1

    FlowRow(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            val scale = if (maxCount > 0) {
                0.7f + 0.6f * (tag.usageCount.toFloat() / maxCount)
            } else 1f

            AssistChip(
                onClick = { onTagClick(tag.name) },
                label = {
                    Text(
                        text = "${tag.name} (${tag.usageCount})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = (12 * scale).sp,
                        ),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = getTagColor(tag.colorIndex).copy(alpha = 0.15f),
                ),
            )
        }
    }
}
