package com.shuli.reader.feature.stats.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.util.StatsFormatter
import com.shuli.reader.feature.stats.DailyHeatCell
import com.shuli.reader.feature.stats.HeatLevel
import com.shuli.reader.feature.stats.StatsGranularity
import com.shuli.reader.feature.stats.dateKeyToLocalDate
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun CalendarHeatmap(
    cells: List<DailyHeatCell>,
    granularity: StatsGranularity,
    modifier: Modifier = Modifier,
    currentStreak: Int = 0,
    dailyAvgMinutes: Long = 0,
) {
    when (granularity) {
        StatsGranularity.YEAR -> YearCalendarHeatmap(cells = cells, modifier = modifier)
        StatsGranularity.MONTH -> MonthCalendarHeatmap(
            cells = cells,
            modifier = modifier,
            currentStreak = currentStreak,
            dailyAvgMinutes = dailyAvgMinutes,
        )
        else -> {}
    }
}

@Composable
fun YearCalendarHeatmap(
    cells: List<DailyHeatCell>,
    modifier: Modifier = Modifier,
    cellSize: Dp = 11.dp,
    cellGap: Dp = 3.dp,
) {
    val isDark = isSystemInDarkTheme()
    val scrollState = rememberScrollState()
    val strings = LocalAppStrings.current.stats
    val density = LocalDensity.current

    val stepPx = with(density) { (cellSize + cellGap).toPx() }
    val canvasWidthDp = with(density) { (54 * stepPx).toDp() }
    val canvasHeightDp = (cellSize + cellGap) * 7 + 20.dp

    val todayKey = remember {
        val today = LocalDate.now()
        today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
    }

    val accentColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (cells.isNotEmpty()) {
            val year = dateKeyToLocalDate(cells.first().dateKey).year
            val activeDays = cells.count { it.minutes > 0 }
            val totalHours = cells.sumOf { it.minutes } / 60
            Text(
                text = strings.heatmapTitle(year.toString()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = strings.heatmapSummary(activeDays, totalHours),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            Canvas(
                modifier = Modifier
                    .width(canvasWidthDp)
                    .height(canvasHeightDp),
            ) {
                val size = cellSize.toPx()
                val gap = cellGap.toPx()
                val step = size + gap
                val topOffset = 16.dp.toPx()

                cells.forEach { cell ->
                    val date = dateKeyToLocalDate(cell.dateKey)
                    val weekFields = WeekFields.of(Locale.getDefault())
                    val weekOfYear = date.get(weekFields.weekOfWeekBasedYear())
                    val dayOfWeek = (date.dayOfWeek.value - 1) % 7

                    val x = weekOfYear * step
                    val y = dayOfWeek * step + topOffset

                    if (cell.dateKey == todayKey) {
                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.6f),
                            topLeft = Offset(x - 2f, y - 2f),
                            size = Size(size + 4f, size + 4f),
                            cornerRadius = CornerRadius(3f),
                            style = Stroke(width = 2f),
                        )
                    }

                    drawRoundRect(
                        color = cell.heatLevel.color(isDark),
                        topLeft = Offset(x, y),
                        size = Size(size, size),
                        cornerRadius = CornerRadius(2f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.less,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                HeatLevel.entries.forEach { level ->
                    Canvas(modifier = Modifier.width(11.dp).height(11.dp)) {
                        drawRoundRect(
                            color = level.color(isDark),
                            size = Size(11.dp.toPx(), 11.dp.toPx()),
                            cornerRadius = CornerRadius(2f),
                        )
                    }
                }
            }
            Text(
                text = strings.more,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LaunchedEffect(Unit) {
            val today = LocalDate.now()
            val weekFields = WeekFields.of(Locale.getDefault())
            val currentWeekCol = today.get(weekFields.weekOfWeekBasedYear())
            val targetScroll = (currentWeekCol * stepPx.toInt() - 200).coerceAtLeast(0)
            scrollState.scrollTo(targetScroll.coerceAtMost(scrollState.maxValue))
        }
    }
}

@Composable
fun MonthCalendarHeatmap(
    cells: List<DailyHeatCell>,
    modifier: Modifier = Modifier,
    cellSize: Dp = 20.dp,
    cellGap: Dp = 4.dp,
    currentStreak: Int = 0,
    dailyAvgMinutes: Long = 0,
) {
    val isDark = isSystemInDarkTheme()
    val strings = LocalAppStrings.current.stats

    val todayKey = remember {
        val today = LocalDate.now()
        today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
    }

    val accentColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (cells.isNotEmpty()) {
            val date = dateKeyToLocalDate(cells.first().dateKey)
            val year = date.year
            Text(
                text = strings.heatmapTitle(year.toString()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            dayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(cellSize),
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((cellSize + cellGap) * 6),
        ) {
            val size = cellSize.toPx()
            val gap = cellGap.toPx()
            val step = size + gap

            if (cells.isEmpty()) return@Canvas

            val firstDate = dateKeyToLocalDate(cells.first().dateKey)
            val firstDayWeekday = (firstDate.withDayOfMonth(1).dayOfWeek.value - 1) % 7

            cells.forEachIndexed { index, cell ->
                val dayOfMonth = cell.dateKey % 100
                val col = (firstDayWeekday + dayOfMonth - 1) % 7
                val row = (firstDayWeekday + dayOfMonth - 1) / 7

                val x = col * step + (this.size.width - 7 * step) / 2f
                val y = row * step

                if (cell.dateKey == todayKey) {
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.6f),
                        topLeft = Offset(x - 2f, y - 2f),
                        size = Size(size + 4f, size + 4f),
                        cornerRadius = CornerRadius(4f),
                        style = Stroke(width = 2f),
                    )
                }

                drawRoundRect(
                    color = cell.heatLevel.color(isDark),
                    topLeft = Offset(x, y),
                    size = Size(size, size),
                    cornerRadius = CornerRadius(3f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val activeDays = cells.count { it.minutes > 0 }
            val totalMinutes = cells.sumOf { it.minutes }
            SummaryItem(
                value = StatsFormatter.formatDuration(totalMinutes * 60),
                label = strings.cumulativeLabel,
            )
            SummaryItem(
                value = "$activeDays",
                label = strings.activeDays,
            )
            SummaryItem(
                value = "$currentStreak",
                label = strings.longestStreak,
            )
            SummaryItem(
                value = if (dailyAvgMinutes > 0) StatsFormatter.formatDuration(dailyAvgMinutes * 60) else "--",
                label = strings.dailyAvg,
            )
        }
    }
}

@Composable
private fun SummaryItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
