package com.shuli.reader.feature.reader.component.quicksettings.v5

import android.graphics.SweepGradient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.shuli.reader.feature.reader.component.quicksettings.v5.controls.InkSlider
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 背景色预设（9 个，参考主流阅读 App） */
private val backgroundColorPresets = listOf(
    0xFFFFFFFF.toInt(),  // 白色
    0xFFF5F5DC.toInt(),  // 米色
    0xFFE8F5E9.toInt(),  // 浅绿
    0xFFE3F2FD.toInt(),  // 浅蓝
    0xFFFCE4EC.toInt(),  // 浅粉
    0xFFEFEBE9.toInt(),  // 浅棕
    0xFF263238.toInt(),  // 深蓝灰
    0xFF303030.toInt(),  // 深灰
    0xFF000000.toInt(),  // 黑色
)

/** 正文色预设（8 个） */
private val textColorPresets = listOf(
    0xFF000000.toInt(),  // 黑色
    0xFF212121.toInt(),  // 深灰
    0xFF424242.toInt(),  // 中灰
    0xFF1B5E20.toInt(),  // 深绿
    0xFF0D47A1.toInt(),  // 深蓝
    0xFF4A148C.toInt(),  // 深紫
    0xFF880E4F.toInt(),  // 深粉
    0xFF3E2723.toInt(),  // 深棕
)

/** 标题色预设（8 个） */
private val titleColorPresets = listOf(
    0xFF000000.toInt(),  // 黑色
    0xFF1A237E.toInt(),  // 深靛蓝
    0xFF311B92.toInt(),  // 深紫
    0xFF880E4F.toInt(),  // 深粉
    0xFFB71C1C.toInt(),  // 深红
    0xFFE65100.toInt(),  // 深橙
    0xFF33691E.toInt(),  // 深绿
    0xFF3E2723.toInt(),  // 深棕
)

/** 页眉页脚色预设（8 个） */
private val headerFooterColorPresets = listOf(
    0xFF757575.toInt(),  // 中灰
    0xFF9E9E9E.toInt(),  // 浅灰
    0xFF616161.toInt(),  // 深灰
    0xFF546E7A.toInt(),  // 蓝灰
    0xFF6D4C41.toInt(),  // 棕灰
    0xFF558B2F.toInt(),  // 橄榄绿
    0xFF00695C.toInt(),  // 深青
    0xFF37474F.toInt(),  // 暗蓝灰
)

/**
 * 自定义主题编辑对话框。
 *
 * 包含 4 个颜色选择器：背景色、正文色、标题色、页眉/页脚色。
 * 强调色由标题色自动衍生。
 */
@Composable
fun CustomThemeDialog(
    currentBg: Int?,
    currentText: Int?,
    currentTitle: Int?,
    currentHeaderFooter: Int?,
    onConfirm: (bg: Int, text: Int, title: Int, headerFooter: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    var bgColor by remember { mutableIntStateOf(currentBg ?: 0xFFFFFBFE.toInt()) }
    var textColor by remember { mutableIntStateOf(currentText ?: 0xFF1C1B1F.toInt()) }
    var titleColor by remember { mutableIntStateOf(currentTitle ?: 0xFF1C1B1F.toInt()) }
    var headerFooterColor by remember { mutableIntStateOf(currentHeaderFooter ?: 0xFF49454F.toInt()) }

    var showHuePicker by remember { mutableStateOf(false) }
    var customColorTarget by remember { mutableStateOf("") }
    var customColorInput by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = readerColors.background,
        titleContentColor = readerColors.textPrimary,
        textContentColor = readerColors.textPrimary,
        title = {
            Text(
                text = "自定义主题",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ColorSwatchRow(
                    label = "背景色",
                    presets = backgroundColorPresets,
                    selected = bgColor,
                    onSelect = { bgColor = it },
                    onCustomSelect = {
                        customColorTarget = "bg"
                        customColorInput = bgColor
                        showHuePicker = true
                    },
                    testTag = "BgColor",
                )
                ColorSwatchRow(
                    label = "正文色",
                    presets = textColorPresets,
                    selected = textColor,
                    onSelect = { textColor = it },
                    onCustomSelect = {
                        customColorTarget = "text"
                        customColorInput = textColor
                        showHuePicker = true
                    },
                    testTag = "TextColor",
                )
                ColorSwatchRow(
                    label = "标题色",
                    presets = titleColorPresets,
                    selected = titleColor,
                    onSelect = { titleColor = it },
                    onCustomSelect = {
                        customColorTarget = "title"
                        customColorInput = titleColor
                        showHuePicker = true
                    },
                    testTag = "TitleColor",
                )
                ColorSwatchRow(
                    label = "页眉页脚色",
                    presets = headerFooterColorPresets,
                    selected = headerFooterColor,
                    onSelect = { headerFooterColor = it },
                    onCustomSelect = {
                        customColorTarget = "headerFooter"
                        customColorInput = headerFooterColor
                        showHuePicker = true
                    },
                    testTag = "HeaderFooterColor",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(bgColor, textColor, titleColor, headerFooterColor) },
                modifier = Modifier.testTag("CustomThemeConfirm"),
            ) {
                Text("确定", color = readerColors.accent)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("CustomThemeCancel"),
            ) {
                Text("取消", color = readerColors.textSecondary)
            }
        },
        modifier = Modifier.testTag("CustomThemeDialog"),
    )

    if (showHuePicker) {
        HueColorPickerDialog(
            initialColor = customColorInput,
            onConfirm = { colorInt ->
                when (customColorTarget) {
                    "bg" -> bgColor = colorInt
                    "text" -> textColor = colorInt
                    "title" -> titleColor = colorInt
                    "headerFooter" -> headerFooterColor = colorInt
                }
                showHuePicker = false
            },
            onDismiss = { showHuePicker = false },
        )
    }
}

@Composable
private fun ColorSwatchRow(
    label: String,
    presets: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onCustomSelect: ((Int) -> Unit)? = null,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val readerColors = LocalReaderColorScheme.current
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textPrimary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { colorInt ->
                    val color = Color(colorInt)
                    val isSelected = colorInt == selected
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = readerColors.accent,
                                        shape = CircleShape,
                                    )
                                } else {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = Color.Black.copy(alpha = 0.1f),
                                        shape = CircleShape,
                                    )
                                }
                            )
                            .clickable { onSelect(colorInt) }
                            .testTag("${testTag}_${colorInt.toString(16)}"),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(readerColors.surface)
                    .border(
                        width = 1.dp,
                        color = readerColors.textSecondary.copy(alpha = 0.3f),
                        shape = CircleShape,
                    )
                    .clickable { onCustomSelect?.invoke(selected) }
                    .testTag("${testTag}_Custom"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "自定义颜色",
                    tint = readerColors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 色相环取色对话框。
 *
 * 包含色相环（Hue Wheel）+ 亮度滑块 + 颜色预览 + HEX 显示。
 */
@Composable
private fun HueColorPickerDialog(
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current

    val hsv = remember { FloatArray(3) }
    remember { android.graphics.Color.colorToHSV(initialColor, hsv) }

    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1].coerceAtLeast(0.3f)) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.3f)) }

    val currentColorInt = remember(hue, saturation, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }
    val currentColor = Color(currentColorInt)
    val hexText = remember(currentColorInt) {
        "#%06X".format(0xFFFFFF and currentColorInt)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = readerColors.background,
        titleContentColor = readerColors.textPrimary,
        textContentColor = readerColors.textPrimary,
        title = {
            Text(
                text = "选择颜色",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HueWheel(
                    hue = hue,
                    saturation = saturation,
                    onHueChange = { hue = it },
                    onSaturationChange = { saturation = it },
                    modifier = Modifier
                        .size(200.dp)
                        .testTag("HueWheel"),
                )

                Spacer(Modifier.height(16.dp))

                // 亮度滑块
                Text(
                    text = "亮度",
                    style = MaterialTheme.typography.bodySmall,
                    color = readerColors.textSecondary,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 4.dp),
                )
                InkSlider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0.3f..1.0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("BrightnessSlider"),
                )

                Spacer(Modifier.height(12.dp))

                // 颜色预览 + HEX
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
                            .testTag("HuePickerPreview"),
                    )
                    Text(
                        text = hexText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = readerColors.textPrimary,
                        modifier = Modifier.testTag("HuePickerHex"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentColorInt) },
                modifier = Modifier.testTag("HuePickerConfirm"),
            ) {
                Text("确定", color = readerColors.accent)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("HuePickerCancel"),
            ) {
                Text("取消", color = readerColors.textSecondary)
            }
        },
        modifier = Modifier.testTag("HueColorPickerDialog"),
    )
}

/**
 * 色相环组件。
 *
 * 绘制 HSV 色相环（环形），支持拖拽选色。
 * 水平方向映射色相（0°–360°），到圆心的距离映射饱和度（0–1）。
 */
@Composable
private fun HueWheel(
    hue: Float,
    saturation: Float,
    onHueChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringStrokeWidth = 28.dp

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = change.position.x - cx
                    val dy = change.position.y - cy
                    val radius = min(cx, cy)

                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    val angle = ((atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI) + 360.0) % 360.0

                    val innerRadius = radius - ringStrokeWidth.toPx()
                    val sat = ((distance - innerRadius) / (radius - innerRadius)).coerceIn(0f, 1f)

                    onHueChange(angle.toFloat())
                    onSaturationChange(sat)
                }
            },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(cx, cy)
        val strokePx = ringStrokeWidth.toPx()
        val innerRadius = radius - strokePx

        drawHueRing(cx, cy, radius, innerRadius, strokePx)

        // 选色指示点
        val angleRad = hue * PI.toFloat() / 180f
        val dotRadius = innerRadius + (radius - innerRadius) * saturation
        val dotX = cx + (dotRadius * cos(angleRad))
        val dotY = cy + (dotRadius * sin(angleRad))

        val dotColorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f))

        // 白色外圈
        drawCircle(
            color = Color.White,
            radius = 10.dp.toPx(),
            center = Offset(dotX, dotY),
        )
        // 颜色点
        drawCircle(
            color = Color(dotColorInt),
            radius = 7.dp.toPx(),
            center = Offset(dotX, dotY),
        )
    }
}

private fun DrawScope.drawHueRing(
    cx: Float,
    cy: Float,
    outerRadius: Float,
    innerRadius: Float,
    strokePx: Float,
) {
    val midRadius = (outerRadius + innerRadius) / 2f
    val ringWidth = outerRadius - innerRadius

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = ringWidth
            shader = SweepGradient(
                cx, cy,
                intArrayOf(
                    android.graphics.Color.HSVToColor(floatArrayOf(0f, 1f, 1f)),
                    android.graphics.Color.HSVToColor(floatArrayOf(60f, 1f, 1f)),
                    android.graphics.Color.HSVToColor(floatArrayOf(120f, 1f, 1f)),
                    android.graphics.Color.HSVToColor(floatArrayOf(180f, 1f, 1f)),
                    android.graphics.Color.HSVToColor(floatArrayOf(240f, 1f, 1f)),
                    android.graphics.Color.HSVToColor(floatArrayOf(300f, 1f, 1f)),
                    android.graphics.Color.HSVToColor(floatArrayOf(360f, 1f, 1f)),
                ),
                null,
            )
        }
        canvas.nativeCanvas.drawCircle(cx, cy, midRadius, paint)
    }
}
