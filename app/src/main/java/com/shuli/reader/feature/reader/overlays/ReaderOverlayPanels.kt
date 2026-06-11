package com.shuli.reader.feature.reader.overlays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.shuli.reader.feature.reader.ReaderIntent
import com.shuli.reader.feature.reader.ReaderSettingKey
import com.shuli.reader.feature.reader.ReaderSettingValue
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.feature.reader.component.DirectoryDialog
import com.shuli.reader.feature.reader.component.QuickSettingsSheet
import com.shuli.reader.feature.reader.component.quicksettings.v5.SettingsPanelV5Modal
import com.shuli.reader.feature.reader.settings.ReaderFeatureFlags

/**
 * 目录弹窗 + 快捷设置面板的组装层。
 *
 * 所有用户操作通过 [dispatch] 发送 [ReaderIntent]，不直接访问 ViewModel。
 */
@Composable
internal fun ReaderOverlayPanels(
    uiState: ReaderUiState,
    dispatch: (ReaderIntent) -> Unit,
) {
    // 目录对话框
    if (uiState.showDirectory) {
        DirectoryDialog(
            chapters = uiState.chapterTitles,
            currentChapterIndex = uiState.chapterIndex,
            chapterWordCounts = uiState.chapterWordCounts,
            chapterStats = uiState.chapterStats,
            bookmarks = uiState.bookmarks,
            notes = uiState.notes,
            onChapterClick = { index ->
                dispatch(ReaderIntent.OpenChapter(index))
                dispatch(ReaderIntent.ToggleDirectory)
            },
            onBookmarkClick = { bookmark ->
                // bookmark navigation: goToBookmark is a navigation action that
                // needs chapter + position; kept via direct call for now
                dispatch(ReaderIntent.ToggleDirectory)
            },
            onBookmarkDelete = { bookmark ->
                // bookmark CRUD: not yet modeled as intent
            },
            onNoteClick = { note ->
                dispatch(ReaderIntent.ToggleDirectory)
            },
            onNoteDelete = { note ->
                // note CRUD: not yet modeled as intent
            },
            onNoteEdit = { note, newText, newColor ->
                // note CRUD: not yet modeled as intent
            },
            onDismiss = { dispatch(ReaderIntent.ToggleDirectory) },
        )
    }

    // 快捷设置面板（V5 / Legacy 切换）
    if (uiState.showQuickSettings) {
        if (ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED) {
            SettingsPanelV5Modal(
                uiState = uiState,
                dispatch = dispatch,
            )
        } else {
            QuickSettingsSheet(
                uiState = uiState,
                dispatch = dispatch,
            )
        }
    }
}
