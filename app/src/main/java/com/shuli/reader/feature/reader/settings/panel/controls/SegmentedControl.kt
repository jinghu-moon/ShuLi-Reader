package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 通用分段选择控件：N 个选项中单选。
 *
 * 宽度策略：
 * - 有图标时：纯图标模式（只显示图标，不显示文字），各选项等宽
 * - 无图标时：以最长文本宽度为基准等宽分配
 *
 * @param options 选项标签列表（至少 2 个）
 * @param selectedIndex 当前选中索引
 * @param onSelectedChange 选中变化回调
 * @param icons 可选图标，与 [options] 按索引对应；为空时仅显示文字
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
    activeColor: Color = MaterialTheme.colorScheme.primary,
    activeTextColor: Color = MaterialTheme.colorScheme.onPrimary,
    inactiveTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
) {
    require(options.size >= 2) { "SegmentedControl requires at least 2 options" }

    val containerShape = RoundedCornerShape(6.dp)
    val chipShape = RoundedCornerShape(4.dp)
    val outerPad = 3.dp
    val chipHPad = 10.dp
    val chipVPad = 5.dp
    val hasIcons = icons.any { it != null }

    Box(
        modifier = modifier
            .clip(containerShape)
            .background(containerColor),
    ) {
        Row(modifier = Modifier.padding(outerPad)) {
            options.forEachIndexed { index, label ->
                val isActive = index == selectedIndex
                val icon = icons.getOrNull(index)
                val chipColor by animateColorAsState(
                    targetValue = if (isActive) activeColor else Color.Transparent,
                    animationSpec = tween(200),
                    label = "segChip",
                )
                val textColor by animateColorAsState(
                    targetValue = if (isActive) activeTextColor else inactiveTextColor,
                    animationSpec = tween(200),
                    label = "segText",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(chipShape)
                        .background(chipColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelectedChange(index) }
                        .padding(horizontal = chipHPad, vertical = chipVPad),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasIcons && icon != null) {
                        // 纯图标模式
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = textColor,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        // 文本模式
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
