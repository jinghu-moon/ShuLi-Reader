package com.shuli.reader.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.settings.SettingsUiState
import com.shuli.reader.feature.settings.SettingsViewModel
import com.shuli.reader.feature.settings.components.SettingsSectionHeader
import com.shuli.reader.feature.settings.components.SettingsSwitchItem

@Composable
internal fun StatsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val strings = LocalAppStrings.current

    SettingsSectionHeader(title = strings.settings.readingStats, icon = Icons.AutoMirrored.Outlined.ShowChart)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            SettingsSwitchItem(
                title = strings.settings.statsEnable,
                subtitle = strings.settings.statsEnableDesc,
                checked = uiState.readingTimeEnabled,
                onCheckedChange = { viewModel.updateReadingTimeEnabled(it) }
            )

            if (uiState.readingTimeEnabled) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(strings.settings.statsDailyTarget, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(strings.settings.readingTargetMinutes(uiState.readingDailyTarget), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
