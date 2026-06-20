package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shuli.reader.core.reader.model.BoxInsetsDp

/**
 * 盒子边距设置区段：CollapsibleCard + 4 个 InkStepperSlider（上/下/左/右）。
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

    val summary = if (collapsible && !expanded) {
        "${topLabel}${insets.top.toInt()} ${bottomLabel}${insets.bottom.toInt()} ${leftLabel}${insets.left.toInt()} ${rightLabel}${insets.right.toInt()}"
    } else null

    if (collapsible) {
        CollapsibleCard(
            title = title,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            summary = summary,
            modifier = modifier,
        ) {
            MarginSliders(
                localTop = localTop, localBottom = localBottom,
                localLeft = localLeft, localRight = localRight,
                onTopChange = { localTop = it },
                onBottomChange = { localBottom = it },
                onLeftChange = { localLeft = it },
                onRightChange = { localRight = it },
                onFinished = { _ ->
                    onInsetsChange(BoxInsetsDp(localTop, localBottom, localLeft, localRight))
                },
                topLabel = topLabel, bottomLabel = bottomLabel,
                leftLabel = leftLabel, rightLabel = rightLabel,
                defaultInsets = defaultInsets, title = title,
            )
        }
    } else {
        // 非折叠模式：直接显示滑块
        CollapsibleCard(
            title = title,
            expanded = true,
            onExpandedChange = null,
            modifier = modifier,
        ) {
            MarginSliders(
                localTop = localTop, localBottom = localBottom,
                localLeft = localLeft, localRight = localRight,
                onTopChange = { localTop = it },
                onBottomChange = { localBottom = it },
                onLeftChange = { localLeft = it },
                onRightChange = { localRight = it },
                onFinished = { _ ->
                    onInsetsChange(BoxInsetsDp(localTop, localBottom, localLeft, localRight))
                },
                topLabel = topLabel, bottomLabel = bottomLabel,
                leftLabel = leftLabel, rightLabel = rightLabel,
                defaultInsets = defaultInsets, title = title,
            )
        }
    }
}

@Composable
private fun MarginSliders(
    localTop: Float, localBottom: Float,
    localLeft: Float, localRight: Float,
    onTopChange: (Float) -> Unit,
    onBottomChange: (Float) -> Unit,
    onLeftChange: (Float) -> Unit,
    onRightChange: (Float) -> Unit,
    onFinished: (Float) -> Unit,
    topLabel: String, bottomLabel: String,
    leftLabel: String, rightLabel: String,
    defaultInsets: BoxInsetsDp,
    title: String,
) {
    InkStepperSlider(
        value = localTop,
        onValueChange = onTopChange,
        onValueChangeFinished = onFinished,
        valueRange = 0f..96f, step = 4f,
        label = topLabel,
        formatValue = { "${it.toInt()}" },
        testTagPrefix = "Slider_${title}_Top",
        defaultValue = defaultInsets.top,
    )
    InkStepperSlider(
        value = localBottom,
        onValueChange = onBottomChange,
        onValueChangeFinished = onFinished,
        valueRange = 0f..96f, step = 4f,
        label = bottomLabel,
        formatValue = { "${it.toInt()}" },
        testTagPrefix = "Slider_${title}_Bottom",
        defaultValue = defaultInsets.bottom,
    )
    InkStepperSlider(
        value = localLeft,
        onValueChange = onLeftChange,
        onValueChangeFinished = onFinished,
        valueRange = 0f..96f, step = 4f,
        label = leftLabel,
        formatValue = { "${it.toInt()}" },
        testTagPrefix = "Slider_${title}_Left",
        defaultValue = defaultInsets.left,
    )
    InkStepperSlider(
        value = localRight,
        onValueChange = onRightChange,
        onValueChangeFinished = onFinished,
        valueRange = 0f..96f, step = 4f,
        label = rightLabel,
        formatValue = { "${it.toInt()}" },
        testTagPrefix = "Slider_${title}_Right",
        defaultValue = defaultInsets.right,
    )
}
