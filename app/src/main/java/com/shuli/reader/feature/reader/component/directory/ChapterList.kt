package com.shuli.reader.feature.reader.component.directory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.core.i18n.AppStrings
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.testing.UiTestTags
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 目录章节列表。
 */
@Composable
fun ChapterList(
    chapters: List<String>,
    currentIndex: Int,
    wordCounts: List<Int>,
    onChapterClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
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
                text = strings.loading,
                style = MaterialTheme.typography.bodyMedium,
                color = readerColors.textSecondary,
            )
        }
        return
    }

    val listState = rememberLazyListState()

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChapterClick(index) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (chapters[index] == "Full Text") strings.chapterFullText else chapters[index],
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value - 2).sp,
                    ),
                    color = if (index == currentIndex) {
                        readerColors.accent
                    } else {
                        readerColors.textPrimary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (index == currentIndex) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.currentChapterLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.accent,
                    )
                }
                if (index < wordCounts.size) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatWordCount(wordCounts[index], strings),
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.textTertiary,
                    )
                }
            }
            if (index < chapters.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = readerColors.divider,
                )
            }
        }
    }
}

internal fun formatWordCount(count: Int, strings: AppStrings): String {
    return when {
        count >= 10000 -> strings.wordCountTenThousand(count / 10000.0f)
        else -> strings.wordCountUnit(count)
    }
}

internal fun formatByteOffset(offset: Long): String {
    return when {
        offset >= 1024 * 1024 -> String.format("%.1fMB", offset / (1024.0 * 1024.0))
        offset >= 1024 -> String.format("%.1fKB", offset / 1024.0)
        else -> "${offset}B"
    }
}
