package com.shuli.reader.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.PageAnimConst
import com.shuli.reader.core.data.PageTurnDirConst
import com.shuli.reader.core.i18n.LocalAppStrings

@Composable
internal fun SettingsDialogs(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    showLanguageDialog: Boolean,
    onLanguageDialogChange: (Boolean) -> Unit,
    showFontDialog: Boolean,
    onFontDialogChange: (Boolean) -> Unit,
    showThemeDialog: Boolean,
    onThemeDialogChange: (Boolean) -> Unit,
    showLineSpacingDialog: Boolean,
    onLineSpacingDialogChange: (Boolean) -> Unit,
    showPageAnimDialog: Boolean,
    onPageAnimDialogChange: (Boolean) -> Unit,
    showPageDirDialog: Boolean,
    onPageDirDialogChange: (Boolean) -> Unit,
    showResetDialog: Boolean,
    onResetDialogChange: (Boolean) -> Unit,
    showLicenseDialog: Boolean,
    onLicenseDialogChange: (Boolean) -> Unit,
) {
    val strings = LocalAppStrings.current

    // 1. 语言切换
    if (showLanguageDialog) {
        val options = listOf("zh-CN", "zh-TW", "en")
        val optionLabels = listOf(strings.common.languageCn, strings.common.languageTw, strings.common.languageEn)
        AlertDialog(
            onDismissRequest = { onLanguageDialogChange(false) },
            title = { Text(strings.common.languageLabel) },
            text = {
                Column {
                    options.forEachIndexed { index, code ->
                        ListItem(
                            headlineContent = { Text(optionLabels[index]) },
                            trailingContent = {
                                if (uiState.language == code) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.updateLanguage(code)
                                onLanguageDialogChange(false)
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // 2. 界面字体
    if (showFontDialog) {
        val options = listOf("harmony", "system")
        val optionLabels = listOf(strings.common.appFontHarmony, strings.common.appFontSystem)
        AlertDialog(
            onDismissRequest = { onFontDialogChange(false) },
            title = { Text(strings.common.appFontLabel) },
            text = {
                Column {
                    options.forEachIndexed { index, code ->
                        ListItem(
                            headlineContent = { Text(optionLabels[index]) },
                            trailingContent = {
                                if (uiState.appFont == code) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.updateAppFont(code)
                                onFontDialogChange(false)
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // 3. 主题选择
    if (showThemeDialog) {
        val options = listOf("system", "light", "dark", "paper")
        val optionLabels = listOf(strings.common.themeSystem, strings.common.themeLight, strings.common.themeDark, strings.common.themePaper)
        AlertDialog(
            onDismissRequest = { onThemeDialogChange(false) },
            title = { Text(strings.common.themeModeLabel) },
            text = {
                Column {
                    options.forEachIndexed { index, code ->
                        ListItem(
                            headlineContent = { Text(optionLabels[index]) },
                            trailingContent = {
                                if (uiState.themeMode == code) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.updateThemeMode(code)
                                onThemeDialogChange(false)
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // 4. 行距选择
    if (showLineSpacingDialog) {
        val options = listOf(1.2f, 1.5f, 1.8f)
        val optionLabels = listOf(strings.reader.lineSpacingCompact, strings.reader.lineSpacingMedium, strings.reader.lineSpacingWide)
        AlertDialog(
            onDismissRequest = { onLineSpacingDialogChange(false) },
            title = { Text(strings.reader.defaultLineSpacing) },
            text = {
                Column {
                    options.forEachIndexed { index, size ->
                        ListItem(
                            headlineContent = { Text(optionLabels[index]) },
                            trailingContent = {
                                if (uiState.defaultLineSpacing == size) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.updateDefaultLineSpacing(size)
                                onLineSpacingDialogChange(false)
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // 5. 翻页动画
    if (showPageAnimDialog) {
        val options = listOf(
            PageAnimConst.OVERLAY,
            PageAnimConst.SLIDE,
            PageAnimConst.SIMULATION,
            PageAnimConst.VERTICAL_SLIDE,
            PageAnimConst.SCROLL,
            PageAnimConst.FADE,
            PageAnimConst.NONE
        )
        val optionLabels = listOf(
            strings.reader.pageAnimOverlay,
            strings.reader.pageAnimSlide,
            strings.reader.pageAnimSimulation,
            strings.reader.pageAnimTypeVerticalSlide,
            strings.reader.pageAnimTypeScroll,
            strings.reader.pageAnimFade,
            strings.reader.pageAnimNone
        )
        AlertDialog(
            onDismissRequest = { onPageAnimDialogChange(false) },
            title = { Text(strings.reader.defaultPageAnim) },
            text = {
                Column {
                    options.forEachIndexed { index, code ->
                        ListItem(
                            headlineContent = { Text(optionLabels[index]) },
                            trailingContent = {
                                if (uiState.defaultPageAnim == code) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.updateDefaultPageAnim(code)
                                onPageAnimDialogChange(false)
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // 6. 翻页方向
    if (showPageDirDialog) {
        val options = listOf(PageTurnDirConst.HORIZONTAL, PageTurnDirConst.VERTICAL)
        val optionLabels = listOf(strings.reader.pageTurnHorizontal, strings.reader.pageTurnVertical)
        AlertDialog(
            onDismissRequest = { onPageDirDialogChange(false) },
            title = { Text(strings.reader.pageTurnDirection) },
            text = {
                Column {
                    options.forEachIndexed { index, code ->
                        ListItem(
                            headlineContent = { Text(optionLabels[index]) },
                            trailingContent = {
                                if (uiState.pageTurnDir == code) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable {
                                viewModel.updatePageTurnDir(code)
                                onPageDirDialogChange(false)
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // 7. 重置设置确认
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { onResetDialogChange(false) },
            title = { Text(strings.settings.resetAllSettings) },
            text = { Text(strings.settings.resetAllSettingsDesc) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAllSettings()
                    onResetDialogChange(false)
                }) {
                    Text(strings.settings.resetAllSettings, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onResetDialogChange(false) }) {
                    Text(strings.common.backIconDesc)
                }
            }
        )
    }

    // 8. 许可证说明
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { onLicenseDialogChange(false) },
            title = { Text(strings.settings.licenseLabel) },
            text = {
                Column {
                    Text("ShuLi Reader is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("This ensures open-source distribution, modification, and network interaction transparency.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { onLicenseDialogChange(false) }) {
                    Text(strings.common.backIconDesc)
                }
            }
        )
    }
}
