package com.shuli.reader.feature.reader.component.quicksettings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.TitleAlign
import com.shuli.reader.feature.reader.component.ReaderFormPickerRow
import com.shuli.reader.feature.reader.component.ReaderSwitchRow
import com.shuli.reader.feature.reader.component.ReaderValueSlider
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Tab 3: 设置面板 — 页眉页脚、标题样式、行为开关、TTS、预设管理
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SettingsPanel(
    prefs: ReaderPreferences,
    presets: List<com.shuli.reader.core.database.entity.ReaderPresetEntity>,
    onHeaderVisibilityChange: (HeaderVisibility) -> Unit,
    onFooterVisibilityChange: (HeaderVisibility) -> Unit,
    onShowProgressChange: (Boolean) -> Unit,
    onHeaderFooterAlphaChange: (Float) -> Unit,
    onHeaderMarginTopChange: (Float) -> Unit,
    onFooterMarginBottomChange: (Float) -> Unit,
    onHeaderLeftChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onHeaderCenterChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onHeaderRightChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onFooterLeftChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onFooterCenterChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onFooterRightChange: (com.shuli.reader.core.reader.SlotContent) -> Unit,
    onTitleAlignChange: (TitleAlign) -> Unit,
    onTitleSizeOffsetChange: (Int) -> Unit,
    onTitleMarginTopChange: (Float) -> Unit,
    onTitleMarginBottomChange: (Float) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVolumeKeyTurnPageChange: (Boolean) -> Unit,
    onEdgeTurnPageChange: (Boolean) -> Unit,
    onEdgeWidthPercentChange: (Float) -> Unit,
    onShowHeaderLineChange: (Boolean) -> Unit,
    onShowFooterLineChange: (Boolean) -> Unit,
    onHeaderFontSizeRatioChange: (Float) -> Unit,
    onFooterFontSizeRatioChange: (Float) -> Unit,
    onBottomJustifyChange: (Boolean) -> Unit,
    onProgressStyleChange: (com.shuli.reader.core.data.ProgressStyle) -> Unit = {},
    onAutoNightModeChange: (Boolean) -> Unit = {},
    onApplyPreset: (Long) -> Unit,
    onSavePreset: (String) -> Unit,
    onRenamePreset: (Long, String) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onResetToDefault: () -> Unit,
    ttsState: com.shuli.reader.core.tts.TtsState = com.shuli.reader.core.tts.TtsState.IDLE,
    onTtsStart: () -> Unit = {},
    onTtsPause: () -> Unit = {},
    onTtsStop: () -> Unit = {},
    onTtsSpeedChange: (Float) -> Unit = {},
    onTtsPitchChange: (Float) -> Unit = {},
) {
    val readerColors = LocalReaderColorScheme.current
    val strings = LocalAppStrings.current
    var expandedHeaderFooter by remember { mutableStateOf(false) }
    var expandedTitleStyle by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Long?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    // ── 页面元素开关 ──
    ReaderSwitchRow(
        label = strings.reader.headerLabel,
        checked = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE,
        onCheckedChange = { onHeaderVisibilityChange(if (it) HeaderVisibility.HIDE_WHEN_STATUS_BAR else HeaderVisibility.ALWAYS_HIDE) },
    )
    ReaderSwitchRow(
        label = strings.reader.footerLabel,
        checked = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE,
        onCheckedChange = { onFooterVisibilityChange(if (it) HeaderVisibility.ALWAYS_SHOW else HeaderVisibility.ALWAYS_HIDE) },
    )
    ReaderSwitchRow(
        label = strings.reader.progressBarLabel,
        checked = prefs.showProgress,
        onCheckedChange = onShowProgressChange,
    )
    // P1: 进度显示样式
    ReaderFormPickerRow(
        label = strings.reader.progressStyleLabel,
        options = listOf(
            com.shuli.reader.core.data.ProgressStyle.CHAPTER_FRACTION to strings.reader.progressStyleChapterFraction,
            com.shuli.reader.core.data.ProgressStyle.CHAPTER_PERCENT to strings.reader.progressStyleChapterPercent,
            com.shuli.reader.core.data.ProgressStyle.PAGE_NUMBER to strings.reader.progressStylePageNumber,
            com.shuli.reader.core.data.ProgressStyle.BOOK_FRACTION to strings.reader.progressStyleBookFraction,
            com.shuli.reader.core.data.ProgressStyle.BOOK_PERCENT to strings.reader.progressStyleBookPercent,
        ),
        selected = prefs.progressStyle,
        onSelect = onProgressStyleChange,
    )
    ReaderValueSlider(
        label = strings.reader.opacityLabel,
        value = prefs.headerFooterAlpha,
        valueRange = 0.1f..1.0f,
        steps = 8,
        format = { "%.0f%%".format(it * 100) },
        onValueChange = onHeaderFooterAlphaChange,
    )
    // P2: 自动夜间模式
    ReaderSwitchRow(
        label = strings.reader.autoNightModeLabel,
        description = strings.reader.autoNightModeDesc,
        checked = prefs.autoNightMode,
        onCheckedChange = onAutoNightModeChange,
    )

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    // ── 页眉脚详细（可折叠） ──
    ExpandableSection(
        title = strings.reader.headerFooterCustom,
        expanded = expandedHeaderFooter,
        onToggle = { expandedHeaderFooter = !expandedHeaderFooter },
    ) {
        HeaderFooterCustomizationPanel(
            prefs = prefs,
            onHeaderVisibilityChange = onHeaderVisibilityChange,
            onFooterVisibilityChange = onFooterVisibilityChange,
            onHeaderMarginTopChange = onHeaderMarginTopChange,
            onFooterMarginBottomChange = onFooterMarginBottomChange,
            onHeaderLeftChange = onHeaderLeftChange,
            onHeaderCenterChange = onHeaderCenterChange,
            onHeaderRightChange = onHeaderRightChange,
            onFooterLeftChange = onFooterLeftChange,
            onFooterCenterChange = onFooterCenterChange,
            onFooterRightChange = onFooterRightChange,
            onShowHeaderLineChange = onShowHeaderLineChange,
            onShowFooterLineChange = onShowFooterLineChange,
            onHeaderFontSizeRatioChange = onHeaderFontSizeRatioChange,
            onFooterFontSizeRatioChange = onFooterFontSizeRatioChange,
        )
    }

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    // ── 标题样式（可折叠） ──
    ExpandableSection(
        title = strings.reader.titleStyleLabel,
        expanded = expandedTitleStyle,
        onToggle = { expandedTitleStyle = !expandedTitleStyle },
    ) {
        ReaderFormPickerRow(
            label = strings.reader.textAlignLabel,
            options = listOf(
                TitleAlign.LEFT to strings.reader.titleAlignLeft,
                TitleAlign.CENTER to strings.reader.titleAlignCenter,
                TitleAlign.HIDDEN to strings.reader.titleAlignHidden,
            ),
            selected = prefs.titleStyle.align,
            onSelect = onTitleAlignChange,
        )
        if (prefs.titleStyle.align != TitleAlign.HIDDEN) {
            ReaderValueSlider(
                label = strings.reader.titleSizeOffset,
                value = prefs.titleStyle.sizeOffsetSp.toFloat(),
                valueRange = 0f..16f,
                steps = 15,
                format = { "+${it.toInt()}sp" },
                onValueChange = { onTitleSizeOffsetChange(it.toInt()) },
            )
            ReaderValueSlider(
                label = strings.reader.titleMarginTop,
                value = prefs.titleStyle.marginTopDp,
                valueRange = 0f..60f,
                steps = 11,
                format = { "${it.toInt()}dp" },
                onValueChange = onTitleMarginTopChange,
            )
            ReaderValueSlider(
                label = strings.reader.titleMarginBottom,
                value = prefs.titleStyle.marginBottomDp,
                valueRange = 0f..120f,
                steps = 11,
                format = { "${it.toInt()}dp" },
                onValueChange = onTitleMarginBottomChange,
            )
        }
    }

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    // ── 行为开关 ──
    ReaderSwitchRow(
        label = strings.reader.keepScreenOnLabel,
        checked = prefs.keepScreenOn,
        onCheckedChange = onKeepScreenOnChange,
        description = strings.reader.keepScreenOnDesc,
    )
    ReaderSwitchRow(
        label = strings.reader.volumeKeyLabel,
        checked = prefs.volumeKeyTurnPage,
        onCheckedChange = onVolumeKeyTurnPageChange,
        description = strings.reader.volumeKeyDesc,
    )
    ReaderSwitchRow(
        label = strings.reader.edgeTurnPageLabel,
        checked = prefs.edgeTurnPage,
        onCheckedChange = onEdgeTurnPageChange,
        description = strings.reader.edgeTurnPageDesc,
    )
    if (prefs.edgeTurnPage) {
        ReaderValueSlider(
            label = strings.reader.edgeWidthLabel,
            value = prefs.edgeWidthPercent,
            valueRange = 0.1f..0.4f,
            steps = 5,
            format = { "%.0f%%".format(it * 100) },
            onValueChange = onEdgeWidthPercentChange,
        )
    }

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    // ── TTS 朗读 ──
    var expandedTts by remember { mutableStateOf(false) }
    ExpandableSection(
        title = strings.tts.ttsSettings,
        expanded = expandedTts,
        onToggle = { expandedTts = !expandedTts },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val isPlaying = ttsState == com.shuli.reader.core.tts.TtsState.PLAYING
            val isPaused = ttsState == com.shuli.reader.core.tts.TtsState.PAUSED
            OutlinedButton(
                onClick = if (isPlaying) onTtsPause else onTtsStart,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isPlaying) strings.tts.ttsPause else if (isPaused) strings.tts.ttsStart else strings.tts.ttsStart)
            }
            if (isPlaying || isPaused) {
                OutlinedButton(onClick = onTtsStop) {
                    Text(strings.tts.ttsStop)
                }
            }
        }
        ReaderValueSlider(
            label = strings.tts.ttsSpeed,
            value = prefs.ttsSpeed,
            valueRange = 0.5f..2.0f,
            steps = 14,
            format = { "%.1fx".format(it) },
            onValueChange = onTtsSpeedChange,
        )
        ReaderValueSlider(
            label = strings.tts.ttsPitch,
            value = prefs.ttsPitch,
            valueRange = 0.5f..2.0f,
            steps = 14,
            format = { "%.1f".format(it) },
            onValueChange = onTtsPitchChange,
        )
    }

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 16.dp),
    )

    // ── 预设 ──
    Text(
        text = strings.reader.readingPresets,
        style = MaterialTheme.typography.titleSmall,
        color = readerColors.textPrimary,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(presets) { preset ->
            AssistChip(
                onClick = { onApplyPreset(preset.id) },
                label = { Text(preset.name) },
                modifier = Modifier.combinedClickable(
                    onClick = { onApplyPreset(preset.id) },
                    onLongClick = { showDeleteDialog = preset.id },
                ),
            )
        }
        item {
            AssistChip(
                onClick = { showSaveDialog = true },
                label = { Text(strings.reader.savePresetAction) },
            )
        }
    }

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 16.dp),
    )

    // 恢复默认按钮
    OutlinedButton(
        onClick = { showResetDialog = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(strings.reader.resetToDefault)
    }

    // ── 对话框 ──
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(strings.reader.resetToDefault) },
            text = { Text(strings.reader.resetToDefaultConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetToDefault()
                        showResetDialog = false
                    },
                ) { Text(strings.reader.confirmAction) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(strings.reader.cancelAction) }
            },
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(strings.reader.savePresetTitle) },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text(strings.reader.presetNameLabel) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetName.isNotBlank()) {
                            onSavePreset(presetName)
                            presetName = ""
                            showSaveDialog = false
                        }
                    },
                ) { Text(strings.reader.saveAction) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text(strings.reader.cancelAction) }
            },
        )
    }

    showDeleteDialog?.let { presetId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(strings.reader.deletePresetTitle) },
            text = { Text(strings.reader.deletePresetConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePreset(presetId)
                        showDeleteDialog = null
                    },
                ) { Text(strings.reader.deleteAction) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(strings.reader.cancelAction) }
            },
        )
    }
}
