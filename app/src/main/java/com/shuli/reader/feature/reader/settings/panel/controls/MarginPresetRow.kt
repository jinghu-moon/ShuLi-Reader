package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.reader.model.BoxInsetsDp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 边距预设数据类
 */
data class MarginPreset(
    val label: String,
    val bodyBox: BoxInsetsDp,
    val headerBox: BoxInsetsDp,
    val footerBox: BoxInsetsDp,
    val titleBox: BoxInsetsDp,
)

/**
 * 预设选择行：横向排列的 FilterChip。
 * 无状态组件，点击回调由外层处理。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarginPresetRow(
    selected: MarginPreset?,
    onSelect: (MarginPreset) -> Unit,
    presets: List<MarginPreset>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            val isSelected = selected?.label == preset.label
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(preset) },
                label = { Text(preset.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.accent.copy(alpha = 0.15f),
                    selectedLabelColor = colors.accent,
                ),
            )
        }
    }
}
