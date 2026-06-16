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
    val indentUnitLabel: String
    val indentUnitChar: String
    val indentUnitDp: String
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
    val fontTab: String
    val pageTab: String
    val interactionTab: String
    val settingsScopeBook: String
    val settingsScopeGlobal: String
    val scopeLabelBook: String
    val scopeLabelGlobal: String
    val resetBookOverrides: String
    val resetBookOverridesConfirm: String
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
    val immersiveModeLabel: String
    val immersiveModeDesc: String
    // P1 设置项
    val maxPageWidthLabel: String
    val maxPageWidthUnlimited: String
    val removeEmptyLinesLabel: String
    val removeEmptyLinesDesc: String
    val cleanChapterTitleLabel: String
    val cleanChapterTitleDesc: String
    val preserveOriginalIndentLabel: String
    val preserveOriginalIndentDesc: String
    val progressStyleLabel: String
    val progressStyleChapterFraction: String
    val progressStyleChapterPercent: String
    val progressStylePageNumber: String
    val progressStyleBookFraction: String
    val progressStyleBookPercent: String
    // P2 设置项
    val autoPageTurnLabel: String
    val autoPageTurnDesc: String
    val autoPageTurnIntervalLabel: String
    val epubOverrideStyleLabel: String
    val epubOverrideStyleDesc: String
    // P0: 触控热区
    val leftZoneRatioLabel: String
    val leftZoneRatioDesc: String
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

    // 自定义主题
    val customThemeLabel: String
    val customThemeBg: String
    val customThemeText: String
    val customThemeAccent: String

    // EPUB 图片占位
    val imagePlaceholder: String

    // 字数统计
    val wordCountTenThousand: (Float) -> String
    val wordCountUnit: (Int) -> String

    // 章节阅读统计
    val chapterReadTimeLabel: String
    val chapterRead: String
    val chapterUnread: String

    // P1: 设置面板 Tab i18n
    // TypeAndFontTab
    val basicTypesettingCard: String
    val marginTopLabel: String
    val marginBottomLabel: String
    val marginLeftLabel: String
    val marginRightLabel: String
    val syncMarginsLabel: String
    val maxPageWidthUnlimitedShort: String
    val fontCardTitle: String
    val advancedTypesettingCard: String
    val fontWeightMediumFull: String
    val textAlignJustifyFull: String
    val panguSpacingLabel: String
    val bottomJustifyDesc: String
    val paragraphDividerLabel: String
    val bionicReadingDesc: String
    val chineseConvertFullLabel: String
    val chineseConvertNoneFull: String
    val usePanguSpacingFullLabel: String
    val removeEmptyLinesShortLabel: String
    val cleanChapterTitleShortLabel: String
    val preserveOriginalIndentShortDesc: String
    val epubOverrideStyleShortLabel: String
    val epubOverrideStyleShortDesc: String

    // AppearanceTab
    val headerFooterCard: String
    val visibilityLabel: String
    val displayAlwaysShowShort: String
    val colorTemperatureLabel: String
    val displayModeCard: String
    val dualPageModeLabel: String
    val autoLabel: String
    val singlePageLabel: String
    val dualPageLabel: String
    val backgroundTextureLabel: String
    val solidColorLabel: String
    val linenTextureLabel: String
    val gridTextureLabel: String
    val pageAnimSpeedLabel: String
    val pageAnimSpeedFast: String
    val pageAnimSpeedNormal: String
    val pageAnimSpeedSlow: String
    val pageAnimTypeLabel: String
    val pageAnimTypeHorizontal: String
    val pageAnimTypeScroll: String

    // BehaviorTab
    val pageTurnCard: String
    val volumeKeyTurnPageLabel: String
    val edgeTurnPageShortDesc: String
    val touchZoneCard: String
    val hapticFeedbackLabel: String
    val eyeCareCard: String
    val eyeCareReminderLabel: String
    val eyeCareReminderDesc: String
    val offLabel: String
    val minutes15: String
    val minutes30: String
    val minutes45: String
    val minutes60: String
    val generalCard: String
    val keepScreenOnShortLabel: String
    val orientationLockLabel: String
    val portraitLockLabel: String
    val landscapeLockLabel: String
    val gestureZoneSettingsLabel: String
    val currentMiddleZonePrefix: String
    val enterLabel: String
    val headerSeparatorLineLabel: String
    val footerSeparatorLineLabel: String

    // P2: 其他设置面板组件 i18n
    // SlotMatrix
    val slotBlankLabel: String
    val slotChapterShort: String
    val slotChapterPercentShort: String
    val slotBookShort: String
    val slotBookPercentShort: String
    val infoGroupLabel: String
    val progressGroupLabel: String
    val expandLabel: String

    // CustomThemeDialog
    val customThemeTitle: String
    val customThemeTextColor: String
    val customThemeTitleColor: String
    val customThemeHeaderFooterColor: String
    val selectColorTitle: String
    val brightnessLabel: String

    // GestureZoneGrid
    val gestureNone: String
    val gesturePrevPage: String
    val gestureNextPage: String
    val gestureToolbar: String
    val gestureDirectory: String
    val gestureBookmark: String
    val gestureTheme: String
    val gestureImmersive: String
    val gestureScrollUp: String
    val gestureScrollDown: String
    val tapZoneSelectAction: String
    val pageTurnGroup: String
    val readingGroup: String

    // GestureZoneEditorOverlay
    val gestureZoneEditorTitle: String
    val closeLabel: String

    // VisualMarginControl
    val marginTopShort: String
    val marginLeftShort: String
    val marginRightShort: String
    val marginBottomShort: String

    // ThemeSwatchRow
    val themeLabel: String
    val selectedLabel: String

    // ReaderSettingsPeek
    val darkModeLabel: String
    val lightModeLabel: String
    val eyeCareModeLabel: String
    val landscapeLockShortLabel: String
    val scopeGlobalShort: String
    val scopeBookShort: String

    // ReaderSettingsPanel
    val clearBookSettings: String
    val resetToDefaultShort: String

    // ReaderSettingsCard
    val collapseLabel: String

    // FontPreviewRow
    val fontPreviewSample: String
    val fontHarmonyShort: String
    val fontSystemShort: String
    val deleteFontTitle: String
    val deleteFontConfirm: (String) -> String
    val deleteLabel: String
    val cancelLabel: String

    // SettingsTab
    val settingsTabTypeAndFont: String
    val settingsTabAppearance: String
    val settingsTabBehavior: String
}
