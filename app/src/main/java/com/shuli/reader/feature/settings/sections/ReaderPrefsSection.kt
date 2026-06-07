package com.shuli.reader.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.PageAnimConst
import com.shuli.reader.core.data.PageTurnDirConst
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.settings.SettingsUiState
import com.shuli.reader.feature.settings.SettingsViewModel
import com.shuli.reader.feature.settings.components.SettingsClickItem
import com.shuli.reader.feature.settings.components.SettingsSectionHeader
import com.shuli.reader.feature.settings.components.SettingsSwitchItem

@Composable
internal fun ReaderPrefsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onShowLineSpacingDialog: () -> Unit,
    onShowPageAnimDialog: () -> Unit,
    onShowPageDirDialog: () -> Unit,
) {
    val strings = LocalAppStrings.current

    SettingsSectionHeader(title = strings.reader.readerPreferences, icon = Icons.Outlined.Palette)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // 默认字号缩放
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.reader.defaultFontSize, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("${uiState.defaultFontSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = uiState.defaultFontSize,
                    onValueChange = { viewModel.updateDefaultFontSize(it) },
                    valueRange = 12f..24f,
                    steps = 11
                )
            }

            // 默认行距
            val lineSpacingText = when (uiState.defaultLineSpacing) {
                1.2f -> strings.reader.lineSpacingCompact
                1.8f -> strings.reader.lineSpacingWide
                else -> strings.reader.lineSpacingMedium
            }
            SettingsClickItem(
                title = strings.reader.defaultLineSpacing,
                subtitle = lineSpacingText,
                onClick = onShowLineSpacingDialog,
            )

            // 默认翻页动画
            val pageAnimText = when (uiState.defaultPageAnim) {
                PageAnimConst.OVERLAY -> strings.reader.pageAnimOverlay
                PageAnimConst.SLIDE -> strings.reader.pageAnimSlide
                PageAnimConst.SIMULATION -> strings.reader.pageAnimSimulation
                PageAnimConst.FADE -> strings.reader.pageAnimFade
                PageAnimConst.NONE -> strings.reader.pageAnimNone
                else -> uiState.defaultPageAnim
            }
            SettingsClickItem(
                title = strings.reader.defaultPageAnim,
                subtitle = pageAnimText,
                onClick = onShowPageAnimDialog,
            )

            // 段间距
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.reader.paragraphSpacing, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(String.format("%.1f em", uiState.defaultParagraphSpacing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = uiState.defaultParagraphSpacing,
                    onValueChange = { viewModel.updateDefaultParagraphSpacing(it) },
                    valueRange = 0.5f..3.0f,
                    steps = 24
                )
            }

            // 首行缩进
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.reader.firstLineIndent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(String.format("%.1f em", uiState.defaultIndent), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = uiState.defaultIndent,
                    onValueChange = { viewModel.updateDefaultIndent(it) },
                    valueRange = 0f..4f,
                    steps = 31
                )
            }

            // 翻页方向
            val pageTurnDirText = when (uiState.pageTurnDir) {
                PageTurnDirConst.HORIZONTAL -> strings.reader.pageTurnHorizontal
                PageTurnDirConst.VERTICAL -> strings.reader.pageTurnVertical
                else -> uiState.pageTurnDir
            }
            SettingsClickItem(
                title = strings.reader.pageTurnDirection,
                subtitle = pageTurnDirText,
                onClick = onShowPageDirDialog,
            )

            // 全屏模式
            SettingsSwitchItem(
                title = strings.reader.fullScreenMode,
                checked = uiState.fullScreen,
                onCheckedChange = { viewModel.updateFullScreen(it) }
            )

            // 屏幕常亮
            SettingsSwitchItem(
                title = strings.reader.keepScreenOn,
                checked = uiState.keepScreenOn,
                onCheckedChange = { viewModel.updateKeepScreenOn(it) }
            )

            // 亮度调节
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.reader.brightness, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (uiState.brightness < 0) strings.reader.brightnessFollowSystem
                        else "${(uiState.brightness * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = if (uiState.brightness < 0) 0.5f else uiState.brightness,
                    onValueChange = { viewModel.updateBrightness(it) },
                    valueRange = 0f..1f,
                    steps = 19
                )
            }
        }
    }
}
