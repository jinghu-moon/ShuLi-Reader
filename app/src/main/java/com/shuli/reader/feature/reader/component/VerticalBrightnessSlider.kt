package com.shuli.reader.feature.reader.component

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

@Composable
fun VerticalBrightnessSlider(
    brightness: Float, // -1 表示跟随系统，0.01..1f 表示手动亮度
    onBrightnessChange: (Float, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val readerColors = LocalReaderColorScheme.current
    val context = LocalContext.current
    val isAuto = brightness < 0f

    // 记录拖拽状态
    var isDragging by remember { mutableStateOf(false) }

    // 获取当前实际生效的亮度百分比
    val displayPercent = remember(brightness, isAuto) {
        if (isAuto) {
            val raw = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128,
            )
            (raw * 100f / 255f).toInt().coerceIn(1, 100)
        } else {
            (brightness * 100f).toInt().coerceIn(1, 100)
        }
    }

    Column(
        modifier = modifier
            .width(20.dp)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部图标/数字切换区
        Box(
            modifier = Modifier
                .height(20.dp)
                .width(24.dp)
                .clip(CircleShape)
                .clickable {
                    // 点击切换自动/手动，点击操作即为完成
                    if (isAuto) {
                        onBrightnessChange(displayPercent / 100f, true) // 退出自动模式
                    } else {
                        onBrightnessChange(-1f, true) // 进入自动模式
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // 同时放置，通过 alpha 切换，避免布局跳动
            Icon(
                imageVector = Icons.Outlined.BrightnessAuto,
                contentDescription = "Auto Brightness",
                tint = readerColors.textPrimary,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { alpha = if (isAuto && !isDragging) 1f else 0f }
            )
            Text(
                text = "$displayPercent",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = if (isDragging) readerColors.accent else readerColors.textPrimary,
                modifier = Modifier.graphicsLayer { alpha = if (isAuto && !isDragging) 0f else 1f }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 垂直滑块轨道
        Canvas(
            modifier = Modifier
                .width(12.dp)
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        isDragging = true

                        // 计算拖拽的亮度值
                        val updateBrightness = { y: Float, finished: Boolean ->
                            val trackHeight = size.height.toFloat()
                            val thumbRadiusPx = size.width / 2f
                            val effectiveHeight = trackHeight - 2 * thumbRadiusPx
                            val yInTrack = (y - thumbRadiusPx).coerceIn(0f, effectiveHeight)

                            // y=0 是顶部，所以反转
                            val fraction = 1f - (yInTrack / effectiveHeight)
                            onBrightnessChange(fraction.coerceIn(0.01f, 1f), finished)
                        }

                        var lastY = down.position.y
                        updateBrightness(lastY, false)

                        drag(down.id) { dragEvent ->
                            dragEvent.consume()
                            lastY = dragEvent.position.y
                            updateBrightness(lastY, false)
                        }

                        // 拖拽结束时，使用最后的 lastY 提交一次 finished = true
                        updateBrightness(lastY, true)
                        isDragging = false
                    }
                }
        ) {
            val trackW = size.width
            val trackH = size.height
            val radius = trackW / 2f
            val effectiveH = trackH - trackW
            val fraction = displayPercent / 100f

            val centerY = trackH - radius - effectiveH * fraction

            // 背景轨道
            drawRoundRect(
                color = if (isAuto) readerColors.divider else readerColors.textSecondary.copy(alpha = 0.1f),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(radius, radius)
            )

            // 活跃前景轨道（滑过的一半）
            drawRoundRect(
                color = if (isAuto) readerColors.textSecondary.copy(alpha = 0.2f) else readerColors.accent.copy(alpha = 0.2f),
                topLeft = Offset(0f, centerY - radius),
                size = Size(trackW, trackH - (centerY - radius)),
                cornerRadius = CornerRadius(radius, radius)
            )

            // 滑块圆点 (Thumb)
            drawCircle(
                color = if (isAuto) readerColors.textSecondary else readerColors.accent,
                radius = radius - 2.dp.toPx(),
                center = Offset(trackW / 2f, centerY)
            )
        }
    }
}
