package com.shuli.reader.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.ShuLiAppContainer
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.settings.components.SettingsClickItem
import com.shuli.reader.feature.settings.components.SettingsSectionHeader
import com.shuli.reader.feature.settings.sections.AboutSection
import com.shuli.reader.feature.settings.sections.AdvancedSection
import com.shuli.reader.feature.settings.sections.AppearanceSection
import com.shuli.reader.feature.settings.sections.LibrarySection
import com.shuli.reader.feature.settings.sections.ReaderPrefsSection
import com.shuli.reader.feature.settings.sections.StatsSection
import com.shuli.reader.feature.settings.sections.SyncSection
import com.shuli.reader.ui.testing.UiTestTags

// ================= 子页面导航路由 =================

internal sealed class SettingsSubScreen {
    data object Sync : SettingsSubScreen()
    data object CloudSync : SettingsSubScreen()
    data object Encryption : SettingsSubScreen()
    data object Devices : SettingsSubScreen()
    data object Logs : SettingsSubScreen()
    data object Export : SettingsSubScreen()
    data object LocalBackup : SettingsSubScreen()
    data object DictManagement : SettingsSubScreen()
    data object WordBook : SettingsSubScreen()
    data object DictHistory : SettingsSubScreen()
}

// ================= 主屏幕（薄容器） =================

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

    var currentSubScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLineSpacingDialog by remember { mutableStateOf(false) }
    var showPageAnimDialog by remember { mutableStateOf(false) }
    var showPageDirDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }

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
                title = { Text(strings.common.settings, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag(UiTestTags.SETTINGS_BACK_BUTTON),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.common.backIconDesc)
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
            item {
                AppearanceSection(
                    uiState = uiState,
                    onShowLanguageDialog = { showLanguageDialog = true },
                    onShowFontDialog = { showFontDialog = true },
                    onShowThemeDialog = { showThemeDialog = true },
                )
            }
            item {
                ReaderPrefsSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowLineSpacingDialog = { showLineSpacingDialog = true },
                    onShowPageAnimDialog = { showPageAnimDialog = true },
                    onShowPageDirDialog = { showPageDirDialog = true },
                )
            }
            item { LibrarySection(uiState = uiState, viewModel = viewModel) }
            item { StatsSection(uiState = uiState, viewModel = viewModel) }
            item {
                SyncSection(
                    uiState = uiState,
                    onNavigateToSync = { currentSubScreen = SettingsSubScreen.Sync },
                    onNavigateToLocalBackup = { currentSubScreen = SettingsSubScreen.LocalBackup },
                )
            }
            item {
                DictionarySection(
                    onNavigateToDictManagement = { currentSubScreen = SettingsSubScreen.DictManagement },
                    onNavigateToWordBook = { currentSubScreen = SettingsSubScreen.WordBook },
                    onNavigateToDictHistory = { currentSubScreen = SettingsSubScreen.DictHistory },
                )
            }
            item {
                AdvancedSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowResetDialog = { showResetDialog = true },
                )
            }
            item { AboutSection(onShowLicenseDialog = { showLicenseDialog = true }) }
        }
    }

    SubScreenNavigation(
        currentSubScreen = currentSubScreen,
        onSubScreenChange = { currentSubScreen = it },
        appContainer = appContainer,
        uiState = uiState,
        viewModel = viewModel,
    )

    SettingsDialogs(
        uiState = uiState,
        viewModel = viewModel,
        showLanguageDialog = showLanguageDialog,
        onLanguageDialogChange = { showLanguageDialog = it },
        showFontDialog = showFontDialog,
        onFontDialogChange = { showFontDialog = it },
        showThemeDialog = showThemeDialog,
        onThemeDialogChange = { showThemeDialog = it },
        showLineSpacingDialog = showLineSpacingDialog,
        onLineSpacingDialogChange = { showLineSpacingDialog = it },
        showPageAnimDialog = showPageAnimDialog,
        onPageAnimDialogChange = { showPageAnimDialog = it },
        showPageDirDialog = showPageDirDialog,
        onPageDirDialogChange = { showPageDirDialog = it },
        showResetDialog = showResetDialog,
        onResetDialogChange = { showResetDialog = it },
        showLicenseDialog = showLicenseDialog,
        onLicenseDialogChange = { showLicenseDialog = it },
    )
}

// ================= 词典与生词本 Section =================

@Composable
private fun DictionarySection(
    onNavigateToDictManagement: () -> Unit,
    onNavigateToWordBook: () -> Unit,
    onNavigateToDictHistory: () -> Unit,
) {
    val strings = LocalAppStrings.current

    SettingsSectionHeader(title = "词典与生词本", icon = Icons.Outlined.MenuBook)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier.padding(bottom = 24.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            SettingsClickItem(
                title = "词典管理",
                subtitle = "导入和管理词典文件",
                onClick = onNavigateToDictManagement,
            )
            SettingsClickItem(
                title = "生词本",
                subtitle = "查看收藏的生词",
                onClick = onNavigateToWordBook,
            )
            SettingsClickItem(
                title = "查词历史",
                subtitle = "查看历史查询记录",
                onClick = onNavigateToDictHistory,
            )
        }
    }
}
