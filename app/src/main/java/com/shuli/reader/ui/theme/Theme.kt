package com.shuli.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MoTuDarkColorScheme = darkColorScheme(
    background = AppDarkBackground,
    surface = AppDarkSurface,
    surfaceVariant = AppDarkSurfaceVariant,
    surfaceContainer = AppDarkSurfaceContainer,
    onBackground = AppDarkTextPrimary,
    onSurface = AppDarkTextPrimary,
    onSurfaceVariant = AppDarkTextSecondary,
    primary = AppDarkPrimary,
    onPrimary = AppDarkOnPrimary,
    secondary = AppDarkTextSecondary,
    outline = AppDarkOutline,
    outlineVariant = AppDarkDivider,
    error = StateDarkError,
    onError = AppDarkOnPrimary,
    errorContainer = StateDarkErrorContainer,
    onErrorContainer = StateDarkOnErrorContainer,
)

private val MoTuLightColorScheme = lightColorScheme(
    background = AppBackground,
    surface = AppSurface,
    surfaceVariant = AppSurfaceVariant,
    surfaceContainer = AppSurfaceContainer,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    onSurfaceVariant = AppTextSecondary,
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    secondary = AppTextSecondary,
    outline = AppOutline,
    outlineVariant = AppDivider,
    error = StateError,
    onError = AppOnPrimary,
    errorContainer = StateErrorContainer,
    onErrorContainer = StateOnErrorContainer,
)

@Composable
fun ShuLiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    typography: androidx.compose.material3.Typography = Typography,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) MoTuDarkColorScheme else MoTuLightColorScheme
    val readerColorScheme = if (darkTheme) ReaderDarkColorScheme else ReaderPaperColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalReaderColorScheme provides readerColorScheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes,
            content = content,
        )
    }
}
