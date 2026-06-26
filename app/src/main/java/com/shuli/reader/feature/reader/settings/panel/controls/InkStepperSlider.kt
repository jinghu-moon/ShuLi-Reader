package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.settings.panel.LocalLabelWidthState
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 步进滑块复合控件（对应原型 .stepper-slider）。
 *
 * 布局：标题(可选副标题) … [−] [InkSlider] [+] 数值
 *
 * 取代旧实现里基于 Material3 Slider 的 StepperSlider，统一为自绘造型。
 *
 * @param value 当前值
 * @param onValueChange 值变化回调
 * @param valueRange 取值范围
 * @param step 圆形按钮单次步进量
 * @param label 行标题（为空则不显示左侧标题，控件占满整行）
 * @param sublabel 行副标题（可选，灰色小字）
 * @param formatValue 数值格式化
 * @param parseValue 字符串解析为浮点数（可选，默认使用 toFloatOrNull）
 * @param fillBrush 滑块填充画刷（色温等场景）
 * @param defaultValue 默认值（可选）。传递给 InkSlider 用于竖线标记和双击重置。
 * @param onValueChangeFinished 交互结束回调，参数为最终值（抬手 / 点击完成 / 步进按钮单击）
 * @param testTagPrefix 测试 tag 前缀
 */
@Composable
fun InkStepperSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    modifier: Modifier = Modifier,
    label: String = "",
    sublabel: String? = null,
    formatValue: (Float) -> String = { "%.1f".format(it) },
    parseValue: (String) -> Float? = { it.toFloatOrNull() },
    fillBrush: Brush? = null,
    defaultValue: Float? = null,
    onValueChangeFinished: ((Float) -> Unit)? = null,
    testTagPrefix: String = "InkStepperSlider",
    topDivider: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalReaderColorScheme.current
    val labelWidthState = LocalLabelWidthState.current
    val density = LocalDensity.current
    val alpha = if (enabled) 1f else 0.38f

    // 输入对话框状态
    val showInputDialog = remember { mutableStateOf(false) }
    val inputText = remember { mutableStateOf("") }
    val inputError = remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (topDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag(testTagPrefix)
                .graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (label.isNotEmpty()) {
            val labelModifier = Modifier.widthIn(min = 56.dp).padding(end = 8.dp)
            Column(modifier = labelModifier) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    onTextLayout = { result ->
                        if (labelWidthState != null) {
                            val widthDp = with(density) { result.size.width.toDp() }
                            labelWidthState.onReport(testTagPrefix, widthDp)
                        }
                    },
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InkCircleButton(
                symbol = "−",
                onClick = {
                    if (enabled) {
                        val next = (value - step).coerceIn(valueRange)
                        onValueChange(next)
                        onValueChangeFinished?.invoke(next)
                    }
                },
                enabled = enabled && value > valueRange.start + 0.0001f,
                size = 26.dp,
                modifier = Modifier.testTag("${testTagPrefix}_Dec"),
            )
            InkSlider(
                value = value,
                onValueChange = { if (enabled) onValueChange(it) },
                onValueChangeFinished = { finalValue -> if (enabled) onValueChangeFinished?.invoke(finalValue) },
                valueRange = valueRange,
                fillBrush = fillBrush,
                defaultValue = defaultValue,
                modifier = Modifier.weight(1f).testTag("${testTagPrefix}_Slider"),
            )
            InkCircleButton(
                symbol = "+",
                onClick = {
                    if (enabled) {
                        val next = (value + step).coerceIn(valueRange)
                        onValueChange(next)
                        onValueChangeFinished?.invoke(next)
                    }
                },
                enabled = enabled && value < valueRange.endInclusive - 0.0001f,
                size = 26.dp,
                modifier = Modifier.testTag("${testTagPrefix}_Inc"),
            )
            Spacer(Modifier.padding(end = 2.dp))
            val valueAlignedWidth = labelWidthState?.alignedValueWidth?.takeIf { it != Dp.Unspecified }
            val formatted = formatValue(value)
            val valueModifier = if (valueAlignedWidth != null) {
                Modifier.width(valueAlignedWidth)
            } else {
                Modifier.widthIn(min = 36.dp)
            }
            Text(
                text = formatted,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = valueModifier
                    .testTag("${testTagPrefix}_Value")
                    .clickable(enabled = enabled) {
                        inputText.value = formatValue(value)
                        inputError.value = null
                        showInputDialog.value = true
                    },
                onTextLayout = { result ->
                    if (labelWidthState != null) {
                        val widthDp = with(density) { result.size.width.toDp() }
                        labelWidthState.onReportValue(testTagPrefix, widthDp)
                    }
                },
            )
        }
    }
    }

    // 数值输入对话框
    if (showInputDialog.value) {
        val strings = LocalAppStrings.current.reader
        AlertDialog(
            onDismissRequest = { showInputDialog.value = false },
            title = {
                Text(
                    text = label.ifEmpty { strings.inputNumberTitle },
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column {
                    Text(
                        text = "${strings.rangeLabel}: ${formatValue(valueRange.start)} ~ ${formatValue(valueRange.endInclusive)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = inputText.value,
                        onValueChange = { newValue ->
                            // 只允许数字、小数点和负号
                            val filtered = newValue.filter { it.isDigit() || it == '.' || it == '-' }
                            inputText.value = filtered
                            // 验证输入
                            val parsed = parseValue(filtered)
                            inputError.value = when {
                                filtered.isEmpty() -> null
                                parsed == null -> strings.invalidNumberError
                                parsed < valueRange.start -> strings.numberTooSmallError(formatValue(valueRange.start))
                                parsed > valueRange.endInclusive -> strings.numberTooLargeError(formatValue(valueRange.endInclusive))
                                else -> null
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            color = colors.textPrimary,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = colors.divider.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                    if (inputError.value != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = inputError.value!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = parseValue(inputText.value)
                        if (parsed != null) {
                            val clamped = parsed.coerceIn(valueRange)
                            onValueChange(clamped)
                            onValueChangeFinished?.invoke(clamped)
                        }
                        showInputDialog.value = false
                    },
                    enabled = inputError.value == null && inputText.value.isNotEmpty(),
                ) {
                    Text(strings.confirmAction, color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog.value = false }) {
                    Text(strings.cancelAction, color = colors.textSecondary)
                }
            },
        )
    }
}
