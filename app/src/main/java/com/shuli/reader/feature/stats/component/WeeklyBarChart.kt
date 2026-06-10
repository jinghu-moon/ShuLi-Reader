package com.shuli.reader.feature.stats.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.shuli.reader.feature.stats.WeekChartData

@Composable
fun WeeklyBarChart(
    chartData: WeekChartData,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val strings = com.shuli.reader.core.i18n.LocalAppStrings.current.stats
    var isVisible by remember { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        label = "barAnimation",
    )

    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val barWidth = size.width / 14f
            val maxVal = maxOf(
                chartData.thisWeek.maxOrNull() ?: 0L,
                chartData.lastWeek.maxOrNull() ?: 0L,
                1L,
            ).toFloat()
            val chartHeight = size.height - 20.dp.toPx()

            for (day in 0 until 7) {
                val x = day * barWidth * 2 + barWidth * 0.2f

                val lastWeekHeight = (chartData.lastWeek.getOrNull(day) ?: 0L).toFloat() / maxVal * chartHeight
                drawRoundRect(
                    color = if (isDark) StatsColors.barDarkLastWeek else StatsColors.barLastWeek,
                    topLeft = Offset(x, chartHeight - lastWeekHeight * animationProgress),
                    size = Size(barWidth * 0.8f, lastWeekHeight * animationProgress),
                    cornerRadius = CornerRadius(4f),
                )

                val thisWeekHeight = (chartData.thisWeek.getOrNull(day) ?: 0L).toFloat() / maxVal * chartHeight
                drawRoundRect(
                    color = StatsColors.barThisWeek,
                    topLeft = Offset(x + barWidth, chartHeight - thisWeekHeight * animationProgress),
                    size = Size(barWidth * 0.8f, thisWeekHeight * animationProgress),
                    cornerRadius = CornerRadius(4f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            dayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.height(8.dp).padding(end = 4.dp)) {
                drawRoundRect(
                    color = StatsColors.barThisWeek,
                    size = Size(12.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(2f),
                )
            }
            Text(
                text = strings.thisWeek,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            Canvas(modifier = Modifier.height(8.dp).padding(end = 4.dp)) {
                drawRoundRect(
                    color = if (isDark) StatsColors.barDarkLastWeek else StatsColors.barLastWeek,
                    size = Size(12.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(2f),
                )
            }
            Text(
                text = strings.lastWeek,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
