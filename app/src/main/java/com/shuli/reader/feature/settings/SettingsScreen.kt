package com.shuli.reader.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.core.ShuLiAppContainer
import com.shuli.reader.core.data.PageAnimConst
import com.shuli.reader.core.data.PageTurnDirConst
import com.shuli.reader.core.data.SyncMethodConst
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.sync.export.BackupExporter
import com.shuli.reader.sync.export.BackupImporter
import com.shuli.reader.sync.export.ExportDatabase
import com.shuli.reader.sync.export.ImportDatabase
import com.shuli.reader.sync.export.ImportStrategy
import androidx.room.withTransaction
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.shuli.reader.ui.theme.AppBackground
import com.shuli.reader.ui.theme.AppDarkBackground
import com.shuli.reader.ui.theme.AppDarkPrimary
import com.shuli.reader.ui.theme.AppDarkTextPrimary
import com.shuli.reader.ui.theme.AppPrimary
import com.shuli.reader.ui.settings.sync.SyncSettingsScreen
import com.shuli.reader.ui.settings.sync.SyncSummaryViewModel
import com.shuli.reader.ui.settings.sync.CloudSyncSettingsScreen
import com.shuli.reader.ui.settings.sync.CloudSyncSettingsViewModel
import com.shuli.reader.ui.settings.crypto.EncryptionManagementScreen
import com.shuli.reader.ui.settings.crypto.EncryptionManagementViewModel
import com.shuli.reader.ui.devices.DeviceManagementScreen
import com.shuli.reader.ui.devices.DeviceManagementViewModel
import com.shuli.reader.ui.log.SyncLogScreen
import com.shuli.reader.ui.log.SyncLogViewModel
import com.shuli.reader.ui.export.ExportBottomSheet
import com.shuli.reader.ui.export.LocalBackupScreen
import com.shuli.reader.ui.theme.AppTextPrimary
import com.shuli.reader.ui.theme.ReaderPaperColorScheme
import com.shuli.reader.ui.testing.UiTestTags

private sealed class SettingsSubScreen {
    data object Sync : SettingsSubScreen()
    data object CloudSync : SettingsSubScreen()
    data object Encryption : SettingsSubScreen()
    data object Devices : SettingsSubScreen()
    data object Logs : SettingsSubScreen()
    data object Export : SettingsSubScreen()
    data object LocalBackup : SettingsSubScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    appContainer: ShuLiAppContainer? = null,
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    // 子页面导航状态
    var currentSubScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }

    // 各种弹窗控制状态
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLineSpacingDialog by remember { mutableStateOf(false) }
    var showPageAnimDialog by remember { mutableStateOf(false) }
    var showPageDirDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }

    // 监听 ViewModel 异步事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> {
                    Toast.makeText(context, event.message(strings), Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settings, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_BACK_BUTTON),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backIconDesc)
                    }
                }
            )
        },
        modifier = modifier.testTag(UiTestTags.SETTINGS_SCREEN)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================= 外观设置 (Appearance) =================
            item {
                SettingsSectionHeader(title = strings.appearance, icon = Icons.Outlined.ColorLens)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column {
                        // 界面语言
                        val currentLangText = when (uiState.language) {
                            "zh-TW" -> strings.languageTw
                            "en" -> strings.languageEn
                            else -> strings.languageCn
                        }
                        SettingsClickItem(
                            title = strings.languageLabel,
                            subtitle = currentLangText,
                            onClick = { showLanguageDialog = true }
                        )

                        // 界面字体
                        val currentFontText = when (uiState.appFont) {
                            "system" -> strings.appFontSystem
                            else -> strings.appFontHarmony
                        }
                        SettingsClickItem(
                            title = strings.appFontLabel,
                            subtitle = currentFontText,
                            onClick = { showFontDialog = true }
                        )

                        // 深浅主题 (带色块预览)
                        val currentThemeText = when (uiState.themeMode) {
                            "light" -> strings.themeLight
                            "dark" -> strings.themeDark
                            "paper" -> strings.themePaper
                            else -> strings.themeSystem
                        }
                        ListItem(
                            headlineContent = { Text(strings.themeModeLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(currentThemeText, style = MaterialTheme.typography.bodySmall) },
                            trailingContent = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 显示当前选中主题 of 3 点调色盘预览
                                    when (uiState.themeMode) {
                                        "light" -> ThemePreviewDots(primary = AppPrimary, background = AppBackground, text = AppTextPrimary)
                                        "dark" -> ThemePreviewDots(primary = AppDarkPrimary, background = AppDarkBackground, text = AppDarkTextPrimary)
                                        "paper" -> ThemePreviewDots(
                                            primary = ReaderPaperColorScheme.accent,
                                            background = ReaderPaperColorScheme.background,
                                            text = ReaderPaperColorScheme.textPrimary,
                                        )
                                        else -> {
                                            // 跟随系统，并排渲染亮和暗的小色盘
                                            ThemePreviewDots(primary = AppPrimary, background = AppBackground, text = AppTextPrimary)
                                        }
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { showThemeDialog = true }
                        )
                    }
                }
            }

            // ================= 阅读器显示偏好 =================
            item {
                SettingsSectionHeader(title = strings.readerPreferences, icon = Icons.Outlined.Palette)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        // 默认字号缩放
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.defaultFontSize, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                            1.2f -> strings.lineSpacingCompact
                            1.8f -> strings.lineSpacingWide
                            else -> strings.lineSpacingMedium
                        }
                        SettingsClickItem(
                            title = strings.defaultLineSpacing,
                            subtitle = lineSpacingText,
                            onClick = { showLineSpacingDialog = true }
                        )

                        // 默认翻页动画
                        val pageAnimText = when (uiState.defaultPageAnim) {
                            PageAnimConst.OVERLAY -> strings.pageAnimOverlay
                            PageAnimConst.SLIDE -> strings.pageAnimSlide
                            PageAnimConst.SIMULATION -> strings.pageAnimSimulation
                            PageAnimConst.FADE -> strings.pageAnimFade
                            PageAnimConst.NONE -> strings.pageAnimNone
                            else -> uiState.defaultPageAnim
                        }
                        SettingsClickItem(
                            title = strings.defaultPageAnim,
                            subtitle = pageAnimText,
                            onClick = { showPageAnimDialog = true }
                        )

                        // 段间距
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.paragraphSpacing, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                                Text(strings.firstLineIndent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                            PageTurnDirConst.HORIZONTAL -> strings.pageTurnHorizontal
                            PageTurnDirConst.VERTICAL -> strings.pageTurnVertical
                            else -> uiState.pageTurnDir
                        }
                        SettingsClickItem(
                            title = strings.pageTurnDirection,
                            subtitle = pageTurnDirText,
                            onClick = { showPageDirDialog = true }
                        )

                        // 全屏模式
                        SettingsSwitchItem(
                            title = strings.fullScreenMode,
                            checked = uiState.fullScreen,
                            onCheckedChange = { viewModel.updateFullScreen(it) }
                        )

                        // 屏幕常亮
                        SettingsSwitchItem(
                            title = strings.keepScreenOn,
                            checked = uiState.keepScreenOn,
                            onCheckedChange = { viewModel.updateKeepScreenOn(it) }
                        )

                        // 亮度调节
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.brightness, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (uiState.brightness < 0) strings.brightnessFollowSystem
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

            // ================= 书库与导入设置 =================
            item {
                SettingsSectionHeader(title = strings.libraryImportSettings, icon = Icons.AutoMirrored.Outlined.LibraryBooks)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column {
                        SettingsSwitchItem(
                            title = strings.duplicateCheck,
                            subtitle = strings.duplicateCheckDesc,
                            checked = uiState.duplicateCheckEnabled,
                            onCheckedChange = { viewModel.updateDuplicateCheckEnabled(it) }
                        )
                        SettingsSwitchItem(
                            title = strings.importCopy,
                            subtitle = strings.importCopyDesc,
                            checked = uiState.importCopyFile,
                            onCheckedChange = { viewModel.updateImportCopyFile(it) }
                        )
                        val unifiedPalette by viewModel.unifiedCoverPaletteFlow
                            .collectAsState(initial = com.shuli.reader.core.data.COVER_PALETTE_AUTO)
                        val currentPaletteIndex = if (unifiedPalette == com.shuli.reader.core.data.COVER_PALETTE_AUTO) null
                            else unifiedPalette.toIntOrNull()?.takeIf { it in 0..19 }
                        var showCoverPaletteDialog by remember { mutableStateOf(false) }
                        SettingsClickItem(
                            title = strings.unifiedCoverColor,
                            subtitle = if (currentPaletteIndex == null) strings.unifiedCoverColorAuto
                                else strings.unifiedCoverColorActive(currentPaletteIndex + 1),
                            onClick = { showCoverPaletteDialog = true }
                        )
                        if (showCoverPaletteDialog) {
                            com.shuli.reader.feature.bookshelf.component.CoverColorPickerDialog(
                                currentIndex = currentPaletteIndex,
                                onSelected = { idx ->
                                    viewModel.setUnifiedCoverPalette(
                                        idx?.toString() ?: com.shuli.reader.core.data.COVER_PALETTE_AUTO
                                    )
                                    showCoverPaletteDialog = false
                                },
                                onDismiss = { showCoverPaletteDialog = false }
                            )
                        }
                        SettingsButtonItem(
                            title = strings.clearTempCache,
                            subtitle = strings.clearTempCacheDesc,
                            buttonText = strings.clearTempCache,
                            onClick = {
                                Toast.makeText(context, strings.clearCacheSuccess, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // ================= 阅读统计 =================
            item {
                SettingsSectionHeader(title = strings.readingStats, icon = Icons.AutoMirrored.Outlined.ShowChart)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        SettingsSwitchItem(
                            title = strings.statsEnable,
                            subtitle = strings.statsEnableDesc,
                            checked = uiState.readingTimeEnabled,
                            onCheckedChange = { viewModel.updateReadingTimeEnabled(it) }
                        )

                        if (uiState.readingTimeEnabled) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(strings.statsDailyTarget, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(strings.readingTargetMinutes(uiState.readingDailyTarget), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = uiState.readingDailyTarget.toFloat(),
                                    onValueChange = { viewModel.updateReadingDailyTarget(it.toInt()) },
                                    valueRange = 10f..180f,
                                    steps = 17
                                )
                            }
                        }
                    }
                }
            }

            // ================= 同步设置 =================
            item {
                SettingsSectionHeader(title = strings.syncSettings, icon = Icons.Outlined.Sync)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        val syncMethodText = when (uiState.syncMethod) {
                            SyncMethodConst.LOCAL -> strings.syncMethodLocal
                            SyncMethodConst.WEBDAV -> strings.syncMethodWebdav
                            else -> uiState.syncMethod
                        }
                        SettingsClickItem(
                            title = strings.syncMethod,
                            subtitle = syncMethodText,
                            onClick = { currentSubScreen = SettingsSubScreen.Sync }
                        )
                        if (uiState.syncMethod == SyncMethodConst.WEBDAV) {
                            SettingsClickItem(
                                title = strings.syncAndBackup,
                                subtitle = strings.syncAndBackupDesc,
                                onClick = { currentSubScreen = SettingsSubScreen.Sync }
                            )
                        }
                        SettingsClickItem(
                            title = strings.syncMethodLocal,
                            subtitle = strings.localBackupDesc,
                            onClick = { currentSubScreen = SettingsSubScreen.LocalBackup }
                        )
                    }
                }
            }

            // ================= 朗读设置 =================
            item {
                SettingsSectionHeader(title = strings.ttsSettings, icon = Icons.AutoMirrored.Outlined.VolumeUp)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.ttsSpeed, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(String.format("%.1fx", uiState.ttsSpeed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = uiState.ttsSpeed,
                                onValueChange = { viewModel.updateTtsSpeed(it) },
                                valueRange = 0.5f..3.0f,
                                steps = 25
                            )
                        }

                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.ttsPitch, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(String.format("%.1f", uiState.ttsPitch), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = uiState.ttsPitch,
                                onValueChange = { viewModel.updateTtsPitch(it) },
                                valueRange = 0.5f..2.0f,
                                steps = 15
                            )
                        }

                        SettingsSwitchItem(
                            title = strings.ttsAutoPage,
                            checked = uiState.ttsAutoPage,
                            onCheckedChange = { viewModel.updateTtsAutoPage(it) }
                        )

                        SettingsSwitchItem(
                            title = strings.ttsHighlightSentence,
                            checked = uiState.ttsHighlightSentence,
                            onCheckedChange = { viewModel.updateTtsHighlightSentence(it) }
                        )
                    }
                }
            }

            // ================= 高级设置 =================
            item {
                SettingsSectionHeader(title = strings.advancedSettings, icon = Icons.Outlined.Settings)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                    Column {
                        SettingsSwitchItem(
                            title = strings.gpuAcceleration,
                            checked = uiState.gpuAcceleration,
                            onCheckedChange = { viewModel.updateGpuAcceleration(it) }
                        )
                        SettingsSwitchItem(
                            title = strings.loggingEnabled,
                            checked = uiState.loggingEnabled,
                            onCheckedChange = { viewModel.updateLoggingEnabled(it) }
                        )
                        SettingsButtonItem(
                            title = strings.resetAllSettings,
                            subtitle = strings.resetAllSettingsDesc,
                            buttonText = strings.resetAllSettings,
                            onClick = { showResetDialog = true }
                        )
                    }
                }
            }

            // ================= 关于与版权 =================
            item {
                SettingsSectionHeader(title = strings.aboutLabel, icon = Icons.Outlined.Info)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        // 开发者卡片
                        ListItem(
                            headlineContent = { Text(strings.developerLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("jinghu-moon", style = MaterialTheme.typography.bodySmall) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        // 版本号
                        ListItem(
                            headlineContent = { Text(strings.versionLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("v1.0.0 (Build 20260523)", style = MaterialTheme.typography.bodySmall) },
                            trailingContent = {
                                OutlinedButton(onClick = {
                                    Toast.makeText(context, strings.alreadyLatestVersion, Toast.LENGTH_SHORT).show()
                                }) {
                                    Text(strings.checkUpdate, style = MaterialTheme.typography.labelMedium)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        // 项目地址
                        SettingsClickItem(
                            title = "GitHub",
                            subtitle = "https://github.com/jinghu-moon/ShuLi-Reader",
                            onClick = { uriHandler.openUri("https://github.com/jinghu-moon/ShuLi-Reader") }
                        )

                        // 开源许可证 (AGPL-3.0)
                        SettingsClickItem(
                            title = strings.licenseLabel,
                            subtitle = "AGPL-3.0 License",
                            onClick = { showLicenseDialog = true }
                        )
                    }
                }
            }
        }
    }

    // ================= 子页面导航 =================
    when (currentSubScreen) {
        is SettingsSubScreen.Sync -> {
            val syncSummaryViewModel = remember {
                SyncSummaryViewModel(stateMachine = com.shuli.reader.sync.state.SyncStateMachine())
            }
            SyncSettingsScreen(
                viewModel = syncSummaryViewModel,
                onBackClick = { currentSubScreen = null },
                onNavigateToCloudSync = { currentSubScreen = SettingsSubScreen.CloudSync },
                onNavigateToEncryption = { currentSubScreen = SettingsSubScreen.Encryption },
                onNavigateToDevices = { currentSubScreen = SettingsSubScreen.Devices },
                onNavigateToLogs = { currentSubScreen = SettingsSubScreen.Logs },
                onNavigateToExport = { currentSubScreen = SettingsSubScreen.Export },
            )
        }
        is SettingsSubScreen.CloudSync -> {
            val cloudSyncViewModel = remember {
                CloudSyncSettingsViewModel(userPreferences = appContainer?.userPreferences)
            }
            CloudSyncSettingsScreen(
                viewModel = cloudSyncViewModel,
                onBackClick = { currentSubScreen = SettingsSubScreen.Sync },
                onNavigateToEncryption = { currentSubScreen = SettingsSubScreen.Encryption },
                onNavigateToDevices = { currentSubScreen = SettingsSubScreen.Devices },
                onNavigateToLogs = { currentSubScreen = SettingsSubScreen.Logs },
            )
        }
        is SettingsSubScreen.Encryption -> {
            val encryptionViewModel = remember { EncryptionManagementViewModel() }
            EncryptionManagementScreen(
                viewModel = encryptionViewModel,
                onBackClick = { currentSubScreen = SettingsSubScreen.Sync },
            )
        }
        is SettingsSubScreen.Devices -> {
            val devicesViewModel = remember { DeviceManagementViewModel() }
            DeviceManagementScreen(
                viewModel = devicesViewModel,
                onBackClick = { currentSubScreen = SettingsSubScreen.Sync },
            )
        }
        is SettingsSubScreen.Logs -> {
            val logsViewModel = remember { SyncLogViewModel() }
            SyncLogScreen(
                viewModel = logsViewModel,
                onBackClick = { currentSubScreen = SettingsSubScreen.Sync },
            )
        }
        is SettingsSubScreen.Export -> {
            var showExportSheet by remember { mutableStateOf(true) }
            if (showExportSheet) {
                ExportBottomSheet(
                    onDismiss = {
                        showExportSheet = false
                        currentSubScreen = SettingsSubScreen.Sync
                    },
                    onExport = { options ->
                        // TODO: 触发导出操作
                        showExportSheet = false
                        currentSubScreen = SettingsSubScreen.Sync
                    },
                )
            }
        }
        is SettingsSubScreen.LocalBackup -> {
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            var isExporting by remember { mutableStateOf(false) }
            var isImporting by remember { mutableStateOf(false) }
            var exportResult by remember { mutableStateOf<String?>(null) }
            var importResult by remember { mutableStateOf<String?>(null) }

            LocalBackupScreen(
                onBackClick = { currentSubScreen = null },
                onExport = { options ->
                    if (!isExporting && appContainer != null) {
                        coroutineScope.launch {
                            isExporting = true
                            exportResult = null
                            try {
                                val database = appContainer.database
                                val exportDb = object : ExportDatabase {
                                    override suspend fun getAllBooks(): List<BookEntity> = database.bookDao().getAllBooksSync()
                                    override suspend fun getAllBookmarks(): List<BookmarkEntity> = database.bookmarkDao().queryAllActive()
                                    override suspend fun getAllNotes(): List<NoteEntity> = database.noteDao().queryAllActive()
                                    override suspend fun getAllProgress(): List<ReadingProgressEntity> = database.readingProgressDao().queryAllActive()
                                }
                                val exporter = BackupExporter(exportDb, context)
                                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                                val fileName = "shuli_backup_${dateFormat.format(Date())}.zip"

                                // 导出到临时文件
                                val tempFile = withContext(Dispatchers.IO) {
                                    File.createTempFile("shuli_export_", ".zip", context.cacheDir).also {
                                        exporter.export(it, options)
                                    }
                                }

                                val customDir = uiState.backupLocation
                                if (customDir.isNotEmpty()) {
                                    // 自定义目录：使用 DocumentFile 拷贝到 SAF 目录
                                    val treeUri = Uri.parse(customDir)
                                    val docDir = DocumentFile.fromTreeUri(context, treeUri)
                                    if (docDir != null && docDir.canWrite()) {
                                        val targetFile = docDir.createFile("application/zip", fileName)
                                        if (targetFile != null) {
                                            withContext(Dispatchers.IO) {
                                                context.contentResolver.openOutputStream(targetFile.uri)?.use { out ->
                                                    tempFile.inputStream().use { inp ->
                                                        inp.copyTo(out)
                                                    }
                                                } ?: throw IllegalStateException("无法写入目标目录")
                                            }
                                            exportResult = "导出成功：自定义目录"
                                        } else {
                                            exportResult = "导出失败：无法创建文件"
                                        }
                                    } else {
                                        exportResult = "导出失败：目录无写入权限，请重新选择"
                                    }
                                } else {
                                    // 默认目录：应用私有目录
                                    val backupDir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
                                    val outputFile = File(backupDir, fileName)
                                    withContext(Dispatchers.IO) {
                                        tempFile.copyTo(outputFile, overwrite = true)
                                    }
                                    exportResult = "导出成功：${outputFile.parent}"
                                }

                                // 清理临时文件
                                withContext(Dispatchers.IO) { tempFile.delete() }
                            } catch (e: Exception) {
                                exportResult = "导出失败：${e.message}"
                            } finally {
                                isExporting = false
                            }
                        }
                    }
                },
                onImport = { uri ->
                    if (!isImporting && appContainer != null) {
                        coroutineScope.launch {
                            isImporting = true
                            importResult = null
                            try {
                                val database = appContainer.database
                                val importDb = object : ImportDatabase {
                                    override suspend fun getAllBooks(): List<BookEntity> = database.bookDao().getAllBooksSync()
                                    override suspend fun getAllBookmarks(): List<BookmarkEntity> = database.bookmarkDao().queryAllActive()
                                    override suspend fun getAllNotes(): List<NoteEntity> = database.noteDao().queryAllActive()
                                    override suspend fun getAllProgress(): List<ReadingProgressEntity> = database.readingProgressDao().queryAllActive()
                                    override suspend fun upsertBook(book: BookEntity) = database.bookDao().upsertBook(book)
                                    override suspend fun clearBooks() = database.bookDao().deleteAllBooks()
                                    override suspend fun upsertBookmark(bookmark: BookmarkEntity) = database.bookmarkDao().upsertBookmark(bookmark)
                                    override suspend fun clearBookmarks() = database.bookmarkDao().deleteAllBookmarks()
                                    override suspend fun upsertNote(note: NoteEntity) = database.noteDao().upsertNote(note)
                                    override suspend fun clearNotes() = database.noteDao().deleteAllNotes()
                                    override suspend fun upsertProgress(progress: ReadingProgressEntity) = database.readingProgressDao().upsertProgress(progress)
                                    override suspend fun clearProgress() = database.readingProgressDao().deleteAllProgress()
                                    override suspend fun runInTransaction(block: suspend () -> Unit) {
                                        database.withTransaction { block() }
                                    }
                                }
                                val importer = BackupImporter(db = importDb)

                                // 从 SAF URI 复制到临时文件再导入
                                val tempFile = withContext(Dispatchers.IO) {
                                    File.createTempFile("shuli_import_", ".zip", context.cacheDir).also { file ->
                                        context.contentResolver.openInputStream(uri)?.use { input ->
                                            file.outputStream().use { output -> input.copyTo(output) }
                                        } ?: throw IllegalStateException("无法读取备份文件")
                                    }
                                }

                                try {
                                    importer.import(tempFile, strategy = ImportStrategy.MERGE)
                                    importResult = "导入成功"
                                } finally {
                                    withContext(Dispatchers.IO) { tempFile.delete() }
                                }
                            } catch (e: Exception) {
                                importResult = "导入失败：${e.message}"
                            } finally {
                                isImporting = false
                            }
                        }
                    }
                },
                isExporting = isExporting,
                isImporting = isImporting,
                exportResult = exportResult,
                importResult = importResult,
                autoBackupEnabled = uiState.autoBackupEnabled,
                backupOnAppStart = uiState.backupOnAppStart,
                backupOnAppExit = uiState.backupOnAppExit,
                backupIntervalHours = uiState.backupIntervalHours,
                backupLocation = uiState.backupLocation,
                onAutoBackupEnabledChange = { viewModel.updateAutoBackupEnabled(it) },
                onBackupOnAppStartChange = { viewModel.updateBackupOnAppStart(it) },
                onBackupOnAppExitChange = { viewModel.updateBackupOnAppExit(it) },
                onBackupIntervalChange = { viewModel.updateBackupIntervalHours(it) },
                onBackupLocationChange = { viewModel.updateBackupLocation(it) },
            )
        }
        null -> {}
    }

    // ================= 以下为弹窗 Dialog 组 =================

    // 1. 语言切换
    if (showLanguageDialog) {
        val options = listOf("zh-CN", "zh-TW", "en")
        val optionLabels = listOf(strings.languageCn, strings.languageTw, strings.languageEn)
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(strings.languageLabel) },
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
                                showLanguageDialog = false
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
        val optionLabels = listOf(strings.appFontHarmony, strings.appFontSystem)
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text(strings.appFontLabel) },
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
                                showFontDialog = false
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
        val optionLabels = listOf(strings.themeSystem, strings.themeLight, strings.themeDark, strings.themePaper)
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(strings.themeModeLabel) },
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
                                showThemeDialog = false
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
        val optionLabels = listOf(strings.lineSpacingCompact, strings.lineSpacingMedium, strings.lineSpacingWide)
        AlertDialog(
            onDismissRequest = { showLineSpacingDialog = false },
            title = { Text(strings.defaultLineSpacing) },
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
                                showLineSpacingDialog = false
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
            PageAnimConst.FADE,
            PageAnimConst.NONE
        )
        val optionLabels = listOf(
            strings.pageAnimOverlay,
            strings.pageAnimSlide,
            strings.pageAnimSimulation,
            strings.pageAnimFade,
            strings.pageAnimNone
        )
        AlertDialog(
            onDismissRequest = { showPageAnimDialog = false },
            title = { Text(strings.defaultPageAnim) },
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
                                showPageAnimDialog = false
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
        val options = listOf(
            PageTurnDirConst.HORIZONTAL,
            PageTurnDirConst.VERTICAL
        )
        val optionLabels = listOf(
            strings.pageTurnHorizontal,
            strings.pageTurnVertical
        )
        AlertDialog(
            onDismissRequest = { showPageDirDialog = false },
            title = { Text(strings.pageTurnDirection) },
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
                                showPageDirDialog = false
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
            onDismissRequest = { showResetDialog = false },
            title = { Text(strings.resetAllSettings) },
            text = { Text(strings.resetAllSettingsDesc) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAllSettings()
                    showResetDialog = false
                }) {
                    Text(strings.resetAllSettings, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(strings.backIconDesc)
                }
            }
        )
    }

    // 8. 许可证说明
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text(strings.licenseLabel) },
            text = {
                Column {
                    Text("ShuLi Reader is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("This ensures open-source distribution, modification, and network interaction transparency.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(strings.backIconDesc)
                }
            }
        )
    }
}

// ================= 以下为辅助子项 UI 组件 =================

@Composable
fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SettingsClickItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun SettingsSwitchItem(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SettingsButtonItem(title: String, subtitle: String, buttonText: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            OutlinedButton(onClick = onClick) {
                Text(buttonText, style = MaterialTheme.typography.labelMedium)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun ThemePreviewDots(primary: Color, background: Color, text: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(background, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(primary, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(text, CircleShape)
        )
    }
}
