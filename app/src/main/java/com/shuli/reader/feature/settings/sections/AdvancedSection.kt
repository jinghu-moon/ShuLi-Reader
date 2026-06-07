package com.shuli.reader.feature.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.settings.SettingsUiState
import com.shuli.reader.feature.settings.SettingsViewModel
import com.shuli.reader.feature.settings.components.SettingsButtonItem
import com.shuli.reader.feature.settings.components.SettingsSectionHeader
import com.shuli.reader.feature.settings.components.SettingsSwitchItem

@Composable
internal fun AdvancedSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onShowResetDialog: () -> Unit,
) {
    val strings = LocalAppStrings.current

    SettingsSectionHeader(title = strings.settings.advancedSettings, icon = Icons.Outlined.Settings)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column {
            SettingsSwitchItem(
                title = strings.settings.gpuAcceleration,
                checked = uiState.gpuAcceleration,
                onCheckedChange = { viewModel.updateGpuAcceleration(it) }
            )
            SettingsSwitchItem(
                title = strings.settings.loggingEnabled,
                checked = uiState.loggingEnabled,
                onCheckedChange = { viewModel.updateLoggingEnabled(it) }
            )
            SettingsButtonItem(
                title = strings.settings.resetAllSettings,
                subtitle = strings.settings.resetAllSettingsDesc,
                buttonText = strings.settings.resetAllSettings,
                onClick = onShowResetDialog,
            )
        }
    }
}
