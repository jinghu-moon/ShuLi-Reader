package com.shuli.reader.feature.reader.component.quicksettings.v5

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
 * 四边距可视化控件。
 *
 * 顶部显示缩略图（反映四边距比例），下方是 4 组步进器 + 同步开关。
 */
@Composable
fun VisualMarginControl(
    margins: MarginValues,
    onMarginsChange: (MarginValues) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..120f,
    step: Float = 6f,
) {
    val readerColors = LocalReaderColorScheme.current
    var syncVertical by remember { mutableStateOf(false) }
    var syncHorizontal by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("VisualMarginControl"),
    ) {
        // 缩略图
        MarginThumbnail(
            margins = margins,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(vertical = 8.dp)
                .testTag("VisualMarginControl_Thumbnail"),
        )

        // 同步开关
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("↕ 同步", style = MaterialTheme.typography.labelSmall, color = readerColors.textSecondary)
                Switch(
                    checked = syncVertical,
                    onCheckedChange = { syncVertical = it },
                    modifier = Modifier.padding(start = 4.dp).testTag("VisualMarginControl_SyncV"),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("↔ 同步", style = MaterialTheme.typography.labelSmall, color = readerColors.textSecondary)
                Switch(
                    checked = syncHorizontal,
                    onCheckedChange = { syncHorizontal = it },
                    modifier = Modifier.padding(start = 4.dp).testTag("VisualMarginControl_SyncH"),
                )
            }
        }

        // 上
        StepperSlider(
            value = margins.top,
            onValueChange = { newTop ->
                onMarginsChange(
                    margins.copy(
                        top = newTop,
                        bottom = if (syncVertical) newTop else margins.bottom,
                    )
                )
            },
            valueRange = range,
            step = step,
            label = "上",
            formatValue = { "${it.toInt()}dp" },
            testTagPrefix = "MarginTop",
        )
        // 下
        StepperSlider(
            value = margins.bottom,
            onValueChange = { newBottom ->
                onMarginsChange(
                    margins.copy(
                        bottom = newBottom,
                        top = if (syncVertical) newBottom else margins.top,
                    )
                )
            },
            valueRange = range,
            step = step,
            label = "下",
            formatValue = { "${it.toInt()}dp" },
            testTagPrefix = "MarginBottom",
        )
        // 左
        StepperSlider(
            value = margins.left,
            onValueChange = { newLeft ->
                onMarginsChange(
                    margins.copy(
                        left = newLeft,
                        right = if (syncHorizontal) newLeft else margins.right,
                    )
                )
            },
            valueRange = range,
            step = step,
            label = "左",
            formatValue = { "${it.toInt()}dp" },
            testTagPrefix = "MarginLeft",
        )
        // 右
        StepperSlider(
            value = margins.right,
            onValueChange = { newRight ->
                onMarginsChange(
                    margins.copy(
                        right = newRight,
                        left = if (syncHorizontal) newRight else margins.left,
                    )
                )
            },
            valueRange = range,
            step = step,
            label = "右",
            formatValue = { "${it.toInt()}dp" },
            testTagPrefix = "MarginRight",
        )
    }
}

/**
 * 缩略图：用矩形表示页面，四边距用彩色边线标示粗细。
 */
@Composable
private fun MarginThumbnail(
    margins: MarginValues,
    modifier: Modifier = Modifier,
) {
    val accent = LocalReaderColorScheme.current.accent
    val textSecondary = LocalReaderColorScheme.current.textSecondary
    val maxMargin = 120f
    Box(
        modifier = modifier
            .aspectRatio(0.6f)
            .background(Color.Transparent)
            .drawBehind {
                val w = size.width
                val h = size.height
                val t = margins.top / maxMargin * h * 0.3f
                val b = margins.bottom / maxMargin * h * 0.3f
                val l = margins.left / maxMargin * w * 0.3f
                val r = margins.right / maxMargin * w * 0.3f
                // 外框
                drawRect(
                    color = textSecondary.copy(alpha = 0.3f),
                    size = size,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
                // 内容区域（白底）
                drawRect(
                    color = Color.White,
                    topLeft = Offset(l, t),
                    size = androidx.compose.ui.geometry.Size((w - l - r).coerceAtLeast(0f), (h - t - b).coerceAtLeast(0f)),
                )
                // 四边距高亮边
                val strokeW = 3.dp.toPx()
                drawLine(accent, Offset(0f, t), Offset(w, t), strokeWidth = strokeW) // top
                drawLine(accent, Offset(0f, h - b), Offset(w, h - b), strokeWidth = strokeW) // bottom
                drawLine(accent, Offset(l, 0f), Offset(l, h), strokeWidth = strokeW) // left
                drawLine(accent, Offset(w - r, 0f), Offset(w - r, h), strokeWidth = strokeW) // right
                // 虚线表示文字行
                val lineCount = 5
                val lineHeight = (h - t - b) / (lineCount + 1)
                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                for (i in 1..lineCount) {
                    val y = t + lineHeight * i
                    drawLine(
                        color = textSecondary.copy(alpha = 0.4f),
                        start = Offset(l + 4.dp.toPx(), y),
                        end = Offset(w - r - 4.dp.toPx(), y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = pathEffect,
                    )
                }
            },
    )
}
