package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 自绘滑动条（对应原型 §4.7 的 .ss-track / .ss-fill / .ss-thumb）。
 *
 * 不依赖 Material3 [androidx.compose.material3.Slider]：
 * - 细圆角轨道 + 强调色填充 + 圆形 thumb，造型贴合墨土设计。
 * - 点击轨道任意位置跳转、拖拽连续调值，均通过 [pointerInput] 实现。
 * - 双击轨道重置到 [defaultValue]（如有）。
 *
 * @param value 当前值（落在 [valueRange] 内）
 * @param onValueChange 值变化回调（拖拽 / 点击实时触发）
 * @param valueRange 取值范围
 * @param defaultValue 默认值（可选）。在轨道上绘制竖线标记，双击重置到此值。
 * @param fillBrush 可选填充画刷（如色温滑块用橙→强调色渐变），为 null 时用纯强调色
 * @param onValueChangeFinished 交互结束回调（抬手 / 点击完成 / 双击重置）
 */
@Composable
fun InkSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    defaultValue: Float? = null,
    fillBrush: Brush? = null,
    onValueChangeFinished: ((Float) -> Unit)? = null,
) {
    val colors = LocalReaderColorScheme.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val snapThresholdPx = with(density) { 12.dp.toPx() }
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var lastEmitted = value

    fun emitFromX(x: Float) {
        if (trackWidthPx <= 0f) return
        val ratio = (x / trackWidthPx).coerceIn(0f, 1f)
        val rawValue = valueRange.start + ratio * span
        // 吸附到默认值（±12dp 范围内）
        val emitted = if (defaultValue != null) {
            val defaultX = ((defaultValue - valueRange.start) / span) * trackWidthPx
            if (kotlin.math.abs(x - defaultX) < snapThresholdPx) defaultValue else rawValue
        } else rawValue
        lastEmitted = emitted
        onValueChange(emitted)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .testTag("InkSlider")
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(valueRange, defaultValue) {
                detectTapGestures(
                    onTap = { offset ->
                        emitFromX(offset.x)
                        onValueChangeFinished?.invoke(lastEmitted)
                    },
                    onDoubleTap = {
                        if (defaultValue != null) {
                            lastEmitted = defaultValue
                            onValueChange(defaultValue)
                            onValueChangeFinished?.invoke(defaultValue)
                        }
                    },
                )
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragEnd = { onValueChangeFinished?.invoke(lastEmitted) },
                ) { change, _ ->
                    change.consume()
                    emitFromX(change.position.x)
                }
            },
    ) {
        val cy = size.height / 2f
        val trackH = 4.dp.toPx()
        val thumbR = 7.dp.toPx()
        // 轨道
        drawRoundRect(
            color = colors.divider,
            topLeft = Offset(0f, cy - trackH / 2f),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        // 默认值标记（小圆点）
        if (defaultValue != null) {
            val defFraction = ((defaultValue - valueRange.start) / span).coerceIn(0f, 1f)
            val defX = size.width * defFraction
            drawCircle(
                color = colors.textTertiary,
                radius = 4.dp.toPx(),
                center = Offset(defX, cy),
            )
        }
        // 填充
        val fillW = size.width * fraction
        if (fillW > 0f) {
            if (fillBrush != null) {
                drawRoundRect(
                    brush = fillBrush,
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(fillW, trackH),
                    cornerRadius = CornerRadius(trackH / 2f),
                )
            } else {
                drawRoundRect(
                    color = colors.accent,
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(fillW, trackH),
                    cornerRadius = CornerRadius(trackH / 2f),
                )
            }
        }
        // thumb（带浅色描边圈，提升对比）
        val cx = fillW.coerceIn(thumbR, size.width - thumbR)
        drawCircle(color = colors.background, radius = thumbR + 1.5.dp.toPx(), center = Offset(cx, cy))
        drawCircle(color = colors.accent, radius = thumbR, center = Offset(cx, cy))
    }
}
