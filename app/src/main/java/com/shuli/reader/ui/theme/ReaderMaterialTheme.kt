package com.shuli.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.ThemeColors

/**
 * 把 [ReaderColorScheme] 桥接到 Material3 的 surface / onSurface 等角色，
 * 让 Material3 组件（DropdownMenu / Dialog / Card 等）跟随阅读器主题自动换色。
 *
 * 仅在阅读器 UI（QuickSettings / 菜单）层级使用，避免污染 App 全局 MaterialTheme。
 */
@Composable
fun ReaderMaterialTheme(
    readerColorScheme: ReaderColorScheme,
    content: @Composable () -> Unit,
) {
    val isDark = readerColorScheme.background.luminance() < 0.3f

    val m3Scheme = if (isDark) {
        darkColorScheme(
            background = readerColorScheme.background,
            surface = readerColorScheme.surface,
            surfaceVariant = readerColorScheme.divider.copy(alpha = 0.4f),
            surfaceContainer = readerColorScheme.surface,
            surfaceContainerHigh = readerColorScheme.surface,
            surfaceContainerHighest = readerColorScheme.surface,
            onBackground = readerColorScheme.textPrimary,
            onSurface = readerColorScheme.textPrimary,
            onSurfaceVariant = readerColorScheme.textSecondary,
            primary = readerColorScheme.accent,
            onPrimary = readerColorScheme.surface,
            secondary = readerColorScheme.textSecondary,
            outline = readerColorScheme.divider,
            outlineVariant = readerColorScheme.divider.copy(alpha = 0.5f),
        )
    } else {
        lightColorScheme(
            background = readerColorScheme.background,
            surface = readerColorScheme.surface,
            surfaceVariant = readerColorScheme.divider.copy(alpha = 0.4f),
            surfaceContainer = readerColorScheme.surface,
            surfaceContainerHigh = readerColorScheme.surface,
            surfaceContainerHighest = readerColorScheme.surface,
            onBackground = readerColorScheme.textPrimary,
            onSurface = readerColorScheme.textPrimary,
            onSurfaceVariant = readerColorScheme.textSecondary,
            primary = readerColorScheme.accent,
            onPrimary = readerColorScheme.surface,
            secondary = readerColorScheme.textSecondary,
            outline = readerColorScheme.divider,
            outlineVariant = readerColorScheme.divider.copy(alpha = 0.5f),
        )
    }

    MaterialTheme(
        colorScheme = m3Scheme,
        content = content,
    )
}

/**
 * 从 [ReaderTheme] 枚举快速派生 [ReaderMaterialTheme]。
 */
@Composable
fun ReaderMaterialTheme(
    readerTheme: ReaderTheme,
    content: @Composable () -> Unit,
) {
    ReaderMaterialTheme(
        readerColorScheme = readerTheme.toReaderColorScheme(),
        content = content,
    )
}
