package com.shuli.reader.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
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
internal fun TtsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val strings = LocalAppStrings.current

    SettingsSectionHeader(title = strings.tts.ttsSettings, icon = Icons.AutoMirrored.Outlined.VolumeUp)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.tts.ttsSpeed, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                    Text(strings.tts.ttsPitch, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                title = strings.tts.ttsAutoPage,
                checked = uiState.ttsAutoPage,
                onCheckedChange = { viewModel.updateTtsAutoPage(it) }
            )

            SettingsSwitchItem(
                title = strings.tts.ttsHighlightSentence,
                checked = uiState.ttsHighlightSentence,
                onCheckedChange = { viewModel.updateTtsHighlightSentence(it) }
            )
        }
    }
}
