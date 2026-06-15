package com.shuli.reader.feature.reader.component.quicksettings.v5.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 字号步进器（对应原型 .font-stepper）—— Peek 态常驻。
 *
 * 居中布局：字号  [−]  18 sp  [+]
 *
 * @param value 当前字号（sp）
 * @param onValueChange 值变化回调
 * @param range 允许范围，默认 12..32
 * @param step 步进量，默认 1
 */
@Composable
fun FontStepper(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 12f..32f,
    step: Float = 1f,
) {
    val colors = LocalReaderColorScheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("FontStepper"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "字号",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.width(16.dp))
        InkCircleButton(
            symbol = "−",
            onClick = { onValueChange((value - step).coerceIn(range)) },
            enabled = value > range.start + 0.0001f,
            size = 36.dp,
            modifier = Modifier.testTag("FontStepper_Decrease"),
        )
        Row(
            modifier = Modifier.widthIn(min = 72.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = value.toInt().toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("FontStepper_Value"),
            )
            Text(
                text = " sp",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        InkCircleButton(
            symbol = "+",
            onClick = { onValueChange((value + step).coerceIn(range)) },
            enabled = value < range.endInclusive - 0.0001f,
            size = 36.dp,
            modifier = Modifier.testTag("FontStepper_Increase"),
        )
    }
}
