package com.shuli.reader.feature.reader.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.feature.reader.component.quicksettings.QuickSettingsActions
import com.shuli.reader.feature.reader.component.quicksettings.LayoutPanel
import com.shuli.reader.feature.reader.component.quicksettings.SettingsPanel
import com.shuli.reader.feature.reader.component.quicksettings.StylePanel
import com.shuli.reader.feature.reader.component.quicksettings.ThemeColorRow
import com.shuli.reader.feature.reader.component.quicksettings.TAB_LAYOUT
import com.shuli.reader.feature.reader.component.quicksettings.TAB_SETTINGS
import com.shuli.reader.feature.reader.component.quicksettings.TAB_STYLE
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 快捷设置底部弹出面板（3-Tab 结构）
 *
 * 职责：Sheet 容器、Tab 切换、状态聚合。
 * 各 Tab 面板拆分至 quicksettings/ 子包。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsSheet(
    uiState: ReaderUiState,
    actions: QuickSettingsActions,
) {
    val readerColors = LocalReaderColorScheme.current
    val strings = com.shuli.reader.core.i18n.LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = sheetState,
        containerColor = readerColors.surface,
        contentColor = readerColors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = readerColors.textSecondary) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.45f)) {
            // 常驻：主题色块
            ThemeColorRow(
                currentTheme = uiState.readerPreferences.backgroundColor,
                onThemeChange = actions.onThemeChange,
            )

            HorizontalDivider(color = readerColors.divider)

            // 3-Tab 分类
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = readerColors.surface,
                contentColor = readerColors.textPrimary,
                divider = {},
            ) {
                listOf(strings.reader.layoutTab, strings.reader.styleTab, strings.reader.settingsTab).forEachIndexed { index, title ->
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
                    when (tab) {
                        TAB_LAYOUT -> LayoutPanel(
                            prefs = uiState.readerPreferences,
                            onFontSizeChange = actions.onFontSizeChange,
                            onLetterSpacingChange = actions.onLetterSpacingChange,
                            onLineSpacingChange = actions.onLineSpacingChange,
                            onParagraphSpacingChange = actions.onParagraphSpacingChange,
                            onIndentChange = actions.onIndentChange,
                            onMarginVerticalChange = actions.onMarginVerticalChange,
                            onMarginHorizontalChange = actions.onMarginHorizontalChange,
                        )
                        TAB_STYLE -> StylePanel(
                            prefs = uiState.readerPreferences,
                            customFonts = uiState.customFonts,
                            onImportFont = actions.onImportFont,
                            onDeleteFont = actions.onDeleteFont,
                            onPageAnimTypeChange = actions.onPageAnimTypeChange,
                            onReadingFontChange = actions.onReadingFontChange,
                            onFontWeightChange = actions.onFontWeightChange,
                            onTextAlignChange = actions.onTextAlignChange,
                            onChineseConvertChange = actions.onChineseConvertChange,
                            onUseZhLayoutChange = actions.onUseZhLayoutChange,
                            onPanguSpacingChange = actions.onPanguSpacingChange,
                            onBottomJustifyChange = actions.onBottomJustifyChange,
                        )
                        TAB_SETTINGS -> SettingsPanel(
                            prefs = uiState.readerPreferences,
                            presets = uiState.presets,
                            onHeaderVisibilityChange = actions.onHeaderVisibilityChange,
                            onFooterVisibilityChange = actions.onFooterVisibilityChange,
                            onShowProgressChange = actions.onShowProgressChange,
                            onHeaderFooterAlphaChange = actions.onHeaderFooterAlphaChange,
                            onHeaderMarginTopChange = actions.onHeaderMarginTopChange,
                            onFooterMarginBottomChange = actions.onFooterMarginBottomChange,
                            onHeaderLeftChange = actions.onHeaderLeftChange,
                            onHeaderCenterChange = actions.onHeaderCenterChange,
                            onHeaderRightChange = actions.onHeaderRightChange,
                            onFooterLeftChange = actions.onFooterLeftChange,
                            onFooterCenterChange = actions.onFooterCenterChange,
                            onFooterRightChange = actions.onFooterRightChange,
                            onTitleAlignChange = actions.onTitleAlignChange,
                            onTitleSizeOffsetChange = actions.onTitleSizeOffsetChange,
                            onTitleMarginTopChange = actions.onTitleMarginTopChange,
                            onTitleMarginBottomChange = actions.onTitleMarginBottomChange,
                            onKeepScreenOnChange = actions.onKeepScreenOnChange,
                            onVolumeKeyTurnPageChange = actions.onVolumeKeyTurnPageChange,
                            onEdgeTurnPageChange = actions.onEdgeTurnPageChange,
                            onEdgeWidthPercentChange = actions.onEdgeWidthPercentChange,
                            onShowHeaderLineChange = actions.onShowHeaderLineChange,
                            onShowFooterLineChange = actions.onShowFooterLineChange,
                            onHeaderFontSizeRatioChange = actions.onHeaderFontSizeRatioChange,
                            onFooterFontSizeRatioChange = actions.onFooterFontSizeRatioChange,
                            onBottomJustifyChange = actions.onBottomJustifyChange,
                            onApplyPreset = actions.onApplyPreset,
                            onSavePreset = actions.onSavePreset,
                            onRenamePreset = actions.onRenamePreset,
                            onDeletePreset = actions.onDeletePreset,
                            onResetToDefault = actions.onResetToDefault,
                            ttsState = actions.ttsState,
                            onTtsStart = actions.onTtsStart,
                            onTtsPause = actions.onTtsPause,
                            onTtsStop = actions.onTtsStop,
                            onTtsSpeedChange = actions.onTtsSpeedChange,
                            onTtsPitchChange = actions.onTtsPitchChange,
                        )
                    }
                }
            }
        }
    }
}
