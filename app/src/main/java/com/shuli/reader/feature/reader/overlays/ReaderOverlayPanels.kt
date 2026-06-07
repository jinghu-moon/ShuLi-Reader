package com.shuli.reader.feature.reader.overlays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.shuli.reader.core.data.toFactoryType
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.feature.reader.ReaderViewModel
import com.shuli.reader.feature.reader.component.DirectoryDialog
import com.shuli.reader.feature.reader.component.QuickSettingsSheet
import com.shuli.reader.feature.reader.component.quicksettings.QuickSettingsActions

/**
 * 目录弹窗 + 快捷设置面板的组装层。
 */
@Composable
internal fun ReaderOverlayPanels(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
) {
    val nav = viewModel.navigationCoordinator
    val notes = viewModel.bookmarkNotesManager
    val settings = viewModel.readerSettingsManager
    val presets = viewModel.readerPresetManager
    val tts = viewModel.ttsPlaybackManager
    val fonts = viewModel.fontImportManager

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
                nav.toggleDirectory()
            },
            onBookmarkClick = { bookmark ->
                notes.goToBookmark(bookmark)
                nav.toggleDirectory()
            },
            onBookmarkDelete = { bookmark ->
                notes.deleteBookmark(bookmark)
            },
            onNoteClick = { note ->
                notes.goToNote(note)
                nav.toggleDirectory()
            },
            onNoteDelete = { note ->
                notes.deleteNote(note)
            },
            onNoteEdit = { note, newText, newColor ->
                notes.updateNote(note, newText, newColor)
            },
            onDismiss = {
                nav.toggleDirectory()
            }
        )
    }

    // 快捷设置面板
    if (uiState.showQuickSettings) {
        val quickSettingsActions = remember(viewModel) {
            QuickSettingsActions(
                onDismiss = nav::toggleQuickSettings,
                onBrightnessChange = settings::setBrightness,
                onFontSizeChange = settings::setFontSize,
                onLineSpacingChange = settings::setLineSpacing,
                onParagraphSpacingChange = settings::setParagraphSpacing,
                onIndentChange = settings::setIndent,
                onMarginVerticalChange = settings::setMarginVertical,
                onMarginHorizontalChange = settings::setMarginHorizontal,
                onReadingFontChange = settings::setReadingFont,
                onPageAnimTypeChange = { type ->
                    nav.setPageAnimType(type.toFactoryType()) { viewModel.pageDelegate = it }
                },
                onThemeChange = settings::setReaderTheme,
                onLetterSpacingChange = settings::setLetterSpacing,
                onFontWeightChange = settings::setFontWeight,
                onTextAlignChange = settings::setTextAlign,
                onChineseConvertChange = settings::setChineseConvert,
                onUseZhLayoutChange = settings::setUseZhLayout,
                onPanguSpacingChange = settings::setPanguSpacing,
                onApplyPreset = presets::applyPreset,
                onSavePreset = presets::saveCurrentAsPreset,
                onRenamePreset = presets::renamePreset,
                onDeletePreset = presets::deletePreset,
                onResetToDefault = presets::resetToDefault,
                onHeaderVisibilityChange = settings::setHeaderVisibility,
                onHeaderLeftChange = settings::setHeaderLeft,
                onHeaderCenterChange = settings::setHeaderCenter,
                onHeaderRightChange = settings::setHeaderRight,
                onFooterVisibilityChange = settings::setFooterVisibility,
                onFooterLeftChange = settings::setFooterLeft,
                onFooterCenterChange = settings::setFooterCenter,
                onFooterRightChange = settings::setFooterRight,
                onHeaderFooterAlphaChange = settings::setHeaderFooterAlpha,
                onHeaderMarginTopChange = settings::setHeaderMarginTop,
                onFooterMarginBottomChange = settings::setFooterMarginBottom,
                onShowProgressChange = settings::setShowProgress,
                onTitleAlignChange = settings::setTitleAlign,
                onTitleSizeOffsetChange = settings::setTitleSizeOffset,
                onTitleMarginTopChange = settings::setTitleMarginTop,
                onTitleMarginBottomChange = settings::setTitleMarginBottom,
                onKeepScreenOnChange = settings::setKeepScreenOn,
                onVolumeKeyTurnPageChange = settings::setVolumeKeyTurnPage,
                onEdgeTurnPageChange = settings::setEdgeTurnPage,
                onEdgeWidthPercentChange = settings::setEdgeWidthPercent,
                onShowHeaderLineChange = settings::setShowHeaderLine,
                onShowFooterLineChange = settings::setShowFooterLine,
                onHeaderFontSizeRatioChange = settings::setHeaderFontSizeRatio,
                onFooterFontSizeRatioChange = settings::setFooterFontSizeRatio,
                onBottomJustifyChange = settings::setBottomJustify,
                ttsState = uiState.ttsState,
                onTtsStart = { tts.startTts() },
                onTtsPause = { tts.pauseTts() },
                onTtsStop = { tts.stopTts() },
                onTtsSpeedChange = { settings.setTtsSpeed(it) },
                onTtsPitchChange = { settings.setTtsPitch(it) },
                onImportFont = { fonts.importFont(it) },
                onDeleteFont = { fonts.deleteFont(it) },
            )
        }
        QuickSettingsSheet(
            uiState = uiState,
            actions = quickSettingsActions,
        )
    }
}
