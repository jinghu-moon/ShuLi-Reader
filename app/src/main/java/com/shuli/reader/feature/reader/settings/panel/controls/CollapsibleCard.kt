package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 通用折叠卡片：可点击标题行 + 内嵌色块包裹的展开内容。
 *
 * 设计决策（方案 2）：
 * - 展开时标题变为 primary 色（激活态反馈），不需要额外加粗/放大
 * - 展开内容用 surfaceVariant 色块包裹，建立父子归属关系
 * - 折叠时可在标题右侧显示摘要文本
 *
 * @param title 标题文本
 * @param expanded 是否展开（受控模式）；为 null 时使用内部状态（非受控模式）
 * @param onExpandedChange 展开状态变化回调；为 null 时组件自行管理状态
 * @param summary 折叠时显示的摘要文本（标题右侧）
 * @param initiallyExpanded 非受控模式下的初始展开状态
 * @param content 展开后显示的内容
 */
@Composable
fun CollapsibleCard(
    title: String,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 非受控模式：内部管理状态
    var internalExpanded by remember { mutableStateOf(initiallyExpanded) }
    val isExpanded = expanded ?: internalExpanded
    val isCollapsible = expanded != null || onExpandedChange != null || initiallyExpanded || summary != null

    Column(modifier = modifier.fillMaxWidth()) {
        // 标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    val newValue = !isExpanded
                    if (expanded == null) internalExpanded = newValue
                    onExpandedChange?.invoke(newValue)
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                ),
                color = if (isExpanded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (!isExpanded && summary != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (isExpanded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        // 展开内容（内嵌色块）
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    content = content,
                )
            }
        }
    }
}
