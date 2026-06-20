package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * 通用分段选择控件：N 个选项中单选，带滑动指示器动画。
 *
 * @param options 选项标签列表（至少 2 个）
 * @param selectedIndex 当前选中索引
 * @param onSelectedChange 选中变化回调
 * @param equalWidth true = 所有选项等宽分配；false = 各选项按文本内容自适应宽度
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    equalWidth: Boolean = true,
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
    val density = LocalDensity.current

    // 跟踪容器总宽度和每个芯片的实际宽度
    var containerWidthPx by remember { mutableStateOf(0) }
    val chipWidthsPx = remember(options) {
        mutableStateListOf(*(IntArray(options.size).toTypedArray()))
    }

    // 计算每个芯片的 Dp 宽度和累积偏移
    val chipWidthsDp: List<Dp> = chipWidthsPx.map { px ->
        with(density) { px.toDp() }
    }
    val chipOffsetsDp: List<Dp> = run {
        val result = mutableListOf<Dp>()
        var cum = outerPad
        for (w in chipWidthsDp) {
            result.add(cum)
            cum += w
        }
        result
    }

    // 指示器动画
    val indicatorX by animateDpAsState(
        targetValue = chipOffsetsDp.getOrElse(selectedIndex) { outerPad },
        animationSpec = tween(200),
        label = "segIndX",
    )
    val indicatorW by animateDpAsState(
        targetValue = chipWidthsDp.getOrElse(selectedIndex) { 0.dp },
        animationSpec = tween(200),
        label = "segIndW",
    )

    // 估算芯片高度（文本行高 + 垂直 padding）
    val estimatedChipHeight = with(density) {
        MaterialTheme.typography.labelMedium.lineHeight.toDp() + chipVPad * 2
    }

    Box(
        modifier = modifier
            .clip(containerShape)
            .background(containerColor)
            .onSizeChanged { size -> containerWidthPx = size.width },
    ) {
        // 滑动指示器
        if (indicatorW > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorX, y = outerPad)
                    .width(indicatorW)
                    .height(estimatedChipHeight)
                    .clip(chipShape)
                    .background(activeColor),
            )
        }

        // 选项文本行
        Row(modifier = Modifier.padding(outerPad)) {
            options.forEachIndexed { index, label ->
                val isActive = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isActive) activeTextColor else inactiveTextColor,
                    animationSpec = tween(200),
                    label = "segText",
                )

                val chipModifier = if (equalWidth) {
                    Modifier.weight(1f)
                } else {
                    Modifier
                }

                Box(
                    modifier = chipModifier
                        .clip(chipShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelectedChange(index) }
                        .padding(horizontal = chipHPad, vertical = chipVPad)
                        .onSizeChanged { size ->
                            if (chipWidthsPx[index] != size.width) {
                                chipWidthsPx[index] = size.width
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                    )
                }
            }
        }
    }
}
