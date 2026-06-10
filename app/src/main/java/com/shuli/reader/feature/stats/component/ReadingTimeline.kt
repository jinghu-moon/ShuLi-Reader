package com.shuli.reader.feature.stats.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.util.StatsFormatter
import com.shuli.reader.feature.stats.dateKeyToLocalDate
import java.time.format.DateTimeFormatter

data class TimelineSession(
    val bookTitle: String,
    val chapterText: String,
    val durationSeconds: Long,
    val startedAt: Long,
    val endedAt: Long,
)

@Composable
fun ReadingTimeline(
    sessions: List<com.shuli.reader.core.database.entity.ReadingSessionEntity>,
    bookTitles: Map<Long, String>,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current.stats
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val mergedSessions = remember(sessions) {
        mergeSessions(sessions, bookTitles, timeFormatter, strings)
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = strings.readingTimeline,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (mergedSessions.isEmpty()) {
            Text(
                text = strings.emptyStateHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(mergedSessions) { session ->
                TimelineRow(session = session)
            }
        }
    }
}

@Composable
private fun TimelineRow(
    session: TimelineSession,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val startLocal = java.time.Instant.ofEpochMilli(session.startedAt)
        .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    val endLocal = java.time.Instant.ofEpochMilli(session.endedAt)
        .atZone(java.time.ZoneId.systemDefault()).toLocalTime()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .padding(vertical = 4.dp),
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth()) {
                drawRect(
                    color = StatsColors.statusReading,
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.bookTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = session.chapterText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(
                text = "${startLocal.format(timeFormatter)} - ${endLocal.format(timeFormatter)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = StatsFormatter.formatDuration(session.durationSeconds),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun mergeSessions(
    sessions: List<com.shuli.reader.core.database.entity.ReadingSessionEntity>,
    bookTitles: Map<Long, String>,
    timeFormatter: DateTimeFormatter,
    strings: com.shuli.reader.core.i18n.StatsStrings,
): List<TimelineSession> {
    if (sessions.isEmpty()) return emptyList()

    val sorted = sessions.sortedByDescending { it.startedAt }
    val result = mutableListOf<TimelineSession>()
    val mergeThresholdMs = 30 * 60 * 1000L

    var i = 0
    while (i < sorted.size) {
        val current = sorted[i]
        var mergedDuration = current.durationSeconds
        var mergedStart = current.startedAt
        var mergedEnd = current.endedAt
        var firstChapter = current.chapterIndex
        var lastChapter = current.chapterIndex
        var j = i + 1

        while (j < sorted.size) {
            val next = sorted[j]
            if (next.bookId == current.bookId &&
                (current.startedAt - next.endedAt) < mergeThresholdMs
            ) {
                mergedDuration += next.durationSeconds
                mergedStart = minOf(mergedStart, next.startedAt)
                mergedEnd = maxOf(mergedEnd, next.endedAt)
                firstChapter = minOf(firstChapter, next.chapterIndex)
                lastChapter = maxOf(lastChapter, next.chapterIndex)
                j++
            } else {
                break
            }
        }

        val chapterText = if (firstChapter == lastChapter) {
            strings.chapterSingle(firstChapter + 1)
        } else {
            strings.chapterRange(firstChapter + 1, lastChapter + 1)
        }

        result.add(
            TimelineSession(
                bookTitle = bookTitles[current.bookId] ?: "",
                chapterText = chapterText,
                durationSeconds = mergedDuration,
                startedAt = mergedStart,
                endedAt = mergedEnd,
            ),
        )

        i = j
    }

    return result
}
