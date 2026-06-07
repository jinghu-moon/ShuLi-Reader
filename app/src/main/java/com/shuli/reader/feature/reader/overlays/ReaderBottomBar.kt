package com.shuli.reader.feature.reader.overlays

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reader.animation.ReaderMotionTokens
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.feature.reader.component.CanvasSlider
import com.shuli.reader.ui.testing.UiTestTags
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.ReaderDimens
import kotlin.math.roundToInt

@Composable
internal fun ReaderBottomBar(
    uiState: ReaderUiState,
    onToggleDirectory: () -> Unit,
    onCycleTheme: () -> Unit,
    onAddBookmark: () -> Unit,
    onToggleQuickSettings: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onStartPageScrub: () -> Unit,
    onScrubToPage: (Int) -> Unit,
    onCommitPageScrub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current

    AnimatedVisibility(
        visible = uiState.showToolbar && !uiState.showSearch,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())) + fadeIn(animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())) + fadeOut(animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())),
        modifier = modifier,
    ) {
        Surface(
            color = readerColors.surface.copy(alpha = 0.95f),
            contentColor = readerColors.textPrimary,
            tonalElevation = ReaderDimens.ElevationMedium,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
        ) {
            Column {
                // 章节快捷跳转
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (uiState.chapterIndex > 0) onOpenChapter(uiState.chapterIndex - 1) }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.reader.previousChapter, tint = readerColors.textPrimary)
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
                    IconButton(
                        onClick = { if (uiState.chapterIndex + 1 < uiState.totalChapters) onOpenChapter(uiState.chapterIndex + 1) }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = strings.reader.nextChapter, tint = readerColors.textPrimary)
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
                                    onStartPageScrub()
                                    isScrubbing = true
                                }
                                onScrubToPage(p)
                            },
                            onValueChangeFinished = {
                                onCommitPageScrub()
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
                        Icon(Icons.Outlined.List, contentDescription = strings.reader.directoryTab, tint = readerColors.textPrimary)
                    }
                    IconButton(onClick = onCycleTheme) {
                        val isDark = uiState.readerPreferences.backgroundColor == ReaderTheme.DARK
                                || uiState.readerPreferences.backgroundColor == ReaderTheme.OLED
                        Icon(
                            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = strings.common.themeModeLabel,
                            tint = readerColors.textPrimary,
                        )
                    }
                    IconButton(onClick = onAddBookmark) {
                        Icon(
                            Icons.Outlined.Bookmark,
                            contentDescription = strings.reader.addBookmarkAction,
                            tint = readerColors.textPrimary,
                        )
                    }
                    IconButton(onClick = onToggleQuickSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = strings.reader.readerPreferences, tint = readerColors.textPrimary)
                    }
                }
            }
        }
    }
}
