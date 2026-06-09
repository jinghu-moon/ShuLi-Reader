package com.shuli.reader.feature.reader.component.directory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.ChapterReadingStatsEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.testing.UiTestTags
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 目录弹窗 - 章节列表（增强版）。
 *
 * 每行显示：已读/未读指示器 | 标题 + 字数 | 书签/笔记图标 | 阅读耗时
 */
@Composable
internal fun ChapterList(
    chapters: List<String>,
    currentIndex: Int,
    wordCounts: List<Int>,
    onChapterClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chapterStats: List<ChapterReadingStatsEntity> = emptyList(),
    bookmarks: List<BookmarkEntity> = emptyList(),
    notes: List<NoteEntity> = emptyList(),
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current

    if (chapters.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = strings.common.loading,
                style = MaterialTheme.typography.bodyMedium,
                color = readerColors.textSecondary,
            )
        }
        return
    }

    val listState = rememberLazyListState()

    // 按 chapterIndex 建立快速查找 Map
    val statsMap = remember(chapterStats) {
        chapterStats.associateBy { it.chapterIndex }
    }
    val bookmarkCountMap = remember(bookmarks) {
        bookmarks.groupingBy { it.chapterIndex }.eachCount()
    }
    val noteCountMap = remember(notes) {
        // NoteEntity 没有 chapterIndex 字段，需要通过 byteOffset 推断
        // 此处简化：不在 per-chapter 层面统计笔记数量
        emptyMap<Int, Int>()
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex in chapters.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .then(Modifier.heightIn(max = 400.dp))
            .testTag(UiTestTags.READER_DIRECTORY_CHAPTER_LIST),
    ) {
        items(chapters.size) { index ->
            val stats = statsMap[index]
            val isVisited = stats?.visited == true
            val readTimeSeconds = stats?.readTimeSeconds ?: 0L
            val bmCount = bookmarkCountMap[index] ?: 0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChapterClick(index) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 已读/未读指示器
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(
                        color = if (isVisited) readerColors.accent
                        else readerColors.textTertiary.copy(alpha = 0.3f),
                    )
                }
                Spacer(Modifier.width(10.dp))

                // 标题 + 当前标签
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (chapters[index] == "Full Text") strings.settings.chapterFullText else chapters[index],
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value - 2).sp,
                        ),
                        color = if (index == currentIndex) readerColors.accent else readerColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (index == currentIndex) {
                        Text(
                            text = strings.reader.currentChapterLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = readerColors.accent,
                        )
                    }
                }

                // 书签图标 + 计数
                if (bmCount > 0) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmark,
                        contentDescription = null,
                        tint = readerColors.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    if (bmCount > 1) {
                        Text(
                            text = "$bmCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = readerColors.textTertiary,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

                // 字数
                if (index < wordCounts.size) {
                    Text(
                        text = formatWordCount(wordCounts[index], strings),
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.textTertiary,
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // 阅读耗时
                if (readTimeSeconds > 0) {
                    Text(
                        text = formatReadTime(readTimeSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.textTertiary,
                    )
                }
            }
            if (index < chapters.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = readerColors.divider,
                )
            }
        }
    }
}

private fun formatWordCount(count: Int, strings: com.shuli.reader.core.i18n.AppStrings): String {
    return when {
        count >= 10000 -> strings.reader.wordCountTenThousand(count / 10000.0f)
        else -> strings.reader.wordCountUnit(count)
    }
}

/** 格式化阅读耗时：秒 → "5min" / "1h12min" / "--" */
private fun formatReadTime(seconds: Long): String {
    if (seconds <= 0) return "--"
    val minutes = seconds / 60
    val hours = minutes / 60
    val remainMinutes = minutes % 60
    return when {
        hours > 0 -> "${hours}h${remainMinutes}min"
        minutes > 0 -> "${minutes}min"
        else -> "<1min"
    }
}
