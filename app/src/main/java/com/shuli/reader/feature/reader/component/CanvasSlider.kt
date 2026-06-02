package com.shuli.reader.feature.reader.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
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
 * @param modifier Modifier
 * @param enabled 是否启用，false 时降低透明度并忽略手势
 * @param steps 分步数，0 表示连续
 * @param onValueChangeFinished 拖拽结束或点击确认后回调
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    thumbColor: Color = LocalReaderColorScheme.current.accent,
    activeTrackColor: Color = LocalReaderColorScheme.current.accent,
    inactiveTrackColor: Color = LocalReaderColorScheme.current.divider,
    thumbRadius: Dp = 8.dp,
    trackHeight: Dp = 3.dp,
) {
    val stepFractions = remember(steps) {
        if (steps > 0) {
            (0..steps).map { it.toFloat() / steps }
        } else emptyList()
    }

    val density = LocalDensity.current
    val thumbRadiusPx = remember(thumbRadius, density) { with(density) { thumbRadius.toPx() } }

    // 按压/拖拽状态
    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // 避免 pointerInput 中捕获过期回调
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val animatedThumbRadius by animateFloatAsState(
        targetValue = if (isPressed) thumbRadiusPx * 1.3f else thumbRadiusPx,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbRadius",
    )
    val animatedFraction by animateFloatAsState(
        targetValue = if (valueRange.endInclusive > valueRange.start) {
            ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        } else 0f,
        animationSpec = if (isDragging) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh,
            )
        },
        label = "fraction",
    )

    Canvas(
        modifier = modifier
            .semantics {
                if (!enabled) disabled()
                setProgress { targetValue ->
                    val newValue = targetValue.coerceIn(valueRange.start, valueRange.endInclusive)
                    currentOnValueChange(newValue)
                    currentOnValueChangeFinished?.invoke()
                    true
                }
            }
            .pointerInput(valueRange, steps, enabled) {
                if (!enabled) return@pointerInput
                fun fractionToValue(frac: Float): Float {
                    val clamped = frac.coerceIn(0f, 1f)
                    val snapped = if (stepFractions.isNotEmpty()) {
                        stepFractions.minByOrNull { kotlin.math.abs(it - clamped) } ?: clamped
                    } else clamped
                    return valueRange.start + snapped * (valueRange.endInclusive - valueRange.start)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    isDragging = true
                    currentOnValueChange(fractionToValue(down.position.x / size.width))
                    drag(down.id) { dragEvent ->
                        dragEvent.consume()
                        val frac = dragEvent.position.x.coerceIn(0f, size.width.toFloat()) / size.width
                        currentOnValueChange(fractionToValue(frac))
                    }
                    isDragging = false
                    isPressed = false
                    currentOnValueChangeFinished?.invoke()
                }
            },
    ) {
        val trackY = size.height / 2f
        val rPx = animatedThumbRadius
        val start = thumbRadiusPx
        val end = size.width - thumbRadiusPx
        val trackW = end - start

        val thumbX = start + animatedFraction * trackW

        val alpha = if (enabled) 1f else 0.38f

        // 非活跃轨道
        drawLine(
            color = inactiveTrackColor.copy(alpha = alpha),
            start = Offset(start, trackY),
            end = Offset(end, trackY),
            strokeWidth = trackHeight.toPx(),
            cap = StrokeCap.Round,
        )
        // 活跃轨道
        drawLine(
            color = activeTrackColor.copy(alpha = alpha),
            start = Offset(start, trackY),
            end = Offset(thumbX, trackY),
            strokeWidth = trackHeight.toPx(),
            cap = StrokeCap.Round,
        )
        // 圆形 thumb
        drawCircle(
            color = thumbColor.copy(alpha = alpha),
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
 * @param modifier Modifier
 * @param showSlider 是否显示滑动条部分，false 时仅显示标签 + 数值
 * @param sliderEnabled 滑动条是否可交互
 * @param onValueChangeFinished 拖拽/点击确认后回调
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
    sliderEnabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueClick: (() -> Unit)? = null,
    sliderFraction: Float = 0.55f,
) {
    val readerColors = LocalReaderColorScheme.current
    val stepSize = remember(steps, valueRange) {
        if (steps > 0) {
            (valueRange.endInclusive - valueRange.start) / steps
        } else {
            (valueRange.endInclusive - valueRange.start) / 20f
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
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
                    onValueChange((value - stepSize).coerceIn(valueRange))
                    onValueChangeFinished?.invoke()
                },
                modifier = Modifier.size(36.dp),
                enabled = sliderEnabled,
            ) {
                Icon(Icons.Outlined.Remove, null, Modifier.size(18.dp), tint = readerColors.textPrimary)
            }
            CanvasSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                enabled = sliderEnabled,
                onValueChangeFinished = onValueChangeFinished,
                modifier = Modifier.fillMaxWidth(sliderFraction).height(36.dp),
            )
            IconButton(
                onClick = {
                    onValueChange((value + stepSize).coerceIn(valueRange))
                    onValueChangeFinished?.invoke()
                },
                modifier = Modifier.size(36.dp),
                enabled = sliderEnabled,
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
