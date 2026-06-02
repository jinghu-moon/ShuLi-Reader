package com.shuli.reader.feature.reader.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Immutable
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
import com.shuli.reader.core.font.FontManager

/**
 * 快捷设置面板的所有回调动作
 *
 * 使用 @Immutable 标注，Compose 可将其视为稳定对象，
 * 避免因 lambda 重建导致不必要的重组。
 */
@Immutable
data class QuickSettingsActions(
    val onDismiss: () -> Unit = {},
    val onBrightnessChange: (Float) -> Unit = {},
    val onFontSizeChange: (Float) -> Unit = {},
    val onLineSpacingChange: (Float) -> Unit = {},
    val onParagraphSpacingChange: (Float) -> Unit = {},
    val onIndentChange: (Float) -> Unit = {},
    val onMarginVerticalChange: (Float) -> Unit = {},
    val onMarginHorizontalChange: (Float) -> Unit = {},
    val onReadingFontChange: (String) -> Unit = {},
    val onPageAnimTypeChange: (PageAnimType) -> Unit = {},
    val onThemeChange: (ReaderTheme) -> Unit = {},
    val onLetterSpacingChange: (Float) -> Unit = {},
    val onFontWeightChange: (ReaderFontWeight) -> Unit = {},
    val onTextAlignChange: (ReaderTextAlign) -> Unit = {},
    val onChineseConvertChange: (ChineseConvert) -> Unit = {},
    val onUseZhLayoutChange: (Boolean) -> Unit = {},
    val onPanguSpacingChange: (Boolean) -> Unit = {},
    val onApplyPreset: (Long) -> Unit = {},
    val onSavePreset: (String) -> Unit = {},
    val onRenamePreset: (Long, String) -> Unit = { _, _ -> },
    val onDeletePreset: (Long) -> Unit = {},
    val onResetToDefault: () -> Unit = {},
    val onHeaderVisibilityChange: (HeaderVisibility) -> Unit = {},
    val onHeaderLeftChange: (SlotContent) -> Unit = {},
    val onHeaderCenterChange: (SlotContent) -> Unit = {},
    val onHeaderRightChange: (SlotContent) -> Unit = {},
    val onFooterVisibilityChange: (HeaderVisibility) -> Unit = {},
    val onFooterLeftChange: (SlotContent) -> Unit = {},
    val onFooterCenterChange: (SlotContent) -> Unit = {},
    val onFooterRightChange: (SlotContent) -> Unit = {},
    val onHeaderFooterAlphaChange: (Float) -> Unit = {},
    val onHeaderMarginTopChange: (Float) -> Unit = {},
    val onFooterMarginBottomChange: (Float) -> Unit = {},
    val onShowProgressChange: (Boolean) -> Unit = {},
    val onTitleAlignChange: (TitleAlign) -> Unit = {},
    val onTitleSizeOffsetChange: (Int) -> Unit = {},
    val onTitleMarginTopChange: (Float) -> Unit = {},
    val onTitleMarginBottomChange: (Float) -> Unit = {},
    val onKeepScreenOnChange: (Boolean) -> Unit = {},
    val onVolumeKeyTurnPageChange: (Boolean) -> Unit = {},
    val onEdgeTurnPageChange: (Boolean) -> Unit = {},
    val onEdgeWidthPercentChange: (Float) -> Unit = {},
    val onShowHeaderLineChange: (Boolean) -> Unit = {},
    val onShowFooterLineChange: (Boolean) -> Unit = {},
    val onHeaderFontSizeRatioChange: (Float) -> Unit = {},
    val onFooterFontSizeRatioChange: (Float) -> Unit = {},
    val onBottomJustifyChange: (Boolean) -> Unit = {},
    val ttsState: com.shuli.reader.core.tts.TtsState = com.shuli.reader.core.tts.TtsState.IDLE,
    val onTtsStart: () -> Unit = {},
    val onTtsPause: () -> Unit = {},
    val onTtsStop: () -> Unit = {},
    val onTtsSpeedChange: (Float) -> Unit = {},
    val onTtsPitchChange: (Float) -> Unit = {},
    val onImportFont: (android.net.Uri) -> Unit = {},
    val onDeleteFont: (String) -> Unit = {},
)

/**
 * Tab 索引（3 Tab 结构）
 */
private const val TAB_LAYOUT = 0   // 排版
private const val TAB_STYLE = 1    // 样式
private const val TAB_SETTINGS = 2 // 设置

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsSheet(
    uiState: ReaderUiState,
    actions: QuickSettingsActions,
) {
    val readerColors = LocalReaderColorScheme.current
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = sheetState,
        containerColor = readerColors.surface,
        contentColor = readerColors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = readerColors.textSecondary) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.62f)) {
            // 常驻：主题色块
            ThemeColorRow(
                currentTheme = uiState.readerPreferences.backgroundColor,
                onThemeChange = actions.onThemeChange,
            )

            HorizontalDivider(color = readerColors.divider)

            // 3-Tab 分类（纯文字，固定宽度不滚动）
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = readerColors.surface,
                contentColor = readerColors.textPrimary,
                divider = {},
            ) {
                listOf(strings.layoutTab, strings.styleTab, strings.settingsTab).forEachIndexed { index, title ->
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

// ── 常驻组件 ──────────────────────────────────────────────

/**
 * 常驻主题色块行（亮度栏下方）
 */
@Composable
private fun ThemeColorRow(
    currentTheme: ReaderTheme,
    onThemeChange: (ReaderTheme) -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    val themeColorMap = remember {
        ReaderTheme.entries.associateWith {
            it.toReaderColorScheme().toCanvasThemeColors()
        }
    }
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧说明文本
        Text(
            text = strings.readerThemeLabel,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = readerColors.textPrimary,
            modifier = Modifier.weight(1f),
        )

        // 右侧色块列表
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
        ReaderTheme.entries.forEach { theme ->
            val isSelected = currentTheme == theme
            val themeColors = themeColorMap[theme]!!
            Canvas(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(36.dp)
                    .clickable { onThemeChange(theme) },
            ) {
                val strokeWidth = 2.dp.toPx()
                val gap = 3.dp.toPx()
                val centerRadius = size.minDimension / 2
                
                // 选中时的内圈半径稍小，以留出间隔
                val innerRadius = if (isSelected) centerRadius - strokeWidth - gap else centerRadius - 2.dp.toPx()
                
                // 未选中时，添加一个极淡的边框，防止白色主题与背景混在一起
                if (!isSelected) {
                    drawCircle(
                        color = readerColors.textSecondary.copy(alpha = 0.1f),
                        radius = innerRadius,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                
                // 绘制内侧主题色块
                drawCircle(
                    color = Color(themeColors.backgroundColor),
                    radius = innerRadius
                )
                
                // 选中时，绘制带有间隙的强调色外轮廓
                if (isSelected) {
                    drawCircle(
                        color = readerColors.accent,
                        radius = centerRadius - strokeWidth / 2,
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
        }
        }
    }
}

// ── 可折叠区块组件 ──────────────────────────────────────────────

@Composable
private fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = readerColors.textPrimary,
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = readerColors.textSecondary,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column { content() }
        }
    }
}

// ── Tab 1: 排版 ──────────────────────────────────────────────

@Composable
private fun LayoutPanel(
    prefs: ReaderPreferences,
    onFontSizeChange: (Float) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onIndentChange: (Float) -> Unit,
    onMarginVerticalChange: (Float) -> Unit,
    onMarginHorizontalChange: (Float) -> Unit,
) {
    val strings = LocalAppStrings.current

    // 字号
    ReaderValueSlider(
        label = strings.defaultFontSize,
        value = prefs.fontSize,
        valueRange = 10f..32f,
        steps = 21,
        format = { "${it.toInt()}sp" },
        onValueChange = onFontSizeChange,
    )
    // 行距
    ReaderValueSlider(
        label = strings.defaultLineSpacing,
        value = prefs.lineSpacing,
        valueRange = 1.0f..3.0f,
        steps = 19,
        format = { "%.1f".format(it) },
        onValueChange = onLineSpacingChange,
    )
    // 边距（合并自原"边距"Tab）
    ReaderValueSlider(
        label = strings.marginTopBottom,
        value = prefs.marginVertical,
        valueRange = 0f..96f,
        steps = 23,
        format = { "${it.toInt()}dp" },
        onValueChange = onMarginVerticalChange,
    )
    ReaderValueSlider(
        label = strings.marginLeftRight,
        value = prefs.marginHorizontal,
        valueRange = 0f..64f,
        steps = 15,
        format = { "${it.toInt()}dp" },
        onValueChange = onMarginHorizontalChange,
    )
    // 段距
    ReaderValueSlider(
        label = strings.paragraphSpacing,
        value = prefs.paragraphSpacing,
        valueRange = 0.5f..3.0f,
        steps = 24,
        format = { "%.1f".format(it) },
        onValueChange = onParagraphSpacingChange,
    )
    // 缩进
    ReaderValueSlider(
        label = strings.firstLineIndent,
        value = prefs.indent,
        valueRange = 0f..10f,
        steps = 19,
        format = { "%.1f".format(it) },
        onValueChange = onIndentChange,
    )
    // 字距
    ReaderValueSlider(
        label = strings.letterSpacingLabel,
        value = prefs.letterSpacing,
        valueRange = 0f..0.2f,
        steps = 19,
        format = { "%.2f".format(it) },
        onValueChange = onLetterSpacingChange,
    )
}

// ── Tab 2: 样式 ──────────────────────────────────────────────

@Composable
private fun StylePanel(
    prefs: ReaderPreferences,
    customFonts: List<com.shuli.reader.core.font.FontManager.FontEntry>,
    onImportFont: (android.net.Uri) -> Unit,
    onDeleteFont: (String) -> Unit,
    onPageAnimTypeChange: (PageAnimType) -> Unit,
    onReadingFontChange: (String) -> Unit,
    onFontWeightChange: (ReaderFontWeight) -> Unit,
    onTextAlignChange: (ReaderTextAlign) -> Unit,
    onChineseConvertChange: (ChineseConvert) -> Unit,
    onUseZhLayoutChange: (Boolean) -> Unit,
    onPanguSpacingChange: (Boolean) -> Unit,
    onBottomJustifyChange: (Boolean) -> Unit,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    android.util.Log.d("FontManager", "StylePanel recomposed: customFonts.size=${customFonts.size}")
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        android.util.Log.d("FontManager", "OpenDocument 回调: uri=$uri")
        uri?.let { onImportFont(it) }
    }

    // 翻页动画（提到最顶部）
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

    // 字体
    val fontOptions = buildList {
        add("harmony" to strings.readingFontHarmony)
        add("system" to strings.readingFontSystem)
        customFonts.forEach { entry ->
            add(entry.key to entry.name)
        }
    }
    // 为每个自定义字体构建文件映射，用于字体选择面板中以对应字体样式显示名称
    val fontFileMap = remember(customFonts) {
        customFonts.associate { entry -> entry.key to entry.file }
    }
    ReaderFormPickerRow(
        label = strings.readingFont,
        options = fontOptions,
        selected = prefs.readingFont,
        onSelect = onReadingFontChange,
        fontFiles = fontFileMap,
    )
    // 导入字体按钮
    androidx.compose.material3.TextButton(
        onClick = { launcher.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) },
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(strings.importFont, style = MaterialTheme.typography.bodySmall)
    }
    // 已导入字体列表（可删除）
    if (customFonts.isNotEmpty()) {
        Column(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
            customFonts.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = readerColors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "删除字体",
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onDeleteFont(entry.id) },
                        tint = readerColors.textSecondary,
                    )
                }
            }
        }
    }
    // 字重
    ReaderFormPickerRow(
        label = strings.fontWeightLabel,
        options = listOf(
            ReaderFontWeight.LIGHT to strings.fontWeightLight,
            ReaderFontWeight.NORMAL to strings.fontWeightNormal,
            ReaderFontWeight.MEDIUM to strings.fontWeightMedium,
            ReaderFontWeight.BOLD to strings.fontWeightBold,
        ),
        selected = prefs.fontWeight,
        onSelect = onFontWeightChange,
    )
    // 对齐
    ReaderFormPickerRow(
        label = strings.textAlignLabel,
        options = listOf(
            ReaderTextAlign.LEFT to strings.textAlignLeft,
            ReaderTextAlign.JUSTIFY to strings.textAlignJustify,
        ),
        selected = prefs.textAlign,
        onSelect = onTextAlignChange,
    )
    // 简繁转换
    ReaderFormPickerRow(
        label = strings.chineseConvertLabel,
        options = listOf(
            ChineseConvert.NONE to strings.chineseConvertNone,
            ChineseConvert.SIMPLIFIED to strings.chineseConvertSimplified,
            ChineseConvert.TRADITIONAL to strings.chineseConvertTraditional,
        ),
        selected = prefs.chineseConvert,
        onSelect = onChineseConvertChange,
    )
    // 自定义中文分行
    ReaderSwitchRow(
        label = strings.useZhLayoutLabel,
        checked = prefs.useZhLayout,
        onCheckedChange = onUseZhLayoutChange,
    )
    // 中英文间增加空格
    ReaderSwitchRow(
        label = strings.usePanguSpacingLabel,
        checked = prefs.usePanguSpacing,
        onCheckedChange = onPanguSpacingChange,
    )
    // 底部对齐
    ReaderSwitchRow(
        label = strings.bottomJustifyLabel,
        checked = prefs.bottomJustify,
        onCheckedChange = onBottomJustifyChange,
    )
}

// ── Tab 3: 设置 ──────────────────────────────────────────────

@Composable
private fun slotOptions() = listOf(
    SlotContent.NONE to LocalAppStrings.current.slotNone,
    SlotContent.CHAPTER_TITLE to LocalAppStrings.current.slotChapterTitle,
    SlotContent.BOOK_TITLE to LocalAppStrings.current.slotBookTitle,
    SlotContent.PAGE_NUMBER to LocalAppStrings.current.slotPageNumber,
    SlotContent.PROGRESS to LocalAppStrings.current.slotProgress,
    SlotContent.TIME to LocalAppStrings.current.slotTime,
    SlotContent.BATTERY to LocalAppStrings.current.slotBattery,
    SlotContent.DATE to LocalAppStrings.current.slotDate,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SettingsPanel(
    prefs: ReaderPreferences,
    presets: List<com.shuli.reader.core.database.entity.ReaderPresetEntity>,
    onHeaderVisibilityChange: (HeaderVisibility) -> Unit,
    onFooterVisibilityChange: (HeaderVisibility) -> Unit,
    onShowProgressChange: (Boolean) -> Unit,
    onHeaderFooterAlphaChange: (Float) -> Unit,
    onHeaderMarginTopChange: (Float) -> Unit,
    onFooterMarginBottomChange: (Float) -> Unit,
    onHeaderLeftChange: (SlotContent) -> Unit,
    onHeaderCenterChange: (SlotContent) -> Unit,
    onHeaderRightChange: (SlotContent) -> Unit,
    onFooterLeftChange: (SlotContent) -> Unit,
    onFooterCenterChange: (SlotContent) -> Unit,
    onFooterRightChange: (SlotContent) -> Unit,
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
        label = strings.headerLabel,
        checked = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE,
        onCheckedChange = { onHeaderVisibilityChange(if (it) HeaderVisibility.HIDE_WHEN_STATUS_BAR else HeaderVisibility.ALWAYS_HIDE) },
    )
    ReaderSwitchRow(
        label = strings.footerLabel,
        checked = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE,
        onCheckedChange = { onFooterVisibilityChange(if (it) HeaderVisibility.ALWAYS_SHOW else HeaderVisibility.ALWAYS_HIDE) },
    )
    ReaderSwitchRow(
        label = strings.progressBarLabel,
        checked = prefs.showProgress,
        onCheckedChange = onShowProgressChange,
    )
    ReaderValueSlider(
        label = strings.opacityLabel,
        value = prefs.headerFooterAlpha,
        valueRange = 0.1f..1.0f,
        steps = 8,
        format = { "%.0f%%".format(it * 100) },
        onValueChange = onHeaderFooterAlphaChange,
    )

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    // ── 页眉脚详细（可折叠） ──
    ExpandableSection(
        title = strings.headerFooterCustom,
        expanded = expandedHeaderFooter,
        onToggle = { expandedHeaderFooter = !expandedHeaderFooter },
    ) {
        // 页眉
        Text(
            text = strings.headerLabel,
            style = MaterialTheme.typography.labelMedium,
            color = readerColors.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        if (prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE) {
            ReaderFormPickerRow(
                label = strings.displayLabel,
                options = listOf(
                    HeaderVisibility.HIDE_WHEN_STATUS_BAR to strings.displayFollowStatusBar,
                    HeaderVisibility.ALWAYS_SHOW to strings.displayAlwaysShow,
                    HeaderVisibility.ALWAYS_HIDE to strings.displayAlwaysHide,
                ),
                selected = prefs.header.visibility,
                onSelect = onHeaderVisibilityChange,
            )
            ReaderFormPickerRow(
                label = strings.positionLeft,
                options = slotOptions(),
                selected = prefs.header.left,
                onSelect = onHeaderLeftChange,
                sheetTitle = strings.headerLeft,
            )
            ReaderFormPickerRow(
                label = strings.positionCenter,
                options = slotOptions(),
                selected = prefs.header.center,
                onSelect = onHeaderCenterChange,
                sheetTitle = strings.headerCenter,
            )
            ReaderFormPickerRow(
                label = strings.positionRight,
                options = slotOptions(),
                selected = prefs.header.right,
                onSelect = onHeaderRightChange,
                sheetTitle = strings.headerRight,
            )
            ReaderValueSlider(
                label = strings.headerMarginTop,
                value = prefs.header.marginTop,
                valueRange = 0f..100f,
                steps = 100,
                format = { "${it.toInt()}dp" },
                onValueChange = onHeaderMarginTopChange,
            )
            ReaderSwitchRow(
                label = strings.headerLineLabel,
                checked = prefs.showHeaderLine,
                onCheckedChange = onShowHeaderLineChange,
            )
            ReaderValueSlider(
                label = strings.headerFontSizeLabel,
                value = prefs.headerFontSizeRatio,
                valueRange = 0.5f..1.2f,
                steps = 6,
                format = { "%.0f%%".format(it * 100) },
                onValueChange = onHeaderFontSizeRatioChange,
            )
        } else {
            Text(
                text = strings.headerHidden,
                style = MaterialTheme.typography.bodySmall,
                color = readerColors.textSecondary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        // 页脚
        Text(
            text = strings.footerLabel,
            style = MaterialTheme.typography.labelMedium,
            color = readerColors.textSecondary,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        if (prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE) {
            ReaderFormPickerRow(
                label = strings.displayLabel,
                options = listOf(
                    HeaderVisibility.ALWAYS_SHOW to strings.displayAlwaysShow,
                    HeaderVisibility.HIDE_WHEN_STATUS_BAR to strings.displayFollowStatusBar,
                    HeaderVisibility.ALWAYS_HIDE to strings.displayAlwaysHide,
                ),
                selected = prefs.footer.visibility,
                onSelect = onFooterVisibilityChange,
            )
            ReaderFormPickerRow(
                label = strings.positionLeft,
                options = slotOptions(),
                selected = prefs.footer.left,
                onSelect = onFooterLeftChange,
                sheetTitle = strings.footerLeft,
            )
            ReaderFormPickerRow(
                label = strings.positionCenter,
                options = slotOptions(),
                selected = prefs.footer.center,
                onSelect = onFooterCenterChange,
                sheetTitle = strings.footerCenter,
            )
            ReaderFormPickerRow(
                label = strings.positionRight,
                options = slotOptions(),
                selected = prefs.footer.right,
                onSelect = onFooterRightChange,
                sheetTitle = strings.footerRight,
            )
            ReaderValueSlider(
                label = strings.footerMarginBottom,
                value = prefs.footer.marginBottom,
                valueRange = 0f..100f,
                steps = 100,
                format = { "${it.toInt()}dp" },
                onValueChange = onFooterMarginBottomChange,
            )
            ReaderSwitchRow(
                label = strings.footerLineLabel,
                checked = prefs.showFooterLine,
                onCheckedChange = onShowFooterLineChange,
            )
            ReaderValueSlider(
                label = strings.footerFontSizeLabel,
                value = prefs.footerFontSizeRatio,
                valueRange = 0.5f..1.2f,
                steps = 6,
                format = { "%.0f%%".format(it * 100) },
                onValueChange = onFooterFontSizeRatioChange,
            )
        } else {
            Text(
                text = strings.footerHidden,
                style = MaterialTheme.typography.bodySmall,
                color = readerColors.textSecondary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }

    HorizontalDivider(
        color = readerColors.divider,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    // ── 标题样式（可折叠） ──
    ExpandableSection(
        title = strings.titleStyleLabel,
        expanded = expandedTitleStyle,
        onToggle = { expandedTitleStyle = !expandedTitleStyle },
    ) {
        ReaderFormPickerRow(
            label = strings.textAlignLabel,
            options = listOf(
                TitleAlign.LEFT to strings.titleAlignLeft,
                TitleAlign.CENTER to strings.titleAlignCenter,
                TitleAlign.HIDDEN to strings.titleAlignHidden,
            ),
            selected = prefs.titleStyle.align,
            onSelect = onTitleAlignChange,
        )
        if (prefs.titleStyle.align != TitleAlign.HIDDEN) {
            ReaderValueSlider(
                label = strings.titleSizeOffset,
                value = prefs.titleStyle.sizeOffsetSp.toFloat(),
                valueRange = 0f..16f,
                steps = 15,
                format = { "+${it.toInt()}sp" },
                onValueChange = { onTitleSizeOffsetChange(it.toInt()) },
            )
            ReaderValueSlider(
                label = strings.titleMarginTop,
                value = prefs.titleStyle.marginTopDp,
                valueRange = 0f..60f,
                steps = 11,
                format = { "${it.toInt()}dp" },
                onValueChange = onTitleMarginTopChange,
            )
            ReaderValueSlider(
                label = strings.titleMarginBottom,
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
        label = strings.keepScreenOnLabel,
        checked = prefs.keepScreenOn,
        onCheckedChange = onKeepScreenOnChange,
        description = strings.keepScreenOnDesc,
    )
    ReaderSwitchRow(
        label = strings.volumeKeyLabel,
        checked = prefs.volumeKeyTurnPage,
        onCheckedChange = onVolumeKeyTurnPageChange,
        description = strings.volumeKeyDesc,
    )
    ReaderSwitchRow(
        label = strings.edgeTurnPageLabel,
        checked = prefs.edgeTurnPage,
        onCheckedChange = onEdgeTurnPageChange,
        description = strings.edgeTurnPageDesc,
    )
    if (prefs.edgeTurnPage) {
        ReaderValueSlider(
            label = strings.edgeWidthLabel,
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
        title = strings.ttsSettings,
        expanded = expandedTts,
        onToggle = { expandedTts = !expandedTts },
    ) {
        // TTS 控制按钮
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
                Text(if (isPlaying) strings.ttsPause else if (isPaused) strings.ttsStart else strings.ttsStart)
            }
            if (isPlaying || isPaused) {
                OutlinedButton(onClick = onTtsStop) {
                    Text(strings.ttsStop)
                }
            }
        }
        // 语速
        ReaderValueSlider(
            label = strings.ttsSpeed,
            value = prefs.ttsSpeed,
            valueRange = 0.5f..2.0f,
            steps = 14,
            format = { "%.1fx".format(it) },
            onValueChange = onTtsSpeedChange,
        )
        // 音调
        ReaderValueSlider(
            label = strings.ttsPitch,
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
        text = strings.readingPresets,
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
                label = { Text(strings.savePresetAction) },
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
        Text(strings.resetToDefault)
    }

    // ── 对话框 ──
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(strings.resetToDefault) },
            text = { Text(strings.resetToDefaultConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetToDefault()
                        showResetDialog = false
                    },
                ) { Text(strings.confirmAction) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(strings.cancelAction) }
            },
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(strings.savePresetTitle) },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text(strings.presetNameLabel) },
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
                ) { Text(strings.saveAction) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text(strings.cancelAction) }
            },
        )
    }

    showDeleteDialog?.let { presetId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(strings.deletePresetTitle) },
            text = { Text(strings.deletePresetConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePreset(presetId)
                        showDeleteDialog = null
                    },
                ) { Text(strings.deleteAction) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(strings.cancelAction) }
            },
        )
    }
}
