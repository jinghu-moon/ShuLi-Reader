package com.shuli.reader.feature.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.settings.SettingsUiState
import com.shuli.reader.feature.settings.components.SettingsClickItem
import com.shuli.reader.feature.settings.components.SettingsSectionHeader
import com.shuli.reader.feature.settings.components.ThemePreviewDots
import com.shuli.reader.ui.theme.AppBackground
import com.shuli.reader.ui.theme.AppDarkBackground
import com.shuli.reader.ui.theme.AppDarkPrimary
import com.shuli.reader.ui.theme.AppDarkTextPrimary
import com.shuli.reader.ui.theme.AppPrimary
import com.shuli.reader.ui.theme.AppTextPrimary
import com.shuli.reader.ui.theme.ReaderPaperColorScheme

@Composable
internal fun AppearanceSection(
    uiState: SettingsUiState,
    onShowLanguageDialog: () -> Unit,
    onShowFontDialog: () -> Unit,
    onShowThemeDialog: () -> Unit,
) {
    val strings = LocalAppStrings.current

    SettingsSectionHeader(title = strings.common.appearance, icon = Icons.Outlined.ColorLens)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column {
            // 界面语言
            val currentLangText = when (uiState.language) {
                "zh-TW" -> strings.common.languageTw
                "en" -> strings.common.languageEn
                else -> strings.common.languageCn
            }
            SettingsClickItem(
                title = strings.common.languageLabel,
                subtitle = currentLangText,
                onClick = onShowLanguageDialog,
            )

            // 界面字体
            val currentFontText = when (uiState.appFont) {
                "system" -> strings.common.appFontSystem
                else -> strings.common.appFontHarmony
            }
            SettingsClickItem(
                title = strings.common.appFontLabel,
                subtitle = currentFontText,
                onClick = onShowFontDialog,
            )

            // 深浅主题 (带色块预览)
            val currentThemeText = when (uiState.themeMode) {
                "light" -> strings.common.themeLight
                "dark" -> strings.common.themeDark
                "paper" -> strings.common.themePaper
                else -> strings.common.themeSystem
            }
            ListItem(
                headlineContent = { Text(strings.common.themeModeLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(currentThemeText, style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (uiState.themeMode) {
                            "light" -> ThemePreviewDots(primary = AppPrimary, background = AppBackground, text = AppTextPrimary)
                            "dark" -> ThemePreviewDots(primary = AppDarkPrimary, background = AppDarkBackground, text = AppDarkTextPrimary)
                            "paper" -> ThemePreviewDots(
                                primary = ReaderPaperColorScheme.accent,
                                background = ReaderPaperColorScheme.background,
                                text = ReaderPaperColorScheme.textPrimary,
                            )
                            else -> ThemePreviewDots(primary = AppPrimary, background = AppBackground, text = AppTextPrimary)
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onShowThemeDialog() }
            )
        }
    }
}
