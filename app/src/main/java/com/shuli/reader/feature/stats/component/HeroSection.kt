package com.shuli.reader.feature.stats.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.util.StatsFormatter
import com.shuli.reader.feature.stats.StatsHeroState
import kotlin.math.min

@Composable
fun HeroSection(
    heroState: StatsHeroState,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.stats
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.cumulativeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = StatsFormatter.zeroOrNull(heroState.totalMinutes * 60),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics {
                        contentDescription = "Total reading time: ${StatsFormatter.formatDuration(heroState.totalMinutes * 60)}"
                    },
                )
                Spacer(Modifier.height(4.dp))
                if (heroState.deltaPercent != 0f) {
                    val trendIcon = if (heroState.deltaIsUp) "↑" else "↓"
                    Text(
                        text = "$trendIcon ${StatsFormatter.formatPercent(kotlin.math.abs(heroState.deltaPercent))} ${strings.vsPrevious}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (heroState.deltaIsUp) StatsColors.statusFinished
                        else StatsColors.statusPaused,
                    )
                }
            }

            GoalRing(
                percent = heroState.goalPercent,
                modifier = Modifier.size(72.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HeroMetric(
                value = heroState.activeDays.toString(),
                label = strings.activeDays,
            )
            HeroMetric(
                value = strings.currentStreak(heroState.currentStreak),
                label = strings.longestStreak,
            )
            HeroMetric(
                value = if (heroState.activeDays > 0)
                    StatsFormatter.formatDuration((heroState.totalMinutes / heroState.activeDays) * 60)
                else "--",
                label = strings.dailyAvg,
            )
        }

        if (heroState.dailyNeededMinutes > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.dailyNeededHint(heroState.dailyNeededMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroMetric(
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoalRing(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val animatedPercent by animateFloatAsState(
        targetValue = percent.coerceIn(0, 100).toFloat(),
        label = "goalRing",
    )
    val trackColor = StatsColors.goalRingTrack
    val progressColor = StatsColors.goalRingProgress

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth()) {
            val strokeWidth = 8.dp.toPx()
            val radius = min(size.width, size.height) / 2f - strokeWidth / 2f
            val topLeft = Offset(
                (size.width - radius * 2) / 2f,
                (size.height - radius * 2) / 2f,
            )
            val arcSize = Size(radius * 2, radius * 2)

            drawArc(
                color = trackColor,
                startAngle = -225f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            drawArc(
                color = progressColor,
                startAngle = -225f,
                sweepAngle = 270f * (animatedPercent / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Text(
            text = "${percent}%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
