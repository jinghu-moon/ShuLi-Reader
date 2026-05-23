package com.shuli.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Geist 风格暗色主题
private val GeistDarkColorScheme = darkColorScheme(
    background = GeistBackground,
    surface = GeistSurfaceSecondary,
    surfaceVariant = GeistSurfaceHover,
    surfaceContainer = GeistCard,
    onBackground = GeistTextPrimary,
    onSurface = GeistTextPrimary,
    onSurfaceVariant = GeistTextSecondary,
    primary = GeistAccent,
    onPrimary = Color.White,
    secondary = GeistTextSecondary,
    outline = GeistTextTertiary,
    outlineVariant = GeistBorder,
    error = GeistDanger,
    onError = Color.White,
)

// Geist 风格亮色主题
private val GeistLightColorScheme = lightColorScheme(
    background = LightBackground,
    surface = LightSurfaceSecondary,
    surfaceVariant = LightSurfaceHover,
    surfaceContainer = LightCard,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    primary = LightAccent,
    onPrimary = Color.White,
    secondary = LightTextSecondary,
    outline = LightTextTertiary,
    outlineVariant = LightBorder,
    error = LightDanger,
    onError = Color.White,
)

@Composable
fun ShuLiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    typography: androidx.compose.material3.Typography = Typography,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) GeistDarkColorScheme else GeistLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = Shapes,
        content = content,
    )
}
