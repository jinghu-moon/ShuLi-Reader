package com.shuli.reader.feature.reader.screen
import com.shuli.reader.feature.reader.screen.ReaderSettingKey
import com.shuli.reader.feature.reader.screen.ReaderSettingValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.shuli.reader.feature.reader.screen.ReaderIntent
import com.shuli.reader.feature.reader.screen.ReaderUiState
import com.shuli.reader.feature.reader.component.DirectoryDrawer
import com.shuli.reader.feature.reader.settings.panel.ReaderSettingsModal

/**
 * 目录侧边抽屉 + 快捷设置面板的组装层。
 *
 * 所有用户操作通过 [dispatch] 发送 [ReaderIntent]，不直接访问 ViewModel。
 */
@Composable
internal fun ReaderOverlayPanels(
    uiState: ReaderUiState,
    dispatch: (ReaderIntent) -> Unit,
) {
    // 目录侧边抽屉
    if (uiState.showDirectory) {
        DirectoryDrawer(
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

    // 快捷设置面板
    if (uiState.showQuickSettings) {
        ReaderSettingsModal(
            uiState = uiState,
            dispatch = dispatch,
        )
    }
}
