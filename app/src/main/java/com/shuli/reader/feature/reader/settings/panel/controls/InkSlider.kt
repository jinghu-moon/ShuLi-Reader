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
 *
 * @param value 当前值（落在 [valueRange] 内）
 * @param onValueChange 值变化回调（拖拽 / 点击实时触发）
 * @param valueRange 取值范围
 * @param fillBrush 可选填充画刷（如色温滑块用橙→强调色渐变），为 null 时用纯强调色
 * @param onValueChangeFinished 交互结束回调（抬手 / 点击完成）
 */
@Composable
fun InkSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    fillBrush: Brush? = null,
    onValueChangeFinished: ((Float) -> Unit)? = null,
) {
    val colors = LocalReaderColorScheme.current
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    // 记录最近一次 emit 的值；每次 recomposition 重置为当前 value，emitFromX 内再覆盖。
    // pointerInput lambda 捕获的是此变量的"当前 recomposition 时刻"引用，
    // 在 onDragEnd 触发时读到的是手势最后一帧写入的最终值。
    var lastEmitted = value

    fun emitFromX(x: Float) {
        if (trackWidthPx <= 0f) return
        val ratio = (x / trackWidthPx).coerceIn(0f, 1f)
        val emitted = valueRange.start + ratio * span
        lastEmitted = emitted
        onValueChange(emitted)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .testTag("InkSlider")
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    emitFromX(offset.x)
                    onValueChangeFinished?.invoke(lastEmitted)
                }
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
