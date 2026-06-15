package com.shuli.reader.feature.reader.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.ReaderIntent
import com.shuli.reader.feature.reader.ReaderSettingKey
import com.shuli.reader.feature.reader.ReaderSettingValue
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.feature.reader.SettingsScope
import com.shuli.reader.feature.reader.component.quicksettings.InteractionPanel
import com.shuli.reader.feature.reader.component.quicksettings.LayoutPanel
import com.shuli.reader.feature.reader.component.quicksettings.StylePanel
import com.shuli.reader.feature.reader.component.quicksettings.SettingsPanel
import com.shuli.reader.feature.reader.component.quicksettings.CustomThemePanel
import com.shuli.reader.feature.reader.component.quicksettings.ThemeColorRow
import com.shuli.reader.feature.reader.component.quicksettings.TAB_LAYOUT
import com.shuli.reader.feature.reader.component.quicksettings.TAB_FONT
import com.shuli.reader.feature.reader.component.quicksettings.TAB_PAGE
import com.shuli.reader.feature.reader.component.quicksettings.TAB_INTERACTION
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 快捷设置底部弹出面板（4-Tab 结构 + 作用域头部）
 *
 * 职责：Sheet 容器、作用域显示、Tab 切换、状态聚合。
 * 所有用户操作通过 [dispatch] 发送 [ReaderIntent]。
 * 各 Tab 面板拆分至 quicksettings/ 子包。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsSheet(
    uiState: ReaderUiState,
    dispatch: (ReaderIntent) -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = { dispatch(ReaderIntent.ToggleQuickSettings) },
        sheetState = sheetState,
        containerColor = readerColors.surface,
        contentColor = readerColors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = readerColors.textSecondary) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.45f)) {
            // 作用域头部
            ScopeHeader(
                settingsScope = uiState.settingsScope,
                hasBookOverrides = uiState.hasBookOverrides,
                onScopeChange = { scope -> dispatch(ReaderIntent.SetSettingsScope(scope)) },
                onResetToDefault = { dispatch(ReaderIntent.ResetSettingsToDefault) },
                onResetBookOverrides = { dispatch(ReaderIntent.ResetBookOverrides) },
            )

            // 常驻：主题色块
            ThemeColorRow(
                currentTheme = uiState.readerPreferences.backgroundColor,
                onThemeChange = { theme ->
                    dispatch(ReaderIntent.UpdateSetting(
                        ReaderSettingKey.THEME,
                        ReaderSettingValue.Theme(theme),
                    ))
                },
                customBackgroundColor = uiState.readerPreferences.customBackgroundColor,
            )

            // 自定义主题颜色面板（仅当 CUSTOM 主题激活时显示）
            if (uiState.readerPreferences.backgroundColor == com.shuli.reader.core.data.ReaderTheme.CUSTOM) {
                CustomThemePanel(
                    currentBg = uiState.readerPreferences.customBackgroundColor,
                    currentText = uiState.readerPreferences.customTextColor,
                    currentTitle = uiState.readerPreferences.customTitleColor,
                    currentHeaderFooter = uiState.readerPreferences.customHeaderFooterColor,
                    onColorChange = { bg, text, title, headerFooter ->
                        dispatch(ReaderIntent.UpdateSetting(
                            ReaderSettingKey.CUSTOM_THEME_COLOR,
                            ReaderSettingValue.CustomThemeColor(bg, text, title, headerFooter),
                        ))
                    },
                )
            }

            HorizontalDivider(color = readerColors.divider)

            // 4-Tab 分类
            val tabTitles = listOf(
                strings.reader.layoutTab,
                strings.reader.fontTab,
                strings.reader.pageTab,
                strings.reader.interactionTab,
            )
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = readerColors.surface,
                contentColor = readerColors.textPrimary,
                divider = {},
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            // Tab 内容（带 crossfade + 方向感知 slide 动画）
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it / 4 } + fadeIn(tween(200)) togetherWith
                            slideOutHorizontally { -it / 4 } + fadeOut(tween(150))
                    } else {
                        slideInHorizontally { -it / 4 } + fadeIn(tween(200)) togetherWith
                            slideOutHorizontally { it / 4 } + fadeOut(tween(150))
                    }
                },
                label = "tab-content",
            ) { tab ->
                val tabScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(tabScrollState)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    val prefs = uiState.readerPreferences
                    val updateFloat = { key: ReaderSettingKey, v: Float ->
                        dispatch(ReaderIntent.UpdateSetting(key, ReaderSettingValue.Float(v)))
                    }
                    val updateBool = { key: ReaderSettingKey, v: Boolean ->
                        dispatch(ReaderIntent.UpdateSetting(key, ReaderSettingValue.Bool(v)))
                    }
                    val updateStr = { key: ReaderSettingKey, v: String ->
                        dispatch(ReaderIntent.UpdateSetting(key, ReaderSettingValue.Str(v)))
                    }
                    val updateInt = { key: ReaderSettingKey, v: Int ->
                        dispatch(ReaderIntent.UpdateSetting(key, ReaderSettingValue.Int(v)))
                    }

                    when (tab) {
                        TAB_LAYOUT -> LayoutPanel(
                            prefs = prefs,
                            onFontSizeChange = { updateFloat(ReaderSettingKey.FONT_SIZE, it) },
                            onLetterSpacingChange = { updateFloat(ReaderSettingKey.LETTER_SPACING, it) },
                            onLineSpacingChange = { updateFloat(ReaderSettingKey.LINE_SPACING, it) },
                            onParagraphSpacingChange = { updateFloat(ReaderSettingKey.PARAGRAPH_SPACING, it) },
                            onIndentChange = { updateFloat(ReaderSettingKey.INDENT, it) },
                            onIndentUnitChange = { unit ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.INDENT_UNIT, ReaderSettingValue.IndentUnit(unit)))
                            },
                            onMarginVerticalChange = { updateFloat(ReaderSettingKey.MARGIN_VERTICAL, it) },
                            onMarginHorizontalChange = { updateFloat(ReaderSettingKey.MARGIN_HORIZONTAL, it) },
                            onMaxPageWidthChange = { updateFloat(ReaderSettingKey.MAX_PAGE_WIDTH, it) },
                        )
                        TAB_FONT -> StylePanel(
                            prefs = prefs,
                            customFonts = uiState.customFonts,
                            onImportFont = { uri -> dispatch(ReaderIntent.ImportFont(uri)) },
                            onDeleteFont = { key -> dispatch(ReaderIntent.DeleteFont(key)) },
                            onPageAnimTypeChange = { type ->
                                dispatch(ReaderIntent.SetPageAnimType(type))
                            },
                            onReadingFontChange = { updateStr(ReaderSettingKey.READING_FONT, it) },
                            onFontWeightChange = { weight ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.FONT_WEIGHT, ReaderSettingValue.FontWeight(weight)))
                            },
                            onTextAlignChange = { align ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.TEXT_ALIGN, ReaderSettingValue.TextAlign(align)))
                            },
                            onChineseConvertChange = { convert: com.shuli.reader.core.data.ChineseConvert ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.CHINESE_CONVERT, ReaderSettingValue.ChineseConvert(convert)))
                            },
                            onUseZhLayoutChange = { updateBool(ReaderSettingKey.USE_ZH_LAYOUT, it) },
                            onPanguSpacingChange = { updateBool(ReaderSettingKey.USE_PANGU_SPACING, it) },
                            onBottomJustifyChange = { updateBool(ReaderSettingKey.BOTTOM_JUSTIFY, it) },
                            onRemoveEmptyLinesChange = { updateBool(ReaderSettingKey.REMOVE_EMPTY_LINES, it) },
                            onCleanChapterTitleChange = { updateBool(ReaderSettingKey.CLEAN_CHAPTER_TITLE, it) },
                            onEpubOverrideStyleChange = { updateBool(ReaderSettingKey.EPUB_OVERRIDE_STYLE, it) },
                        )
                        TAB_PAGE -> SettingsPanel(
                            prefs = prefs,
                            presets = uiState.presets,
                            onHeaderVisibilityChange = { v: com.shuli.reader.core.reader.HeaderVisibility ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.HEADER_VISIBILITY, ReaderSettingValue.HeaderVisibility(v)))
                            },
                            onFooterVisibilityChange = { v: com.shuli.reader.core.reader.HeaderVisibility ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.FOOTER_VISIBILITY, ReaderSettingValue.HeaderVisibility(v)))
                            },
                            onShowProgressChange = { updateBool(ReaderSettingKey.SHOW_PROGRESS, it) },
                            onHeaderFooterAlphaChange = { updateFloat(ReaderSettingKey.HEADER_FOOTER_ALPHA, it) },
                            onHeaderMarginTopChange = { updateFloat(ReaderSettingKey.HEADER_MARGIN_TOP, it) },
                            onFooterMarginBottomChange = { updateFloat(ReaderSettingKey.FOOTER_MARGIN_BOTTOM, it) },
                            onHeaderLeftChange = { v: com.shuli.reader.core.reader.SlotContent ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.HEADER_LEFT, ReaderSettingValue.SlotContent(v)))
                            },
                            onHeaderCenterChange = { v: com.shuli.reader.core.reader.SlotContent ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.HEADER_CENTER, ReaderSettingValue.SlotContent(v)))
                            },
                            onHeaderRightChange = { v: com.shuli.reader.core.reader.SlotContent ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.HEADER_RIGHT, ReaderSettingValue.SlotContent(v)))
                            },
                            onFooterLeftChange = { v: com.shuli.reader.core.reader.SlotContent ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.FOOTER_LEFT, ReaderSettingValue.SlotContent(v)))
                            },
                            onFooterCenterChange = { v: com.shuli.reader.core.reader.SlotContent ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.FOOTER_CENTER, ReaderSettingValue.SlotContent(v)))
                            },
                            onFooterRightChange = { v: com.shuli.reader.core.reader.SlotContent ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.FOOTER_RIGHT, ReaderSettingValue.SlotContent(v)))
                            },
                            onTitleAlignChange = { align: com.shuli.reader.core.reader.TitleAlign ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.TITLE_ALIGN, ReaderSettingValue.TitleAlign(align)))
                            },
                            onTitleSizeOffsetChange = { updateInt(ReaderSettingKey.TITLE_SIZE_OFFSET, it) },
                            onTitleMarginTopChange = { updateFloat(ReaderSettingKey.TITLE_MARGIN_TOP, it) },
                            onTitleMarginBottomChange = { updateFloat(ReaderSettingKey.TITLE_MARGIN_BOTTOM, it) },
                            onKeepScreenOnChange = { updateBool(ReaderSettingKey.KEEP_SCREEN_ON, it) },
                            onVolumeKeyTurnPageChange = { updateBool(ReaderSettingKey.VOLUME_KEY_TURN_PAGE, it) },
                            onEdgeTurnPageChange = { updateBool(ReaderSettingKey.EDGE_TURN_PAGE, it) },
                            onEdgeWidthPercentChange = { updateFloat(ReaderSettingKey.EDGE_WIDTH_PERCENT, it) },
                            onShowHeaderLineChange = { updateBool(ReaderSettingKey.SHOW_HEADER_LINE, it) },
                            onShowFooterLineChange = { updateBool(ReaderSettingKey.SHOW_FOOTER_LINE, it) },
                            onHeaderFontSizeRatioChange = { updateFloat(ReaderSettingKey.HEADER_FONT_SIZE_RATIO, it) },
                            onFooterFontSizeRatioChange = { updateFloat(ReaderSettingKey.FOOTER_FONT_SIZE_RATIO, it) },
                            onBottomJustifyChange = { updateBool(ReaderSettingKey.BOTTOM_JUSTIFY, it) },
                            onProgressStyleChange = { style ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.PROGRESS_STYLE, ReaderSettingValue.ProgressStyle(style)))
                            },
                            onAutoNightModeChange = { updateBool(ReaderSettingKey.AUTO_NIGHT_MODE, it) },
                            onApplyPreset = { id -> dispatch(ReaderIntent.ApplyPreset(id)) },
                            onSavePreset = { name -> dispatch(ReaderIntent.SavePreset(name)) },
                            onRenamePreset = { id, name -> dispatch(ReaderIntent.RenamePreset(id, name)) },
                            onDeletePreset = { id -> dispatch(ReaderIntent.DeletePreset(id)) },
                            onResetToDefault = { dispatch(ReaderIntent.ResetSettingsToDefault) },
                        )
                        TAB_INTERACTION -> InteractionPanel(
                            prefs = prefs,
                            onBrightnessChange = { value ->
                                dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.BRIGHTNESS, ReaderSettingValue.Float(value)))
                            },
                            onPageAnimTypeChange = { type ->
                                dispatch(ReaderIntent.SetPageAnimType(type))
                            },
                            onVolumeKeyTurnPageChange = { updateBool(ReaderSettingKey.VOLUME_KEY_TURN_PAGE, it) },
                            onKeepScreenOnChange = { updateBool(ReaderSettingKey.KEEP_SCREEN_ON, it) },
                            onImmersiveModeChange = { updateBool(ReaderSettingKey.IMMERSIVE_MODE, it) },
                            onEdgeTurnPageChange = { updateBool(ReaderSettingKey.EDGE_TURN_PAGE, it) },
                            onEdgeWidthPercentChange = { updateFloat(ReaderSettingKey.EDGE_WIDTH_PERCENT, it) },
                            onLeftZoneRatioChange = { updateFloat(ReaderSettingKey.LEFT_ZONE_RATIO, it) },
                            onAutoPageTurnChange = { updateBool(ReaderSettingKey.AUTO_PAGE_TURN, it) },
                            onAutoPageTurnIntervalChange = { updateFloat(ReaderSettingKey.AUTO_PAGE_TURN_INTERVAL, it) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 作用域头部：作用域切换（本书/全局）+ 恢复默认按钮。
 */
@Composable
private fun ScopeHeader(
    settingsScope: SettingsScope,
    hasBookOverrides: Boolean,
    onScopeChange: (SettingsScope) -> Unit,
    onResetToDefault: () -> Unit,
    onResetBookOverrides: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    val scopeText = if (settingsScope == SettingsScope.BOOK) {
        strings.reader.settingsScopeBook
    } else {
        strings.reader.settingsScopeGlobal
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 作用域切换
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = scopeText,
                style = MaterialTheme.typography.labelMedium,
                color = readerColors.textSecondary,
            )
            // 切换按钮
            androidx.compose.material3.FilterChip(
                selected = settingsScope == SettingsScope.GLOBAL,
                onClick = { onScopeChange(SettingsScope.GLOBAL) },
                label = {
                    Text(
                        strings.reader.scopeLabelGlobal,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = readerColors.accent.copy(alpha = 0.15f),
                    selectedLabelColor = readerColors.accent,
                ),
            )
            androidx.compose.material3.FilterChip(
                selected = settingsScope == SettingsScope.BOOK,
                onClick = { onScopeChange(SettingsScope.BOOK) },
                label = {
                    Text(
                        strings.reader.scopeLabelBook,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = readerColors.accent.copy(alpha = 0.15f),
                    selectedLabelColor = readerColors.accent,
                ),
            )
        }

        // 恢复默认按钮
        TextButton(
            onClick = if (settingsScope == SettingsScope.BOOK && hasBookOverrides) {
                onResetBookOverrides
            } else {
                onResetToDefault
            },
        ) {
            Text(
                text = if (settingsScope == SettingsScope.BOOK && hasBookOverrides) {
                    strings.reader.resetBookOverrides
                } else {
                    strings.reader.resetToDefault
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
