package com.shuli.reader.feature.reader.settings.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 设置卡片内部的三级分类分隔线。
 *
 * 文本位于左侧，右侧分隔线与文本垂直居中；只表达分组，不表达可点击状态。
 */
@Composable
fun SettingsSectionDivider(
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp)
            .testTag("SettingsSectionDivider_$title"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = colors.textTertiary,
        )
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f)
                .height(1.dp)
                .background(colors.divider),
        )
    }
}
