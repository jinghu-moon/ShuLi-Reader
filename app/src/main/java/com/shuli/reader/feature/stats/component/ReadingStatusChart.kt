package com.shuli.reader.feature.stats.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.util.StatsFormatter
import com.shuli.reader.feature.stats.StatusItem

@Composable
fun ReadingStatusChart(
    items: List<StatusItem>,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.stats

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = strings.readingStatus,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
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

        items.forEach { item ->
            ReadingStatusRow(item = item)
        }
    }
}

@Composable
private fun ReadingStatusRow(
    item: StatusItem,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.stats
    val statusColor = getStatusColor(item.status)
    val statusLabel = getStatusLabel(item.status, strings)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = statusColor, radius = size.width / 2f)
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "${item.count} (${StatsFormatter.formatPercent(item.percent)})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .padding(top = 4.dp),
        ) {
            val barWidth = size.width * (item.percent / 100f)
            drawRoundRect(
                color = trackColor,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(3f),
            )
            drawRoundRect(
                color = statusColor,
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(3f),
            )
        }
    }
}

private fun getStatusColor(status: String): Color {
    return when (status.lowercase()) {
        "reading", "在读", "在讀" -> StatsColors.statusReading
        "finished", "已读完", "已讀完" -> StatsColors.statusFinished
        "paused", "暂停", "暫停" -> StatsColors.statusPaused
        else -> StatsColors.statusWantToRead
    }
}

private fun getStatusLabel(status: String, strings: com.shuli.reader.core.i18n.StatsStrings): String {
    return when (status.lowercase()) {
        "reading", "在读", "在讀" -> strings.statusReading
        "finished", "已读完", "已讀完" -> strings.statusFinished
        "paused", "暂停", "暫停" -> strings.statusPaused
        else -> strings.statusWantToRead
    }
}
