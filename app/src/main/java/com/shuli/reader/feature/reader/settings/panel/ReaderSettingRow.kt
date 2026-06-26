package com.shuli.reader.feature.reader.settings.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.shuli.reader.feature.reader.settings.panel.controls.InkSelect
import com.shuli.reader.feature.reader.settings.panel.controls.InkToggle
import com.shuli.reader.feature.reader.settings.panel.controls.SegmentedControl
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp

/**
 * 标准设置行（对应原型 .setting-row）。
 *
 * 左侧标题(可选副标题)，右侧 [trailing] 控件槽。可选顶部分隔线，用于卡片内多行分隔。
 *
 * @param label 行标题
 * @param sublabel 行副标题（可选）
 * @param topDivider 是否在行顶部绘制分隔线（卡片内非首行传 true）
 * @param trailing 右侧控件
 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    topDivider: Boolean = false,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    val colors = LocalReaderColorScheme.current
    val alpha = if (enabled) 1f else 0.38f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
    ) {
        if (topDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
            }
            trailing()
        }
    }
}

/**
 * 开关设置行：标准行 + [InkToggle]。
 *
 * @param inlinePreview 可选的内联预览（例如分隔线预览条），渲染在 label 与 toggle 之间。
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    topDivider: Boolean = false,
    enabled: Boolean = true,
    inlinePreview: @Composable (() -> Unit)? = null,
) {
    SettingRow(
        label = label,
        modifier = modifier.testTag("SwitchRow_$label"),
        sublabel = sublabel,
        topDivider = topDivider,
        enabled = enabled,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (inlinePreview != null) {
                Box(modifier = Modifier.weight(1f, fill = false)) { inlinePreview() }
            }
            InkToggle(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
            )
        }
    }
}

/**
 * 分隔线预览 Switch 行：在 [SwitchRow] 基础上渲染一根 1dp 的实时分隔线预览。
 *
 * 当 [checked] = false 时，预览线淡出，用户可在设置面板内直观看到开关的实际效果。
 */
@Composable
fun DividerSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    topDivider: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalReaderColorScheme.current
    val lineAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (checked && enabled) 0.55f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "DividerSwitchRow_alpha",
    )
    SwitchRow(
        label = label,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        topDivider = topDivider,
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 140.dp)
                .height(1.dp)
                .background(colors.accent.copy(alpha = lineAlpha)),
        )
    }
}

/**
 * 下拉设置行：标准行 + [InkSelect]。
 */
@Composable
fun <T> SelectRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    topDivider: Boolean = false,
) {
    SettingRow(
        label = label,
        modifier = modifier.testTag("SelectRow_$label"),
        sublabel = sublabel,
        topDivider = topDivider,
    ) {
        InkSelect(
            options = options,
            selected = selected,
            onSelect = onSelect,
            testTag = "SelectRow_${label}_Select",
        )
    }
}

/**
 * 分段选择设置行：标签 + 满宽 [SegmentedControl]。
 *
 * 用于选项数较少（≤4）的离散设置，相比 [SelectRow] 的下拉框，分段控件一步可达、
 * 全部选项一眼可见，更直观。选中态采用主题强调色药丸（与"全局/本书"切换一致）。
 *
 * API 与 [SelectRow] 对齐，可直接替换。
 */
@Composable
fun <T> SegmentedRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    topDivider: Boolean = false,
    icons: List<ImageVector?> = emptyList(),
) {
    val colors = LocalReaderColorScheme.current
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("SegmentedRow_$label"),
    ) {
        if (topDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            SegmentedControl(
                options = options.map { it.second },
                selectedIndex = selectedIndex,
                onSelectedChange = { index ->
                    options.getOrNull(index)?.first?.let(onSelect)
                },
                modifier = Modifier.weight(1f),
                icons = icons,
                activeColor = colors.accent,
                activeTextColor = colors.background,
                inactiveTextColor = colors.textSecondary,
                containerColor = colors.divider.copy(alpha = 0.3f),
            )
        }
    }
}
