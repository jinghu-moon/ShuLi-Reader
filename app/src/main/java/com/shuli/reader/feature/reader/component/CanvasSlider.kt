package com.shuli.reader.feature.reader.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Canvas 手绘滑块：圆形 thumb + 圆角轨道
 *
 * 独立组件，可被任何需要滑块的场景复用。
 *
 * @param value 当前值
 * @param onValueChange 值变化回调
 * @param valueRange 值范围
 * @param steps 分步数，0 表示连续
 * @param thumbColor 圆形拇指颜色
 * @param activeTrackColor 活跃轨道颜色
 * @param inactiveTrackColor 非活跃轨道颜色
 * @param thumbRadius 圆形拇指半径
 * @param trackHeight 轨道粗细
 */
@Composable
fun CanvasSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    thumbColor: Color = LocalReaderColorScheme.current.accent,
    activeTrackColor: Color = LocalReaderColorScheme.current.accent,
    inactiveTrackColor: Color = LocalReaderColorScheme.current.divider,
    thumbRadius: Dp = 8.dp,
    trackHeight: Dp = 3.dp,
    modifier: Modifier = Modifier,
) {
    val stepFractions = remember(steps) {
        if (steps > 0) {
            (0..steps).map { it.toFloat() / steps }
        } else emptyList()
    }

    Canvas(
        modifier = modifier.pointerInput(valueRange, steps) {
            fun fractionToValue(frac: Float): Float {
                val clamped = frac.coerceIn(0f, 1f)
                val snapped = if (stepFractions.isNotEmpty()) {
                    stepFractions.minByOrNull { kotlin.math.abs(it - clamped) } ?: clamped
                } else clamped
                return valueRange.start + snapped * (valueRange.endInclusive - valueRange.start)
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startX = down.position.x
                // 立即响应按下位置
                onValueChange(fractionToValue(startX / size.width))
                // 等待拖动或抬起
                var dragged = false
                drag(down.id) { dragEvent ->
                    dragEvent.consume()
                    dragged = true
                    val frac = dragEvent.position.x.coerceIn(0f, size.width.toFloat()) / size.width
                    onValueChange(fractionToValue(frac))
                }
            }
        },
    ) {
        val trackY = size.height / 2f
        val rPx = thumbRadius.toPx()
        val halfTrack = trackHeight.toPx() / 2f
        val start = rPx
        val end = size.width - rPx
        val trackW = end - start

        val fraction = if (valueRange.endInclusive > valueRange.start) {
            ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        } else 0f
        val thumbX = start + fraction * trackW

        // 非活跃轨道
        drawLine(
            color = inactiveTrackColor,
            start = Offset(start, trackY),
            end = Offset(end, trackY),
            strokeWidth = trackHeight.toPx(),
            cap = StrokeCap.Round,
        )
        // 活跃轨道
        drawLine(
            color = activeTrackColor,
            start = Offset(start, trackY),
            end = Offset(thumbX, trackY),
            strokeWidth = trackHeight.toPx(),
            cap = StrokeCap.Round,
        )
        // 圆形 thumb
        drawCircle(
            color = thumbColor,
            radius = rPx,
            center = Offset(thumbX, trackY),
        )
    }
}

/**
 * 带标签、±按钮、数值显示的滑块行
 *
 * @param label 左侧标签文字
 * @param value 当前值
 * @param valueRange 值范围
 * @param steps 分步数，0 表示连续
 * @param format 数值格式化函数
 * @param onValueChange 值变化回调
 * @param showSlider 是否显示滑动条部分，false 时仅显示标签 + 数值
 * @param onValueClick 点击数值文字的回调（用于自动模式切换等场景）
 * @param sliderFraction 滑动条占行宽的比例（0..1），默认 0.55
 */
@Composable
fun ReaderValueSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showSlider: Boolean = true,
    onValueClick: (() -> Unit)? = null,
    sliderFraction: Float = 0.55f,
) {
    val readerColors = LocalReaderColorScheme.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(SETTINGS_LABEL_WIDTH),
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
        if (showSlider) {
            IconButton(
                onClick = {
                    val step = (valueRange.endInclusive - valueRange.start) / 20f
                    onValueChange((value - step).coerceIn(valueRange))
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Outlined.Remove, null, Modifier.size(18.dp), tint = readerColors.textPrimary)
            }
            CanvasSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth(sliderFraction).height(36.dp),
            )
            IconButton(
                onClick = {
                    val step = (valueRange.endInclusive - valueRange.start) / 20f
                    onValueChange((value + step).coerceIn(valueRange))
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = readerColors.textPrimary)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = format(value),
            modifier = Modifier
                .width(52.dp)
                .then(if (onValueClick != null) Modifier.clickable { onValueClick() } else Modifier),
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textPrimary,
            textAlign = TextAlign.End,
        )
    }
}
