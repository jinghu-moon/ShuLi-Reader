package com.shuli.reader.feature.stats.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.stats.HeatLevel
import com.shuli.reader.feature.stats.StatsRepository

@Composable
fun HourlyHeatmap(
    hourlyMinutes: List<Int>,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val strings = LocalAppStrings.current.stats

    val maxMinutes = remember(hourlyMinutes) { hourlyMinutes.maxOrNull() ?: 0 }
    val peakHour = remember(hourlyMinutes) {
        if (maxMinutes > 0) hourlyMinutes.indexOfFirst { it == maxMinutes } else -1
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (peakHour >= 0 && maxMinutes > 0) {
            val endHour = (peakHour + 1) % 24
            Text(
                text = strings.hourlyPeak(peakHour, endHour),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        val rowLabels = listOf("0h", "8h", "16h")
        val rows = 3
        val cols = 8

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = rowLabels[row],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(24.dp),
                    )

                    for (col in 0 until cols) {
                        val hourIndex = row * cols + col
                        val minutes = if (hourIndex < hourlyMinutes.size) hourlyMinutes[hourIndex] else 0
                        val heatLevel = if (maxMinutes > 0) {
                            StatsRepository.heatLevel(minutes.toLong(), maxMinutes.toLong())
                        } else {
                            HeatLevel.L0
                        }
                        val isPeak = hourIndex == peakHour && maxMinutes > 0

                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp),
                        ) {
                            drawRoundRect(
                                color = heatLevel.color(isDark),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(4f),
                            )
                        }

                        if (isPeak) {
                            // Peak label shown as overlay Text
                        }
                    }
                }

                if (row == 0 || row == 1 || row == 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (col in 0 until cols) {
                            val hourIndex = row * cols + col
                            val isPeak = hourIndex == peakHour && maxMinutes > 0
                            if (isPeak) {
                                Text(
                                    text = "$hourIndex",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
