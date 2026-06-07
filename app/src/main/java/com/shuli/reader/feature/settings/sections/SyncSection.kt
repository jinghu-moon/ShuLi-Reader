package com.shuli.reader.feature.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.SyncMethodConst
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.settings.SettingsUiState
import com.shuli.reader.feature.settings.components.SettingsClickItem
import com.shuli.reader.feature.settings.components.SettingsSectionHeader

@Composable
internal fun SyncSection(
    uiState: SettingsUiState,
    onNavigateToSync: () -> Unit,
    onNavigateToLocalBackup: () -> Unit,
) {
    val strings = LocalAppStrings.current

    SettingsSectionHeader(title = strings.sync.syncSettings, icon = Icons.Outlined.Sync)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            val syncMethodText = when (uiState.syncMethod) {
                SyncMethodConst.LOCAL -> strings.sync.syncMethodLocal
                SyncMethodConst.WEBDAV -> strings.sync.syncMethodWebdav
                else -> uiState.syncMethod
            }
            SettingsClickItem(
                title = strings.sync.syncMethod,
                subtitle = syncMethodText,
                onClick = onNavigateToSync,
            )
            if (uiState.syncMethod == SyncMethodConst.WEBDAV) {
                SettingsClickItem(
                    title = strings.sync.syncAndBackup,
                    subtitle = strings.sync.syncAndBackupDesc,
                    onClick = onNavigateToSync,
                )
            }
            SettingsClickItem(
                title = strings.sync.syncMethodLocal,
                subtitle = strings.sync.localBackupDesc,
                onClick = onNavigateToLocalBackup,
            )
        }
    }
}
