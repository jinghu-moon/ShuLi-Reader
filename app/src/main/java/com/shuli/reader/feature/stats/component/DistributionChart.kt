package com.shuli.reader.feature.stats.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.util.StatsFormatter
import com.shuli.reader.feature.stats.DistributionDim
import com.shuli.reader.feature.stats.DistributionItem

@Composable
fun DistributionChart(
    dimension: DistributionDim,
    items: List<DistributionItem>,
    onDimensionChange: (DistributionDim) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.stats
    val isDark = isSystemInDarkTheme()

    val dimensions = listOf(
        DistributionDim.AUTHOR to strings.dimAuthor,
        DistributionDim.GROUP to strings.dimGroup,
        DistributionDim.FORMAT to strings.dimFormat,
        DistributionDim.WORDS to strings.dimWords,
    )

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = strings.distributionTitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        SegmentedControl(
            options = dimensions.map { it.second },
            selectedIndex = dimensions.indexOfFirst { it.first == dimension },
            onSelectionChange = { index -> onDimensionChange(dimensions[index].first) },
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (items.isEmpty()) {
            Text(
                text = strings.notRead,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(100.dp)) {
                val strokeWidth = 16.dp.toPx()
                val radius = (size.width - strokeWidth) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                var startAngle = -90f

                items.forEachIndexed { index, item ->
                    val sweep = item.percent / 100f * 360f
                    val color = getColorForItem(index, item.label, dimension)

                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth),
                    )
                    startAngle += sweep
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                items.take(5).forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(
                                color = getColorForItem(index, item.label, dimension),
                                radius = size.width / 2f,
                            )
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = StatsFormatter.formatDuration(item.minutes * 60),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = StatsFormatter.formatPercent(item.percent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun getColorForItem(index: Int, label: String, dimension: DistributionDim): Color {
    if (dimension == DistributionDim.FORMAT) {
        return when (label.uppercase()) {
            "TXT" -> StatsColors.txtFormatColor
            "EPUB" -> StatsColors.epubFormatColor
            else -> getColorByIndex(index)
        }
    }
    return getColorByIndex(index)
}

private fun getColorByIndex(index: Int): Color {
    return when (index % 6) {
        0 -> StatsColors.lightL4
        1 -> StatsColors.lightL3
        2 -> StatsColors.lightL2
        3 -> StatsColors.txtFormatColor
        4 -> StatsColors.epubFormatColor
        else -> StatsColors.lightL5
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(
                        topStart = if (index == 0) 8.dp else 0.dp,
                        bottomStart = if (index == 0) 8.dp else 0.dp,
                        topEnd = if (index == options.lastIndex) 8.dp else 0.dp,
                        bottomEnd = if (index == options.lastIndex) 8.dp else 0.dp,
                    ))
                    .clickable { onSelectionChange(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
