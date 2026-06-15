package com.shuli.reader.feature.reader.settings.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.shuli.reader.feature.reader.settings.panel.controls.InkCircleButton
import com.shuli.reader.feature.reader.settings.panel.controls.InkToggle
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 四边距配置数据类。
 */
data class MarginValues(
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
)

/**
 * 四边距可视化控件（对应原型 §4.6.1 .margin-control）。
 *
 * 中心为页面缩略图（实时反映四边距比例），四周环绕圆形步进器；
 * 底部同步开关开启时，调上自动同步下、调左自动同步右。
 *
 * @param margins 当前四边距（dp）
 * @param onMarginsChange 变化回调
 * @param range 取值范围（dp）
 * @param step 单次步进量（dp）
 */
@Composable
fun VisualMarginControl(
    margins: MarginValues,
    onMarginsChange: (MarginValues) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..96f,
    step: Float = 4f,
) {
    val colors = LocalReaderColorScheme.current
    var sync by remember { mutableStateOf(false) }

    fun update(side: String, delta: Float) {
        val next = when (side) {
            "top" -> {
                val v = (margins.top + delta).coerceIn(range)
                margins.copy(top = v, bottom = if (sync) v else margins.bottom)
            }
            "bottom" -> {
                val v = (margins.bottom + delta).coerceIn(range)
                margins.copy(bottom = v, top = if (sync) v else margins.top)
            }
            "left" -> {
                val v = (margins.left + delta).coerceIn(range)
                margins.copy(left = v, right = if (sync) v else margins.right)
            }
            else -> {
                val v = (margins.right + delta).coerceIn(range)
                margins.copy(right = v, left = if (sync) v else margins.left)
            }
        }
        onMarginsChange(next)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("VisualMarginControl"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 上
        MarginStepper(
            label = "上",
            value = margins.top,
            vertical = false,
            onDec = { update("top", -step) },
            onInc = { update("top", step) },
            range = range,
            testTagPrefix = "MarginTop",
        )
        // 左 | 缩略图 | 右
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MarginStepper(
                label = "左",
                value = margins.left,
                vertical = true,
                onDec = { update("left", -step) },
                onInc = { update("left", step) },
                range = range,
                testTagPrefix = "MarginLeft",
            )
            MarginThumbnail(
                margins = margins,
                range = range,
                modifier = Modifier
                    .size(width = 72.dp, height = 100.dp)
                    .testTag("VisualMarginControl_Thumbnail"),
            )
            MarginStepper(
                label = "右",
                value = margins.right,
                vertical = true,
                onDec = { update("right", -step) },
                onInc = { update("right", step) },
                range = range,
                testTagPrefix = "MarginRight",
            )
        }
        // 下
        MarginStepper(
            label = "下",
            value = margins.bottom,
            vertical = false,
            onDec = { update("bottom", -step) },
            onInc = { update("bottom", step) },
            range = range,
            testTagPrefix = "MarginBottom",
        )
        // 同步开关
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InkToggle(
                checked = sync,
                onCheckedChange = {
                    sync = it
                    if (it) {
                        onMarginsChange(margins.copy(bottom = margins.top, right = margins.left))
                    }
                },
                modifier = Modifier.testTag("VisualMarginControl_Sync"),
            )
            Text(
                text = "同步上下 / 左右",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun MarginStepper(
    label: String,
    value: Float,
    vertical: Boolean,
    onDec: () -> Unit,
    onInc: () -> Unit,
    range: ClosedFloatingPointRange<Float>,
    testTagPrefix: String,
) {
    val colors = LocalReaderColorScheme.current
    val valueText: @Composable () -> Unit = {
        Text(
            text = value.toInt().toString(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp).testTag("${testTagPrefix}_Value"),
        )
    }
    val dec: @Composable () -> Unit = {
        InkCircleButton("−", onDec, enabled = value > range.start + 0.0001f, size = 22.dp,
            modifier = Modifier.testTag("${testTagPrefix}_Dec"))
    }
    val inc: @Composable () -> Unit = {
        InkCircleButton("+", onInc, enabled = value < range.endInclusive - 0.0001f, size = 22.dp,
            modifier = Modifier.testTag("${testTagPrefix}_Inc"))
    }
    if (vertical) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            inc()
            valueText()
            dec()
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dec()
            valueText()
            inc()
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        }
    }
}

@Composable
private fun MarginThumbnail(
    margins: MarginValues,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    val maxV = range.endInclusive.coerceAtLeast(1f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.background)
            .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(6.dp))
            .drawBehind {
                // 内层页面区域：按四边距比例内缩（比例上限 35%，保证可见）
                val l = (margins.left / maxV) * size.width * 0.35f
                val r = (margins.right / maxV) * size.width * 0.35f
                val t = (margins.top / maxV) * size.height * 0.35f
                val b = (margins.bottom / maxV) * size.height * 0.35f
                val innerW = (size.width - l - r).coerceAtLeast(0f)
                val innerH = (size.height - t - b).coerceAtLeast(0f)
                drawRect(
                    color = colors.accent.copy(alpha = 0.12f),
                    topLeft = Offset(l, t),
                    size = Size(innerW, innerH),
                )
                drawRect(
                    color = colors.accent.copy(alpha = 0.5f),
                    topLeft = Offset(l, t),
                    size = Size(innerW, innerH),
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
    )
}
