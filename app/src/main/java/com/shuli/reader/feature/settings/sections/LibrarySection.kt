package com.shuli.reader.feature.settings.sections

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.shuli.reader.core.data.COVER_PALETTE_AUTO
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.bookshelf.component.CoverColorPickerDialog
import com.shuli.reader.feature.settings.SettingsUiState
import com.shuli.reader.feature.settings.SettingsViewModel
import com.shuli.reader.feature.settings.components.SettingsButtonItem
import com.shuli.reader.feature.settings.components.SettingsClickItem
import com.shuli.reader.feature.settings.components.SettingsSectionHeader
import com.shuli.reader.feature.settings.components.SettingsSwitchItem

@Composable
internal fun LibrarySection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    SettingsSectionHeader(title = strings.bookshelf.libraryImportSettings, icon = Icons.AutoMirrored.Outlined.LibraryBooks)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column {
            SettingsSwitchItem(
                title = strings.bookshelf.duplicateCheck,
                subtitle = strings.bookshelf.duplicateCheckDesc,
                checked = uiState.duplicateCheckEnabled,
                onCheckedChange = { viewModel.updateDuplicateCheckEnabled(it) }
            )
            SettingsSwitchItem(
                title = strings.bookshelf.importCopy,
                subtitle = strings.bookshelf.importCopyDesc,
                checked = uiState.importCopyFile,
                onCheckedChange = { viewModel.updateImportCopyFile(it) }
            )
            val unifiedPalette by viewModel.unifiedCoverPaletteFlow
                .collectAsState(initial = COVER_PALETTE_AUTO)
            val currentPaletteIndex = if (unifiedPalette == COVER_PALETTE_AUTO) null
                else unifiedPalette.toIntOrNull()?.takeIf { it in 0..19 }
            var showCoverPaletteDialog by remember { mutableStateOf(false) }
            SettingsClickItem(
                title = strings.reader.unifiedCoverColor,
                subtitle = if (currentPaletteIndex == null) strings.reader.unifiedCoverColorAuto
                    else strings.reader.unifiedCoverColorActive(currentPaletteIndex + 1),
                onClick = { showCoverPaletteDialog = true }
            )
            if (showCoverPaletteDialog) {
                CoverColorPickerDialog(
                    currentIndex = currentPaletteIndex,
                    onSelected = { idx ->
                        viewModel.setUnifiedCoverPalette(
                            idx?.toString() ?: COVER_PALETTE_AUTO
                        )
                        showCoverPaletteDialog = false
                    },
                    onDismiss = { showCoverPaletteDialog = false }
                )
            }
            SettingsButtonItem(
                title = strings.bookshelf.clearTempCache,
                subtitle = strings.bookshelf.clearTempCacheDesc,
                buttonText = strings.bookshelf.clearTempCache,
                onClick = {
                    Toast.makeText(context, strings.bookshelf.clearCacheSuccess, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
