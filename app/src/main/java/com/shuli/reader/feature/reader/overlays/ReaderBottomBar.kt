package com.shuli.reader.feature.reader.overlays

import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.feature.reader.component.CanvasSlider
import com.shuli.reader.ui.testing.UiTestTags
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.ReaderDimens

/**
 * 阅读器底部工具栏。
 *
 * 职责：章节跳转、页码进度条、快捷操作按钮（目录/主题/书签/设置）。
 */
@Composable
fun ReaderBottomBar(
    uiState: ReaderUiState,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPageScrubStart: () -> Unit,
    onPageScrub: (Int) -> Unit,
    onPageScrubCommit: () -> Unit,
    onToggleDirectory: () -> Unit,
    onCycleTheme: () -> Unit,
    onAddBookmark: () -> Unit,
    onToggleQuickSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current

    Surface(
        color = readerColors.surface.copy(alpha = 0.95f),
        contentColor = readerColors.textPrimary,
        tonalElevation = ReaderDimens.ElevationMedium,
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Column {
            // 章节快捷跳转
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousChapter) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.previousChapter, tint = readerColors.textPrimary)
                }
                Text(
                    text = uiState.chapterTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = readerColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onNextChapter) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = strings.nextChapter, tint = readerColors.textPrimary)
                }
            }

            // 页码进度条
            if (uiState.totalPages > 1) {
                var isScrubbing by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.pageIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = readerColors.textSecondary,
                        modifier = Modifier.width(32.dp)
                    )
                    CanvasSlider(
                        value = uiState.pageIndex.toFloat(),
                        onValueChange = { v ->
                            val p = v.roundToInt()
                            if (!isScrubbing) {
                                onPageScrubStart()
                                isScrubbing = true
                            }
                            onPageScrub(p)
                        },
                        onValueChangeFinished = {
                            onPageScrubCommit()
                            isScrubbing = false
                        },
                        valueRange = 0f..(uiState.totalPages - 1).coerceAtLeast(1).toFloat(),
                        thumbColor = readerColors.accent,
                        activeTrackColor = readerColors.accent,
                        inactiveTrackColor = readerColors.divider,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${uiState.totalPages}",
                        style = MaterialTheme.typography.labelMedium,
                        color = readerColors.textSecondary,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            // 操作按钮组
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleDirectory,
                    modifier = Modifier.testTag(UiTestTags.READER_DIRECTORY_BUTTON)
                ) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Outlined.List, contentDescription = strings.directoryTab, tint = readerColors.textPrimary)
                }
                IconButton(onClick = onCycleTheme) {
                    val isDark = uiState.readerPreferences.backgroundColor == ReaderTheme.DARK
                            || uiState.readerPreferences.backgroundColor == ReaderTheme.OLED
                    Icon(
                        imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = strings.themeModeLabel,
                        tint = readerColors.textPrimary,
                    )
                }
                IconButton(onClick = onAddBookmark) {
                    Icon(
                        Icons.Outlined.Bookmark,
                        contentDescription = strings.addBookmarkAction,
                        tint = readerColors.textPrimary,
                    )
                }
                IconButton(onClick = onToggleQuickSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = strings.readerPreferences, tint = readerColors.textPrimary)
                }
            }
        }
    }
}
