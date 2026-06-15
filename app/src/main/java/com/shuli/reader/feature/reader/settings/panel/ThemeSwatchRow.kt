package com.shuli.reader.feature.reader.settings.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.feature.reader.settings.panel.controls.onAccentColor
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.toReaderColorScheme

/**
 * 主题色块行（对应原型 .theme-row）—— Peek 态常驻。
 *
 * 为每个内置 [ReaderTheme] + 自定义主题渲染一个圆形色块（填该主题背景色），
 * 选中态加强调色描边环并居中显示 ✓。
 *
 * @param currentTheme 当前主题
 * @param onThemeChange 主题切换回调
 * @param onOpenCustomTheme 打开自定义主题编辑器回调
 * @param customBackgroundColor 自定义主题的背景色（用于 CUSTOM 色块取色）
 */
@Composable
fun ThemeSwatchRow(
    currentTheme: ReaderTheme,
    onThemeChange: (ReaderTheme) -> Unit,
    onOpenCustomTheme: () -> Unit,
    modifier: Modifier = Modifier,
    customBackgroundColor: Int? = null,
) {
    val colors = LocalReaderColorScheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("ThemeSwatchRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "主题",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderTheme.entries.forEach { theme ->
                val swatchColor = when (theme) {
                    ReaderTheme.CUSTOM -> customBackgroundColor?.let { Color(it) } ?: colors.accent
                    else -> theme.toReaderColorScheme().background
                }
                ThemeSwatch(
                    color = swatchColor,
                    selected = currentTheme == theme,
                    ringColor = colors.accent,
                    surfaceColor = colors.surface,
                    onClick = {
                        if (theme == ReaderTheme.CUSTOM) {
                            onOpenCustomTheme()
                        } else {
                            onThemeChange(theme)
                        }
                    },
                    modifier = Modifier.testTag("ThemeSwatch_${theme.name}"),
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.textSecondary.copy(alpha = 0.3f)), CircleShape)
                    .clickable { onOpenCustomTheme() }
                    .testTag("ThemeSwatchAdd"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "自定义颜色",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    color: Color,
    selected: Boolean,
    ringColor: Color,
    surfaceColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .let {
                if (selected) {
                    it.border(BorderStroke(2.dp, ringColor), CircleShape)
                        .padding(4.dp)
                } else {
                    it
                }
            }
            .clip(CircleShape)
            .background(color)
            .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选中",
                tint = onAccentColor(color),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
