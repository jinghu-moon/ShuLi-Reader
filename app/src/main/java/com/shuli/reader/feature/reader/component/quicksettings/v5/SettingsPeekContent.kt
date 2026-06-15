package com.shuli.reader.feature.reader.component.quicksettings.v5

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness2
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.OrientationLock
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.feature.reader.SettingsScope
import com.shuli.reader.feature.reader.component.quicksettings.v5.controls.onAccentColor
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Peek 态常驻内容（对应原型 .peek-content）。
 *
 * 作用域段控 + 快捷图标按钮 / 主题色块。
 */
@Composable
fun SettingsPeekContent(
    prefs: ReaderPreferences,
    settingsScope: SettingsScope,
    onThemeChange: (ReaderTheme) -> Unit,
    onCustomThemeConfirm: (bg: Int, text: Int, title: Int, headerFooter: Int) -> Unit,
    onSettingChanged: (String, Any) -> Unit,
    onScopeChange: (SettingsScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    var showCustomThemeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("SettingsPanelV5_PeekContent"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 作用域 + 快捷图标
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScopeSegment(
                scope = settingsScope,
                onScopeChange = onScopeChange,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 三态主题切换：浅色 → 深色 → 自动 → 浅色
                val themeMode = when {
                    prefs.autoNightMode -> ThemeMode.AUTO
                    prefs.backgroundColor == ReaderTheme.DARK || prefs.backgroundColor == ReaderTheme.OLED -> ThemeMode.DARK
                    else -> ThemeMode.LIGHT
                }
                val (themeIcon, themeDesc) = when (themeMode) {
                    ThemeMode.LIGHT -> Icons.Outlined.LightMode to "浅色模式"
                    ThemeMode.DARK -> Icons.Outlined.Brightness2 to "深色模式"
                    ThemeMode.AUTO -> Icons.Outlined.BrightnessAuto to "跟随系统"
                }
                IconButton(
                    onClick = {
                        when (themeMode) {
                            ThemeMode.LIGHT -> {
                                onThemeChange(ReaderTheme.DARK)
                                onSettingChanged("auto_night_mode", false)
                            }
                            ThemeMode.DARK -> {
                                onSettingChanged("auto_night_mode", true)
                            }
                            ThemeMode.AUTO -> {
                                onThemeChange(ReaderTheme.PAPER)
                                onSettingChanged("auto_night_mode", false)
                            }
                        }
                    },
                    modifier = Modifier.size(32.dp).testTag("Peek_ThemeMode"),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (themeMode != ThemeMode.LIGHT) colors.accent else colors.textTertiary,
                    ),
                ) {
                    Icon(
                        imageVector = themeIcon,
                        contentDescription = themeDesc,
                        modifier = Modifier.size(18.dp),
                    )
                }

                val eyeCare = prefs.colorTemperature < 5500f
                IconButton(
                    onClick = { onSettingChanged("color_temperature", if (eyeCare) 6500f else 4000f) },
                    modifier = Modifier.size(32.dp).testTag("Peek_EyeCare"),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (eyeCare) colors.accent else colors.textTertiary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = "护眼模式",
                        modifier = Modifier.size(18.dp),
                    )
                }

                val landscape = prefs.orientationLock == OrientationLock.LANDSCAPE
                IconButton(
                    onClick = {
                        onSettingChanged(
                            "orientation_lock",
                            if (landscape) OrientationLock.SYSTEM else OrientationLock.LANDSCAPE,
                        )
                    },
                    modifier = Modifier.size(32.dp).testTag("Peek_Landscape"),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (landscape) colors.accent else colors.textTertiary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ScreenRotation,
                        contentDescription = "横屏锁定",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // 主题色块
        ThemeSwatchRow(
            currentTheme = prefs.backgroundColor,
            onThemeChange = onThemeChange,
            onOpenCustomTheme = { showCustomThemeDialog = true },
            customBackgroundColor = prefs.customBackgroundColor,
        )
    }

    // 自定义主题编辑对话框
    if (showCustomThemeDialog) {
        CustomThemeDialog(
            currentBg = prefs.customBackgroundColor,
            currentText = prefs.customTextColor,
            currentTitle = prefs.customTitleColor,
            currentHeaderFooter = prefs.customHeaderFooterColor,
            onConfirm = { bg, text, title, headerFooter ->
                onCustomThemeConfirm(bg, text, title, headerFooter)
                showCustomThemeDialog = false
            },
            onDismiss = { showCustomThemeDialog = false },
        )
    }
}

/**
 * 全局 / 本书 段控（对应原型 .scope-tabs）。
 */
@Composable
private fun ScopeSegment(
    scope: SettingsScope,
    onScopeChange: (SettingsScope) -> Unit,
) {
    val colors = LocalReaderColorScheme.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.background)
            .padding(4.dp)
            .testTag("SettingsPeek_ScopeSegment"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ScopeChip("全局", scope == SettingsScope.GLOBAL) { onScopeChange(SettingsScope.GLOBAL) }
        ScopeChip("本书", scope == SettingsScope.BOOK) { onScopeChange(SettingsScope.BOOK) }
    }
}

@Composable
private fun ScopeChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalReaderColorScheme.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = if (active) onAccentColor(colors.accent) else colors.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (active) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

private enum class ThemeMode { LIGHT, DARK, AUTO }
