package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.reader.model.BoxInsetsDp

/**
 * 盒子边距设置区段：可折叠标题 + 4 个 InkStepperSlider（上/下/左/右）。
 *
 * 无状态组件：只接收 value 和 onValueChange。
 * 拖拽防抖：onValueChange 仅更新本地 UI 状态，
 * onValueChangeFinished 才触发实际的 Setting 变更。
 * 折叠时显示当前数值摘要。
 */
@Composable
fun BoxMarginSection(
    title: String,
    insets: BoxInsetsDp,
    onInsetsChange: (BoxInsetsDp) -> Unit,
    topLabel: String,
    bottomLabel: String,
    leftLabel: String,
    rightLabel: String,
    defaultInsets: BoxInsetsDp = BoxInsetsDp.ZERO,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = !collapsible,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    // 本地 UI 状态（拖拽中不触发 REFLOW）
    var localTop by remember(insets.top) { mutableFloatStateOf(insets.top) }
    var localBottom by remember(insets.bottom) { mutableFloatStateOf(insets.bottom) }
    var localLeft by remember(insets.left) { mutableFloatStateOf(insets.left) }
    var localRight by remember(insets.right) { mutableFloatStateOf(insets.right) }

    Column(modifier = modifier.fillMaxWidth()) {
        // 可折叠标题行（含摘要）
        if (collapsible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!expanded) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${topLabel}${insets.top.toInt()} ${bottomLabel}${insets.bottom.toInt()} ${leftLabel}${insets.left.toInt()} ${rightLabel}${insets.right.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
        }

        AnimatedVisibility(visible = !collapsible || expanded) {
            Column {
                InkStepperSlider(
                    value = localTop,
                    onValueChange = { localTop = it },
                    onValueChangeFinished = {
                        onInsetsChange(BoxInsetsDp(localTop, localBottom, localLeft, localRight))
                    },
                    valueRange = 0f..96f,
                    step = 4f,
                    label = topLabel,
                    formatValue = { "${it.toInt()}" },
                    testTagPrefix = "Slider_${title}_Top",
                    defaultValue = defaultInsets.top,
                )
                InkStepperSlider(
                    value = localBottom,
                    onValueChange = { localBottom = it },
                    onValueChangeFinished = {
                        onInsetsChange(BoxInsetsDp(localTop, localBottom, localLeft, localRight))
                    },
                    valueRange = 0f..96f,
                    step = 4f,
                    label = bottomLabel,
                    formatValue = { "${it.toInt()}" },
                    testTagPrefix = "Slider_${title}_Bottom",
                    defaultValue = defaultInsets.bottom,
                )
                InkStepperSlider(
                    value = localLeft,
                    onValueChange = { localLeft = it },
                    onValueChangeFinished = {
                        onInsetsChange(BoxInsetsDp(localTop, localBottom, localLeft, localRight))
                    },
                    valueRange = 0f..96f,
                    step = 4f,
                    label = leftLabel,
                    formatValue = { "${it.toInt()}" },
                    testTagPrefix = "Slider_${title}_Left",
                    defaultValue = defaultInsets.left,
                )
                InkStepperSlider(
                    value = localRight,
                    onValueChange = { localRight = it },
                    onValueChangeFinished = {
                        onInsetsChange(BoxInsetsDp(localTop, localBottom, localLeft, localRight))
                    },
                    valueRange = 0f..96f,
                    step = 4f,
                    label = rightLabel,
                    formatValue = { "${it.toInt()}" },
                    testTagPrefix = "Slider_${title}_Right",
                    defaultValue = defaultInsets.right,
                )
            }
        }
    }
}
