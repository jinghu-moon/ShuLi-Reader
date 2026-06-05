package com.shuli.reader.feature.reader.overlays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.shuli.reader.core.data.toFactoryType
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.feature.reader.ReaderViewModel
import com.shuli.reader.feature.reader.component.DirectoryDialog
import com.shuli.reader.feature.reader.component.QuickSettingsActions
import com.shuli.reader.feature.reader.component.QuickSettingsSheet

/**
 * 阅读器浮层面板集合。
 *
 * 职责：目录对话框、快速设置面板。
 */
@Composable
fun ReaderOverlayPanels(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
) {
    // 目录对话框
    if (uiState.showDirectory) {
        DirectoryDialog(
            chapters = uiState.chapterTitles,
            currentChapterIndex = uiState.chapterIndex,
            chapterWordCounts = uiState.chapterWordCounts,
            bookmarks = uiState.bookmarks,
            notes = uiState.notes,
            onChapterClick = { index ->
                viewModel.openChapter(index)
                viewModel.toggleDirectory()
            },
            onBookmarkClick = { bookmark ->
                viewModel.goToBookmark(bookmark)
                viewModel.toggleDirectory()
            },
            onBookmarkDelete = { bookmark ->
                viewModel.deleteBookmark(bookmark)
            },
            onNoteClick = { note ->
                viewModel.goToNote(note)
                viewModel.toggleDirectory()
            },
            onNoteDelete = { note ->
                viewModel.deleteNote(note)
            },
            onNoteEdit = { note, newText, newColor ->
                viewModel.updateNote(note, newText, newColor)
            },
            onDismiss = {
                viewModel.toggleDirectory()
            }
        )
    }

    // 快速设置面板
    if (uiState.showQuickSettings) {
        val quickSettingsActions = remember(viewModel) {
            QuickSettingsActions(
                onDismiss = viewModel::toggleQuickSettings,
                onBrightnessChange = viewModel::setBrightness,
                onFontSizeChange = viewModel::setFontSize,
                onLineSpacingChange = viewModel::setLineSpacing,
                onParagraphSpacingChange = viewModel::setParagraphSpacing,
                onIndentChange = viewModel::setIndent,
                onMarginVerticalChange = viewModel::setMarginVertical,
                onMarginHorizontalChange = viewModel::setMarginHorizontal,
                onReadingFontChange = viewModel::setReadingFont,
                onPageAnimTypeChange = { type ->
                    viewModel.setPageAnimType(type.toFactoryType())
                },
                onThemeChange = viewModel::setReaderTheme,
                onLetterSpacingChange = viewModel::setLetterSpacing,
                onFontWeightChange = viewModel::setFontWeight,
                onTextAlignChange = viewModel::setTextAlign,
                onChineseConvertChange = viewModel::setChineseConvert,
                onUseZhLayoutChange = viewModel::setUseZhLayout,
                onPanguSpacingChange = viewModel::setPanguSpacing,
                onApplyPreset = viewModel::applyPreset,
                onSavePreset = viewModel::saveCurrentAsPreset,
                onRenamePreset = viewModel::renamePreset,
                onDeletePreset = viewModel::deletePreset,
                onResetToDefault = viewModel::resetToDefault,
                onHeaderVisibilityChange = viewModel::setHeaderVisibility,
                onHeaderLeftChange = viewModel::setHeaderLeft,
                onHeaderCenterChange = viewModel::setHeaderCenter,
                onHeaderRightChange = viewModel::setHeaderRight,
                onFooterVisibilityChange = viewModel::setFooterVisibility,
                onFooterLeftChange = viewModel::setFooterLeft,
                onFooterCenterChange = viewModel::setFooterCenter,
                onFooterRightChange = viewModel::setFooterRight,
                onHeaderFooterAlphaChange = viewModel::setHeaderFooterAlpha,
                onHeaderMarginTopChange = viewModel::setHeaderMarginTop,
                onFooterMarginBottomChange = viewModel::setFooterMarginBottom,
                onShowProgressChange = viewModel::setShowProgress,
                onTitleAlignChange = viewModel::setTitleAlign,
                onTitleSizeOffsetChange = viewModel::setTitleSizeOffset,
                onTitleMarginTopChange = viewModel::setTitleMarginTop,
                onTitleMarginBottomChange = viewModel::setTitleMarginBottom,
                onKeepScreenOnChange = viewModel::setKeepScreenOn,
                onVolumeKeyTurnPageChange = viewModel::setVolumeKeyTurnPage,
                onEdgeTurnPageChange = viewModel::setEdgeTurnPage,
                onEdgeWidthPercentChange = viewModel::setEdgeWidthPercent,
                onShowHeaderLineChange = viewModel::setShowHeaderLine,
                onShowFooterLineChange = viewModel::setShowFooterLine,
                onHeaderFontSizeRatioChange = viewModel::setHeaderFontSizeRatio,
                onFooterFontSizeRatioChange = viewModel::setFooterFontSizeRatio,
                onBottomJustifyChange = viewModel::setBottomJustify,
                ttsState = uiState.ttsState,
                onTtsStart = { viewModel.startTts() },
                onTtsPause = { viewModel.pauseTts() },
                onTtsStop = { viewModel.stopTts() },
                onTtsSpeedChange = { viewModel.setTtsSpeed(it) },
                onTtsPitchChange = { viewModel.setTtsPitch(it) },
                onImportFont = { viewModel.importFont(it) },
                onDeleteFont = { viewModel.deleteFont(it) },
            )
        }
        QuickSettingsSheet(
            uiState = uiState,
            actions = quickSettingsActions,
        )
    }
}
