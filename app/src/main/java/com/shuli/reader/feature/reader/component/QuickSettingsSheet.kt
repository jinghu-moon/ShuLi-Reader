package com.shuli.reader.feature.reader.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.core.reader.TitleAlign
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme

/**
 * Tab 索引
 */
private const val TAB_FONT_SIZE = 0
private const val TAB_FONT = 1
private const val TAB_MARGIN = 2
private const val TAB_DISPLAY = 3
private const val TAB_HEADER_FOOTER = 4
private const val TAB_MORE = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsSheet(
    uiState: ReaderUiState,
    onDismiss: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onIndentChange: (Float) -> Unit,
    onMarginVerticalChange: (Float) -> Unit,
    onMarginHorizontalChange: (Float) -> Unit,
    onReadingFontChange: (String) -> Unit,
    onPageAnimTypeChange: (PageAnimType) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    // 阶段三新增回调
    onLetterSpacingChange: (Float) -> Unit = {},
    onFontWeightChange: (ReaderFontWeight) -> Unit = {},
    onTextAlignChange: (ReaderTextAlign) -> Unit = {},
    onChineseConvertChange: (ChineseConvert) -> Unit = {},
    onUseZhLayoutChange: (Boolean) -> Unit = {},
    // 阶段四新增回调
    onApplyPreset: (Long) -> Unit = {},
    onSavePreset: (String) -> Unit = {},
    onRenamePreset: (Long, String) -> Unit = { _, _ -> },
    onDeletePreset: (Long) -> Unit = {},
    onResetToDefault: () -> Unit = {},
    // 阶段五新增回调：页眉脚
    onHeaderVisibilityChange: (HeaderVisibility) -> Unit = {},
    onHeaderLeftChange: (SlotContent) -> Unit = {},
    onHeaderCenterChange: (SlotContent) -> Unit = {},
    onHeaderRightChange: (SlotContent) -> Unit = {},
    onFooterVisibilityChange: (HeaderVisibility) -> Unit = {},
    onFooterLeftChange: (SlotContent) -> Unit = {},
    onFooterCenterChange: (SlotContent) -> Unit = {},
    onFooterRightChange: (SlotContent) -> Unit = {},
    onHeaderFooterAlphaChange: (Float) -> Unit = {},
    onShowProgressChange: (Boolean) -> Unit = {},
    // 阶段五新增回调：正文标题样式
    onTitleAlignChange: (TitleAlign) -> Unit = {},
    onTitleSizeOffsetChange: (Int) -> Unit = {},
    onTitleMarginTopChange: (Float) -> Unit = {},
    onTitleMarginBottomChange: (Float) -> Unit = {},
    // 阶段六新增回调
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onVolumeKeyTurnPageChange: (Boolean) -> Unit = {},
    onEdgeTurnPageChange: (Boolean) -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = readerColors.surface,
        contentColor = readerColors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = readerColors.textSecondary) },
    ) {
        // 高度约束放在内容包裹层（而非 ModalBottomSheet 自身），
        // 否则可滚动内容会让 sheet 吸附到屏顶 Expanded 锚点
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.62f)) {
        // 常驻亮度栏
        BrightnessBar(
            brightness = uiState.readerPreferences.brightness,
            onBrightnessChange = onBrightnessChange,
        )
        HorizontalDivider(color = readerColors.divider)

        // 6-Tab 分类（图标 + 短文案 + 横向滚动，避免文字换行）
        val tabIconSize = 20.dp
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = readerColors.surface,
            contentColor = readerColors.textPrimary,
            edgePadding = 8.dp,
            divider = {},
        ) {
            Tab(
                selected = selectedTab == 0, onClick = { selectedTab = 0 },
                icon = { Icon(Icons.Outlined.FormatSize, null, Modifier.size(tabIconSize)) },
                text = { Text("字号", maxLines = 1) },
            )
            Tab(
                selected = selectedTab == 1, onClick = { selectedTab = 1 },
                icon = { Icon(Icons.Outlined.FontDownload, null, Modifier.size(tabIconSize)) },
                text = { Text("字体", maxLines = 1) },
            )
            Tab(
                selected = selectedTab == 2, onClick = { selectedTab = 2 },
                icon = { Icon(Icons.Outlined.AspectRatio, null, Modifier.size(tabIconSize)) },
                text = { Text("边距", maxLines = 1) },
            )
            Tab(
                selected = selectedTab == 3, onClick = { selectedTab = 3 },
                icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(tabIconSize)) },
                text = { Text("翻页", maxLines = 1) },
            )
            Tab(
                selected = selectedTab == 4, onClick = { selectedTab = 4 },
                icon = { Icon(Icons.Outlined.ViewHeadline, null, Modifier.size(tabIconSize)) },
                text = { Text("页眉脚", maxLines = 1) },
            )
            Tab(
                selected = selectedTab == 5, onClick = { selectedTab = 5 },
                icon = { Icon(Icons.Outlined.Tune, null, Modifier.size(tabIconSize)) },
                text = { Text("更多", maxLines = 1) },
            )
        }

        // Tab 内容
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when (selectedTab) {
                TAB_FONT_SIZE -> FontSizePanel(
                    prefs = uiState.readerPreferences,
                    onFontSizeChange = onFontSizeChange,
                    onLetterSpacingChange = onLetterSpacingChange,
                    onLineSpacingChange = onLineSpacingChange,
                    onParagraphSpacingChange = onParagraphSpacingChange,
                    onIndentChange = onIndentChange,
                )
                TAB_FONT -> FontPanel(
                    prefs = uiState.readerPreferences,
                    onReadingFontChange = onReadingFontChange,
                    onFontWeightChange = onFontWeightChange,
                    onTextAlignChange = onTextAlignChange,
                    onChineseConvertChange = onChineseConvertChange,
                    onUseZhLayoutChange = onUseZhLayoutChange,
                )
                TAB_MARGIN -> MarginPanel(
                    prefs = uiState.readerPreferences,
                    onMarginVerticalChange = onMarginVerticalChange,
                    onMarginHorizontalChange = onMarginHorizontalChange,
                )
                TAB_DISPLAY -> DisplayPanel(
                    prefs = uiState.readerPreferences,
                    onPageAnimTypeChange = onPageAnimTypeChange,
                    onThemeChange = onThemeChange,
                    onHeaderVisibilityChange = onHeaderVisibilityChange,
                    onFooterVisibilityChange = onFooterVisibilityChange,
                    onShowProgressChange = onShowProgressChange,
                )
                TAB_HEADER_FOOTER -> HeaderFooterPanel(
                    prefs = uiState.readerPreferences,
                    onTitleAlignChange = onTitleAlignChange,
                    onTitleSizeOffsetChange = onTitleSizeOffsetChange,
                    onTitleMarginTopChange = onTitleMarginTopChange,
                    onTitleMarginBottomChange = onTitleMarginBottomChange,
                    onHeaderVisibilityChange = onHeaderVisibilityChange,
                    onHeaderLeftChange = onHeaderLeftChange,
                    onHeaderCenterChange = onHeaderCenterChange,
                    onHeaderRightChange = onHeaderRightChange,
                    onFooterVisibilityChange = onFooterVisibilityChange,
                    onFooterLeftChange = onFooterLeftChange,
                    onFooterCenterChange = onFooterCenterChange,
                    onFooterRightChange = onFooterRightChange,
                    onHeaderFooterAlphaChange = onHeaderFooterAlphaChange,
                    onShowProgressChange = onShowProgressChange,
                )
                TAB_MORE -> MorePanel(
                    prefs = uiState.readerPreferences,
                    presets = uiState.presets,
                    onApplyPreset = onApplyPreset,
                    onSavePreset = onSavePreset,
                    onRenamePreset = onRenamePreset,
                    onDeletePreset = onDeletePreset,
                    onResetToDefault = onResetToDefault,
                    onKeepScreenOnChange = onKeepScreenOnChange,
                    onVolumeKeyTurnPageChange = onVolumeKeyTurnPageChange,
                    onEdgeTurnPageChange = onEdgeTurnPageChange,
                )
            }
        }
        }  // 高度约束 Column 结束
    }

    // 切换 Tab 时滚动复位
    LaunchedEffect(selectedTab) { scrollState.scrollTo(0) }
}

// ── 亮度常驻栏 ──────────────────────────────────────────────

@Composable
private fun BrightnessBar(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    val isAuto = brightness < 0f
    ReaderSliderRow(
        label = "亮度",
        value = if (isAuto) 0.5f else brightness,
        valueRange = 0.01f..1f,
        steps = 0,
        format = { "${(it * 100).toInt()}%" },
        onValueChange = onBrightnessChange,
        showSlider = true,
        onValueClick = if (isAuto) {
            { onBrightnessChange(0.5f) }
        } else null,
    )
}

// ── Tab 0: 字号 ──────────────────────────────────────────────

@Composable
private fun FontSizePanel(
    prefs: ReaderPreferences,
    onFontSizeChange: (Float) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onIndentChange: (Float) -> Unit,
) {
    val strings = LocalAppStrings.current

    ReaderSliderRow(
        label = strings.defaultFontSize,
        value = prefs.fontSize,
        valueRange = 10f..32f,
        steps = 21,
        format = { "${it.toInt()}sp" },
        onValueChange = onFontSizeChange,
    )
    ReaderSliderRow(
        label = "字距",
        value = prefs.letterSpacing,
        valueRange = 0f..0.2f,
        steps = 19,
        format = { "%.2f".format(it) },
        onValueChange = onLetterSpacingChange,
    )
    ReaderSliderRow(
        label = strings.defaultLineSpacing,
        value = prefs.lineSpacing,
        valueRange = 0.8f..3.0f,
        steps = 21,
        format = { "%.1f".format(it) },
        onValueChange = onLineSpacingChange,
    )
    ReaderSliderRow(
        label = strings.paragraphSpacing,
        value = prefs.paragraphSpacing,
        valueRange = 0f..5.0f,
        steps = 49,
        format = { "%.1f".format(it) },
        onValueChange = onParagraphSpacingChange,
    )
    ReaderSliderRow(
        label = strings.firstLineIndent,
        value = prefs.indent,
        valueRange = 0f..10f,
        steps = 19,
        format = { "%.1f".format(it) },
        onValueChange = onIndentChange,
    )
}

// ── Tab 1: 字体 ──────────────────────────────────────────────

@Composable
private fun FontPanel(
    prefs: ReaderPreferences,
    onReadingFontChange: (String) -> Unit,
    onFontWeightChange: (ReaderFontWeight) -> Unit,
    onTextAlignChange: (ReaderTextAlign) -> Unit,
    onChineseConvertChange: (ChineseConvert) -> Unit,
    onUseZhLayoutChange: (Boolean) -> Unit = {},
) {
    val strings = LocalAppStrings.current

    ReaderFormPickerRow(
        label = strings.readingFont,
        options = listOf(
            "harmony" to strings.readingFontHarmony,
            "lxgw" to strings.readingFontLxgw,
            "system" to strings.readingFontSystem,
        ),
        selected = prefs.readingFont,
        onSelect = onReadingFontChange,
    )
    ReaderFormPickerRow(
        label = "字重",
        options = listOf(
            ReaderFontWeight.LIGHT to "细",
            ReaderFontWeight.NORMAL to "常规",
            ReaderFontWeight.MEDIUM to "中",
            ReaderFontWeight.BOLD to "粗",
        ),
        selected = prefs.fontWeight,
        onSelect = onFontWeightChange,
    )
    ReaderFormPickerRow(
        label = "对齐",
        options = listOf(
            ReaderTextAlign.LEFT to "左对齐",
            ReaderTextAlign.JUSTIFY to "两端",
        ),
        selected = prefs.textAlign,
        onSelect = onTextAlignChange,
    )
    ReaderFormPickerRow(
        label = "简繁",
        options = listOf(
            ChineseConvert.NONE to "原始",
            ChineseConvert.SIMPLIFIED to "简体",
            ChineseConvert.TRADITIONAL to "繁体",
        ),
        selected = prefs.chineseConvert,
        onSelect = onChineseConvertChange,
    )
    ReaderSwitchRow(
        label = "中文分行",
        checked = prefs.useZhLayout,
        onCheckedChange = onUseZhLayoutChange,
        description = "标点避头尾",
    )
}

// ── Tab 2: 边距 ──────────────────────────────────────────────

@Composable
private fun MarginPanel(
    prefs: ReaderPreferences,
    onMarginVerticalChange: (Float) -> Unit,
    onMarginHorizontalChange: (Float) -> Unit,
) {
    val strings = LocalAppStrings.current

    ReaderSliderRow(
        label = strings.marginTopBottom,
        value = prefs.marginVertical,
        valueRange = 0f..96f,
        steps = 23,
        format = { "${it.toInt()}dp" },
        onValueChange = onMarginVerticalChange,
    )
    ReaderSliderRow(
        label = strings.marginLeftRight,
        value = prefs.marginHorizontal,
        valueRange = 0f..64f,
        steps = 15,
        format = { "${it.toInt()}dp" },
        onValueChange = onMarginHorizontalChange,
    )
}

// ── Tab 3: 显示 ──────────────────────────────────────────────

@Composable
private fun DisplayPanel(
    prefs: ReaderPreferences,
    onPageAnimTypeChange: (PageAnimType) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onHeaderVisibilityChange: (HeaderVisibility) -> Unit = {},
    onFooterVisibilityChange: (HeaderVisibility) -> Unit = {},
    onShowProgressChange: (Boolean) -> Unit = {},
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current

    // 翻页动画
    ReaderFormPickerRow(
        label = strings.defaultPageAnim,
        options = listOf(
            PageAnimType.NONE to strings.pageAnimNone,
            PageAnimType.COVER to strings.pageAnimOverlay,
            PageAnimType.HORIZONTAL to strings.pageAnimSlide,
            PageAnimType.SIMULATION to strings.pageAnimSimulation,
            PageAnimType.SCROLL to strings.pageAnimFade,
        ),
        selected = prefs.pageAnimType,
        onSelect = onPageAnimTypeChange,
    )

    // 主题色块
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.appearance,
            modifier = Modifier.width(SETTINGS_LABEL_WIDTH),
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textPrimary,
        )
        ReaderTheme.entries.forEach { theme ->
            val isSelected = prefs.backgroundColor == theme
            val themeColors = theme.toReaderColorScheme().toCanvasThemeColors()
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(36.dp)
                    .then(
                        if (isSelected) Modifier.padding(2.dp) else Modifier
                    ),
            ) {
                drawCircle(color = androidx.compose.ui.graphics.Color(themeColors.backgroundColor))
                if (isSelected) {
                    drawCircle(
                        color = readerColors.accent,
                        radius = size.minDimension / 2,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                    )
                }
            }
        }
    }

    // 页眉/页脚/进度条开关
    ReaderSwitchRow(
        label = "页眉",
        checked = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE,
        onCheckedChange = { onHeaderVisibilityChange(if (it) HeaderVisibility.HIDE_WHEN_STATUS_BAR else HeaderVisibility.ALWAYS_HIDE) },
    )
    ReaderSwitchRow(
        label = "页脚",
        checked = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE,
        onCheckedChange = { onFooterVisibilityChange(if (it) HeaderVisibility.ALWAYS_SHOW else HeaderVisibility.ALWAYS_HIDE) },
    )
    ReaderSwitchRow(
        label = "进度条",
        checked = prefs.showProgress,
        onCheckedChange = onShowProgressChange,
    )
}

// ── Tab 4: 页眉脚 ──────────────────────────────────────────────

private val SLOT_OPTIONS = listOf(
    SlotContent.NONE to "无",
    SlotContent.CHAPTER_TITLE to "章节名",
    SlotContent.BOOK_TITLE to "书名",
    SlotContent.PAGE_NUMBER to "页码",
    SlotContent.PROGRESS to "进度",
    SlotContent.TIME to "时间",
    SlotContent.BATTERY to "电量",
    SlotContent.DATE to "日期",
)

@Composable
private fun HeaderFooterPanel(
    prefs: ReaderPreferences,
    onTitleAlignChange: (TitleAlign) -> Unit,
    onTitleSizeOffsetChange: (Int) -> Unit,
    onTitleMarginTopChange: (Float) -> Unit,
    onTitleMarginBottomChange: (Float) -> Unit,
    onHeaderVisibilityChange: (HeaderVisibility) -> Unit,
    onHeaderLeftChange: (SlotContent) -> Unit,
    onHeaderCenterChange: (SlotContent) -> Unit,
    onHeaderRightChange: (SlotContent) -> Unit,
    onFooterVisibilityChange: (HeaderVisibility) -> Unit,
    onFooterLeftChange: (SlotContent) -> Unit,
    onFooterCenterChange: (SlotContent) -> Unit,
    onFooterRightChange: (SlotContent) -> Unit,
    onHeaderFooterAlphaChange: (Float) -> Unit,
    onShowProgressChange: (Boolean) -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current

    // 正文标题样式（仅章首页生效）
    Text(
        text = "正文标题",
        style = MaterialTheme.typography.titleSmall,
        color = readerColors.textPrimary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    ReaderFormPickerRow(
        label = "对齐",
        options = listOf(
            TitleAlign.LEFT to "靠左",
            TitleAlign.CENTER to "居中",
            TitleAlign.HIDDEN to "隐藏",
        ),
        selected = prefs.titleStyle.align,
        onSelect = onTitleAlignChange,
    )
    if (prefs.titleStyle.align != TitleAlign.HIDDEN) {
        ReaderSliderRow(
            label = "字号偏移",
            value = prefs.titleStyle.sizeOffsetSp.toFloat(),
            valueRange = 0f..16f,
            steps = 15,
            format = { "+${it.toInt()}sp" },
            onValueChange = { onTitleSizeOffsetChange(it.toInt()) },
        )
        ReaderSliderRow(
            label = "上距",
            value = prefs.titleStyle.marginTopDp,
            valueRange = 0f..60f,
            steps = 11,
            format = { "${it.toInt()}dp" },
            onValueChange = onTitleMarginTopChange,
        )
        ReaderSliderRow(
            label = "下距",
            value = prefs.titleStyle.marginBottomDp,
            valueRange = 0f..120f,
            steps = 11,
            format = { "${it.toInt()}dp" },
            onValueChange = onTitleMarginBottomChange,
        )
    }

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    // 页眉配置
    Text(
        text = "页眉",
        style = MaterialTheme.typography.titleSmall,
        color = readerColors.textPrimary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    ReaderFormPickerRow(
        label = "显示",
        options = listOf(
            HeaderVisibility.HIDE_WHEN_STATUS_BAR to "跟随状态栏",
            HeaderVisibility.ALWAYS_SHOW to "常显",
            HeaderVisibility.ALWAYS_HIDE to "隐藏",
        ),
        selected = prefs.header.visibility,
        onSelect = onHeaderVisibilityChange,
    )
    if (prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE) {
        ReaderFormPickerRow(
            label = "左",
            options = SLOT_OPTIONS,
            selected = prefs.header.left,
            onSelect = onHeaderLeftChange,
            sheetTitle = "页眉左",
        )
        ReaderFormPickerRow(
            label = "中",
            options = SLOT_OPTIONS,
            selected = prefs.header.center,
            onSelect = onHeaderCenterChange,
            sheetTitle = "页眉中",
        )
        ReaderFormPickerRow(
            label = "右",
            options = SLOT_OPTIONS,
            selected = prefs.header.right,
            onSelect = onHeaderRightChange,
            sheetTitle = "页眉右",
        )
    }

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    // 页脚配置
    Text(
        text = "页脚",
        style = MaterialTheme.typography.titleSmall,
        color = readerColors.textPrimary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    ReaderFormPickerRow(
        label = "显示",
        options = listOf(
            HeaderVisibility.ALWAYS_SHOW to "常显",
            HeaderVisibility.HIDE_WHEN_STATUS_BAR to "跟随状态栏",
            HeaderVisibility.ALWAYS_HIDE to "隐藏",
        ),
        selected = prefs.footer.visibility,
        onSelect = onFooterVisibilityChange,
    )
    if (prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE) {
        ReaderFormPickerRow(
            label = "左",
            options = SLOT_OPTIONS,
            selected = prefs.footer.left,
            onSelect = onFooterLeftChange,
            sheetTitle = "页脚左",
        )
        ReaderFormPickerRow(
            label = "中",
            options = SLOT_OPTIONS,
            selected = prefs.footer.center,
            onSelect = onFooterCenterChange,
            sheetTitle = "页脚中",
        )
        ReaderFormPickerRow(
            label = "右",
            options = SLOT_OPTIONS,
            selected = prefs.footer.right,
            onSelect = onFooterRightChange,
            sheetTitle = "页脚右",
        )
    }

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    // 通用设置
    ReaderSliderRow(
        label = "透明度",
        value = prefs.headerFooterAlpha,
        valueRange = 0.1f..1.0f,
        steps = 8,
        format = { "%.0f%%".format(it * 100) },
        onValueChange = onHeaderFooterAlphaChange,
    )
    ReaderSwitchRow(
        label = "进度条",
        checked = prefs.showProgress,
        onCheckedChange = onShowProgressChange,
    )
}

// ── Tab 5: 更多 ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MorePanel(
    prefs: ReaderPreferences,
    presets: List<com.shuli.reader.core.database.entity.ReaderPresetEntity>,
    onApplyPreset: (Long) -> Unit,
    onSavePreset: (String) -> Unit,
    onRenamePreset: (Long, String) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onResetToDefault: () -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onVolumeKeyTurnPageChange: (Boolean) -> Unit = {},
    onEdgeTurnPageChange: (Boolean) -> Unit = {},
) {
    val readerColors = LocalReaderColorScheme.current
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Long?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    // 预设区域
    Text(
        text = "阅读预设",
        style = MaterialTheme.typography.titleSmall,
        color = readerColors.textPrimary,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    // 预设 chip 行
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
                label = { Text("＋ 保存当前") },
            )
        }
    }

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 16.dp),
    )

    // 阶段六：杂项开关
    ReaderSwitchRow(
        label = "屏幕常亮",
        checked = prefs.keepScreenOn,
        onCheckedChange = onKeepScreenOnChange,
        description = "阅读时保持屏幕常亮",
    )
    ReaderSwitchRow(
        label = "音量键",
        checked = prefs.volumeKeyTurnPage,
        onCheckedChange = onVolumeKeyTurnPageChange,
        description = "音量 +/- 键翻页",
    )
    ReaderSwitchRow(
        label = "边缘翻页",
        checked = prefs.edgeTurnPage,
        onCheckedChange = onEdgeTurnPageChange,
        description = "点击屏幕左右边缘翻页",
    )

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 16.dp),
    )

    // 恢复默认按钮（带确认对话框）
    OutlinedButton(
        onClick = { showResetDialog = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("恢复默认设置")
    }

    // 恢复默认确认对话框
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复默认设置") },
            text = { Text("确定要将所有阅读设置恢复为默认值吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetToDefault()
                        showResetDialog = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    // 保存预设对话框
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存预设") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("预设名称") },
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
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    // 删除预设对话框
    showDeleteDialog?.let { presetId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除预设") },
            text = { Text("确定要删除这个预设吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePreset(presetId)
                        showDeleteDialog = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            },
        )
    }
}
