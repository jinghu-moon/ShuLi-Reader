package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 圆形步进按钮（对应原型 .ss-btn / .font-stepper-btn / .margin-stepper-btn）。
 *
 * 灰底 + 细描边的圆形按钮，承载 − / + 等符号，按下不可用时降低不透明度。
 *
 * @param symbol 按钮符号（如 "−"、"+"）
 * @param onClick 点击回调
 * @param enabled 是否可用，false 时变灰且不响应点击
 * @param size 直径，默认 28dp（peek 字号步进可传更大值）
 */
@Composable
fun InkCircleButton(
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 28.dp,
) {
    val colors = LocalReaderColorScheme.current
    val alpha = if (enabled) 1f else 0.3f
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.background.copy(alpha = alpha))
            .border(BorderStroke(1.dp, colors.divider.copy(alpha = alpha)), CircleShape)
            .let { if (enabled) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = colors.textPrimary.copy(alpha = alpha),
            fontSize = (size.value * 0.5f).sp,
            fontWeight = FontWeight.Light,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
