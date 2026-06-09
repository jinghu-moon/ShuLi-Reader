package com.shuli.reader.feature.reader.component.quicksettings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.core.reader.TitleAlign
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme

/**
 * Tab 索引（4 Tab 结构）
 */
internal const val TAB_LAYOUT = 0       // 排版
internal const val TAB_FONT = 1         // 字体
internal const val TAB_PAGE = 2         // 页面
internal const val TAB_INTERACTION = 3  // 交互

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
 * 常驻主题色块行（亮度栏下方）
 */
@Composable
internal fun ThemeColorRow(
    currentTheme: ReaderTheme,
    onThemeChange: (ReaderTheme) -> Unit,
    customBackgroundColor: Int? = null,
) {
    val readerColors = LocalReaderColorScheme.current
    // 只缓存非 CUSTOM 的内置主题
    val themeColorMap = remember {
        ReaderTheme.entries
            .filter { it != ReaderTheme.CUSTOM }
            .associateWith { it.toReaderColorScheme().toCanvasThemeColors() }
    }
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.common.readerThemeLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = readerColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 内置主题圆点
            ReaderTheme.entries.filter { it != ReaderTheme.CUSTOM }.forEach { theme ->
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
                    val innerRadius = if (isSelected) centerRadius - strokeWidth - gap else centerRadius - 2.dp.toPx()
                    if (!isSelected) {
                        drawCircle(
                            color = readerColors.textSecondary.copy(alpha = 0.1f),
                            radius = innerRadius,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                    drawCircle(
                        color = Color(themeColors.backgroundColor),
                        radius = innerRadius,
                    )
                    if (isSelected) {
                        drawCircle(
                            color = readerColors.accent,
                            radius = centerRadius - strokeWidth / 2,
                            style = Stroke(width = strokeWidth),
                        )
                    }
                }
            }
            // 自定义主题圆点（显示用户自定义色或默认渐变）
            val isCustomSelected = currentTheme == ReaderTheme.CUSTOM
            val customColor = customBackgroundColor?.let { Color(it) }
                ?: readerColors.accent.copy(alpha = 0.3f)
            Canvas(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onThemeChange(ReaderTheme.CUSTOM) },
            ) {
                val strokeWidth = 2.dp.toPx()
                val gap = 3.dp.toPx()
                val centerRadius = size.minDimension / 2
                val innerRadius = if (isCustomSelected) centerRadius - strokeWidth - gap else centerRadius - 2.dp.toPx()
                if (!isCustomSelected) {
                    drawCircle(
                        color = readerColors.textSecondary.copy(alpha = 0.1f),
                        radius = innerRadius,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                drawCircle(color = customColor, radius = innerRadius)
                // 自定义图标：画一个小 "+" 号
                val plusSize = innerRadius * 0.4f
                val center = this.center
                drawLine(
                    color = readerColors.textPrimary.copy(alpha = 0.6f),
                    start = center.copy(x = center.x - plusSize),
                    end = center.copy(x = center.x + plusSize),
                    strokeWidth = 2.dp.toPx(),
                )
                drawLine(
                    color = readerColors.textPrimary.copy(alpha = 0.6f),
                    start = center.copy(y = center.y - plusSize),
                    end = center.copy(y = center.y + plusSize),
                    strokeWidth = 2.dp.toPx(),
                )
                if (isCustomSelected) {
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

/**
 * 可折叠区块组件
 */
@Composable
internal fun ExpandableSection(
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

/**
 * 自定义主题颜色选择面板（当 CUSTOM 主题激活时显示）
 *
 * 提供背景色、正文色、强调色三行色块，
 * 用户点击即刻应用到阅读器。
 */
@Composable
internal fun CustomThemePanel(
    currentBg: Int?,
    currentText: Int?,
    currentAccent: Int?,
    onColorChange: (bg: Int?, text: Int?, accent: Int?) -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    val strings = LocalAppStrings.current

    // 预定义调色板
    val bgColors = listOf(
        0xFFF6F4F0.toInt(),  // 纸质（默认）
        0xFFFFFFFF.toInt(),  // 纯白
        0xFFF5F0E8.toInt(),  // 米黄
        0xFFE8F0E5.toInt(),  // 浅绿
        0xFFE5ECF0.toInt(),  // 浅蓝
        0xFFF0E8E5.toInt(),  // 浅粉
        0xFF2C231A.toInt(),  // 深棕
        0xFF1A1A2E.toInt(),  // 深蓝
        0xFF1A1A1A.toInt(),  // 纯黑
    )
    val textColors = listOf(
        0xFF453B2E.toInt(),  // 深棕（默认）
        0xFF212121.toInt(),  // 近黑
        0xFF37474F.toInt(),  // 深蓝灰
        0xFF2E4A3E.toInt(),  // 深绿
        0xFF4A3728.toInt(),  // 褐色
        0xFFD4CCC0.toInt(),  // 浅灰（深色背景用）
        0xFFE0E0E0.toInt(),  // 浅灰白
        0xFFC5CAE9.toInt(),  // 浅蓝紫
    )
    val accentColors = listOf(
        0xFF6B5B4E.toInt(),  // 棕色（默认）
        0xFF1976D2.toInt(),  // 蓝色
        0xFF388E3C.toInt(),  // 绿色
        0xFFD32F2F.toInt(),  // 红色
        0xFF7B1FA2.toInt(),  // 紫色
        0xFFFF8F00.toInt(),  // 琥珀
        0xFF00838F.toInt(),  // 青色
        0xFF5D4037.toInt(),  // 深棕
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 标题
        Text(
            text = strings.reader.customThemeLabel,
            style = MaterialTheme.typography.titleSmall,
            color = readerColors.textPrimary,
        )
        // 背景色
        ColorSwatchRow(
            label = strings.reader.customThemeBg,
            colors = bgColors,
            selectedColor = currentBg ?: 0xFFF6F4F0.toInt(),
            onSelect = { onColorChange(it, currentText, currentAccent) },
        )
        // 正文色
        ColorSwatchRow(
            label = strings.reader.customThemeText,
            colors = textColors,
            selectedColor = currentText ?: 0xFF453B2E.toInt(),
            onSelect = { onColorChange(currentBg, it, currentAccent) },
        )
        // 强调色
        ColorSwatchRow(
            label = strings.reader.customThemeAccent,
            colors = accentColors,
            selectedColor = currentAccent ?: 0xFF6B5B4E.toInt(),
            onSelect = { onColorChange(currentBg, currentText, it) },
        )
    }
}

/**
 * 单行颜色色块选择器
 */
@Composable
private fun ColorSwatchRow(
    label: String,
    colors: List<Int>,
    selectedColor: Int,
    onSelect: (Int) -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textSecondary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            colors.forEach { colorInt ->
                val isSelected = colorInt == selectedColor
                Canvas(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onSelect(colorInt) },
                ) {
                    val strokeWidth = 2.dp.toPx()
                    val gap = 2.dp.toPx()
                    val centerRadius = size.minDimension / 2
                    val innerRadius = if (isSelected) centerRadius - strokeWidth - gap else centerRadius - 1.dp.toPx()
                    if (!isSelected) {
                        drawCircle(
                            color = readerColors.textSecondary.copy(alpha = 0.1f),
                            radius = innerRadius,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                    drawCircle(
                        color = Color(colorInt),
                        radius = innerRadius,
                    )
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

/**
 * 页眉/页脚槽位选项列表
 */
@Composable
internal fun slotOptions() = listOf(
    SlotContent.NONE to LocalAppStrings.current.reader.slotNone,
    SlotContent.CHAPTER_TITLE to LocalAppStrings.current.reader.slotChapterTitle,
    SlotContent.BOOK_TITLE to LocalAppStrings.current.reader.slotBookTitle,
    SlotContent.CHAPTER_PROGRESS_FRACTION to LocalAppStrings.current.reader.slotChapterProgressFraction,
    SlotContent.CHAPTER_PROGRESS_PERCENT to LocalAppStrings.current.reader.slotChapterProgressPercent,
    SlotContent.BOOK_PROGRESS_FRACTION to LocalAppStrings.current.reader.slotBookProgressFraction,
    SlotContent.BOOK_PROGRESS_PERCENT to LocalAppStrings.current.reader.slotBookProgressPercent,
    SlotContent.TIME to LocalAppStrings.current.reader.slotTime,
    SlotContent.BATTERY to LocalAppStrings.current.reader.slotBattery,
    SlotContent.DATE to LocalAppStrings.current.reader.slotDate,
)
