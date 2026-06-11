package com.shuli.reader.feature.reader.component.quicksettings.v5

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 字号步进器：− / 显示当前值 / +
 *
 * 用于 Peek 态常驻。
 *
 * @param value 当前字号值（sp）
 * @param range 允许范围，默认 12..32
 * @param step 步进量，默认 1
 * @param onValueChange 值变化回调
 */
@Composable
fun FontSizeStepper(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 12f..32f,
    step: Float = 1f,
) {
    val readerColors = LocalReaderColorScheme.current
    val canDecrease = value > range.start + 0.001f
    val canIncrease = value < range.endInclusive - 0.001f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("FontSizeStepper"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "字号",
            style = MaterialTheme.typography.bodyLarge,
            color = readerColors.textPrimary,
            modifier = Modifier.padding(start = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(
                onClick = { onValueChange((value - step).coerceIn(range)) },
                enabled = canDecrease,
                modifier = Modifier.size(36.dp).testTag("FontSizeStepper_Decrease"),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "减小字号")
            }
            Text(
                text = "${value.toInt()} sp",
                style = MaterialTheme.typography.titleMedium,
                color = readerColors.textPrimary,
                modifier = Modifier.padding(horizontal = 12.dp).testTag("FontSizeStepper_Value"),
            )
            OutlinedIconButton(
                onClick = { onValueChange((value + step).coerceIn(range)) },
                enabled = canIncrease,
                modifier = Modifier.size(36.dp).testTag("FontSizeStepper_Increase"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "增大字号")
            }
        }
    }
}

/**
 * 通用步进器 + 滑块复合控件。
 *
 * 布局：[−] Slider [＋] 数值
 */
@Composable
fun StepperSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    step: Float = (valueRange.endInclusive - valueRange.start) / 20f,
    steps: Int = 0,
    label: String = "",
    formatValue: (Float) -> String = { "%.1f".format(it) },
    testTagPrefix: String = "StepperSlider",
) {
    val readerColors = LocalReaderColorScheme.current
    val canDecrease = value > valueRange.start + 0.0001f
    val canIncrease = value < valueRange.endInclusive - 0.0001f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(testTagPrefix),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = readerColors.textPrimary,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        IconButton(
            onClick = {
                onValueChange((value - step).coerceIn(valueRange))
            },
            enabled = canDecrease,
            modifier = Modifier.size(32.dp).testTag("${testTagPrefix}_Dec"),
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "减")
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f).testTag("${testTagPrefix}_Slider"),
        )
        IconButton(
            onClick = {
                onValueChange((value + step).coerceIn(valueRange))
            },
            enabled = canIncrease,
            modifier = Modifier.size(32.dp).testTag("${testTagPrefix}_Inc"),
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "增")
        }
        Text(
            text = formatValue(value),
            style = MaterialTheme.typography.bodySmall,
            color = readerColors.textSecondary,
            modifier = Modifier.padding(start = 4.dp).testTag("${testTagPrefix}_Value"),
        )
    }
}
