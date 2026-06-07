package com.shuli.reader.feature.bookshelf.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.database.entity.TagEntity
import com.shuli.reader.core.i18n.LocalAppStrings

val TAG_COLORS = listOf(
    Color(0xFF8B5E3C),
    Color(0xFF5E5346),
    Color(0xFFB89568),
    Color(0xFF9C9082),
    Color(0xFFD4CCC0),
    Color(0xFF7D7162),
)

fun getTagColor(colorIndex: Int): Color {
    return TAG_COLORS[colorIndex.coerceIn(0, TAG_COLORS.size - 1)]
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFlow(
    tags: List<TagEntity>,
    onTagClick: (TagEntity) -> Unit,
    onAddClick: () -> Unit,
    onRemoveClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        tags.forEach { tag ->
            TagChip(
                tag = tag,
                onClick = { onTagClick(tag) },
                onRemove = { onRemoveClick(tag.id) },
            )
        }
        AddTagChip(onClick = onAddClick)
    }
}

@Composable
fun TagChip(
    tag: TagEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = getTagColor(tag.colorIndex)
    val strings = LocalAppStrings.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = strings.bookshelf.removeTag,
                modifier = Modifier.size(12.dp),
                tint = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
fun AddTagChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = strings.bookshelf.addTag,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = strings.bookshelf.addTag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
