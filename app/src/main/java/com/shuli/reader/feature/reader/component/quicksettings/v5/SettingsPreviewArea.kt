package com.shuli.reader.feature.reader.component.quicksettings.v5

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.feature.reader.settings.LayoutPrefs
import com.shuli.reader.feature.reader.settings.StylePrefs
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 设置面板 Expanded 态顶部的实时预览区。
 *
 * 当 [previewText] 为 null 时显示默认硬编码文本。
 * 字号 / 行距 / 字体 随 [layoutPrefs] / [stylePrefs] 变化实时更新（LIVE 策略）。
 */
@Composable
fun SettingsPreviewArea(
    layoutPrefs: LayoutPrefs,
    stylePrefs: StylePrefs,
    previewText: String?,
    modifier: Modifier = Modifier,
) {
    val readerColors = LocalReaderColorScheme.current
    val displayText = previewText?.take(120)?.ifBlank { null }
        ?: "天地玄黄，宇宙洪荒。日月盈昃，辰宿列张。The quick brown fox jumps over the lazy dog."
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("SettingsPreviewArea"),
        color = readerColors.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = layoutPrefs.fontSize.sp,
                    lineHeight = (layoutPrefs.fontSize * layoutPrefs.lineSpacing).sp,
                    letterSpacing = layoutPrefs.letterSpacing.sp,
                ),
                color = readerColors.textPrimary,
                maxLines = 3,
            )
        }
    }
}
