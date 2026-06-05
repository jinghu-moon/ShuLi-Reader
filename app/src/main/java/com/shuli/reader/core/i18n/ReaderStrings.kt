package com.shuli.reader.core.i18n

/**
 * 阅读器显示偏好、快捷设置面板、目录/书签/笔记、交互提示字符串。
 */
interface ReaderStrings {
    // 阅读器显示偏好
    val readerPreferences: String
    val defaultFontSize: String
    val defaultLineSpacing: String
    val lineSpacingCompact: String
    val lineSpacingMedium: String
    val lineSpacingWide: String
    val defaultPageAnim: String
    val pageAnimOverlay: String
    val pageAnimSlide: String
    val pageAnimSimulation: String
    val pageAnimFade: String
    val pageAnimNone: String
    val pageTurnDirection: String
    val pageTurnHorizontal: String
    val pageTurnVertical: String
    val paragraphSpacing: String
    val paragraphSpacingCompact: String
    val paragraphSpacingNormal: String
    val paragraphSpacingWide: String
    val firstLineIndent: String
    val indentNone: String
    val indentTwoChars: String
    val fullScreenMode: String
    val keepScreenOn: String
    val brightness: String
    val brightnessFollowSystem: String
    val brightnessFollowSystemLabel: String
    val brightnessResetToSystem: String
    val readingFont: String
    val readingFontSystem: String
    val readingFontHarmony: String
    val readingFontLxgw: String
    val importFont: String
    val fontTestText: String
    val marginTopBottom: String
    val marginLeftRight: String
    val editValue: String
    val confirm: String

    // 快捷设置面板
    val layoutTab: String
    val styleTab: String
    val settingsTab: String
    val letterSpacingLabel: String
    val fontWeightLabel: String
    val fontWeightLight: String
    val fontWeightNormal: String
    val fontWeightMedium: String
    val fontWeightBold: String
    val textAlignLabel: String
    val textAlignLeft: String
    val textAlignJustify: String
    val chineseConvertLabel: String
    val chineseConvertNone: String
    val chineseConvertSimplified: String
    val chineseConvertTraditional: String
    val useZhLayoutLabel: String
    val usePanguSpacingLabel: String
    val slotNone: String
    val slotChapterTitle: String
    val slotBookTitle: String
    val slotChapterProgressFraction: String
    val slotChapterProgressPercent: String
    val slotBookProgressFraction: String
    val slotBookProgressPercent: String
    val slotTime: String
    val slotBattery: String
    val slotDate: String
    val headerLabel: String
    val footerLabel: String
    val progressBarLabel: String
    val opacityLabel: String
    val headerFooterCustom: String
    val headerHidden: String
    val footerHidden: String
    val displayLabel: String
    val displayFollowStatusBar: String
    val displayAlwaysShow: String
    val displayAlwaysHide: String
    val positionLeft: String
    val positionCenter: String
    val positionRight: String
    val headerLeft: String
    val headerCenter: String
    val headerRight: String
    val footerLeft: String
    val footerCenter: String
    val footerRight: String
    val titleStyleLabel: String
    val titleAlignLeft: String
    val titleAlignCenter: String
    val titleAlignHidden: String
    val titleSizeOffset: String
    val titleMarginTop: String
    val titleMarginBottom: String
    val headerMarginTop: String
    val footerMarginBottom: String
    val keepScreenOnLabel: String
    val keepScreenOnDesc: String
    val volumeKeyLabel: String
    val volumeKeyDesc: String
    val edgeTurnPageLabel: String
    val edgeTurnPageDesc: String
    val edgeWidthLabel: String
    val headerLineLabel: String
    val footerLineLabel: String
    val headerFontSizeLabel: String
    val footerFontSizeLabel: String
    val bottomJustifyLabel: String
    val readingPresets: String
    val savePresetAction: String
    val resetToDefault: String
    val resetToDefaultConfirm: String
    val savePresetTitle: String
    val presetNameLabel: String
    val deletePresetTitle: String
    val deletePresetConfirm: String
    val confirmAction: String
    val cancelAction: String
    val saveAction: String
    val deleteAction: String
    val deleteBookmarkTitle: String
    val deleteBookmarkConfirm: String
    val deleteNoteTitle: String
    val deleteNoteConfirm: String

    // 目录、书签与笔记
    val directoryTab: String
    val bookmarksTab: String
    val notesTab: String
    val currentChapterLabel: String
    val noBookmarks: String
    val noNotes: String
    val notePosition: (Int, Int, String) -> String
    val copySelection: String
    val addBookmarkAction: String
    val addNoteAction: String
    val previousChapter: String
    val nextChapter: String
    val customizeCover: String
    val resetCoverColor: String
    val unifiedCoverColor: String
    val unifiedCoverColorAuto: String
    val unifiedCoverColorActive: (Int) -> String

    // 交互响应提示
    val saveSuccess: String
    val saveFailed: String
    val alreadyLatestVersion: String
    val alreadyFirstPage: String
    val alreadyLastPage: String

    // EPUB 图片占位
    val imagePlaceholder: String

    // 字数统计
    val wordCountTenThousand: (Float) -> String
    val wordCountUnit: (Int) -> String
}
