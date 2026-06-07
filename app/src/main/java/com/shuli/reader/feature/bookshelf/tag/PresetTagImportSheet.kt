package com.shuli.reader.feature.bookshelf.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings

data class PresetTagPack(
    val name: String,
    val tags: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetTagImportSheet(
    presetPacks: List<PresetTagPack>,
    onImport: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState()
    var selectedPackIndex by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                text = strings.bookshelf.presetTagPacks,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(16.dp))

            presetPacks.forEachIndexed { index, pack ->
                FilterChip(
                    selected = selectedPackIndex == index,
                    onClick = {
                        selectedPackIndex = if (selectedPackIndex == index) null else index
                    },
                    label = {
                        Column {
                            Text(
                                text = pack.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = pack.tags.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(16.dp))

            Row {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.common.cancel)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        selectedPackIndex?.let { idx ->
                            onImport(presetPacks[idx].tags)
                        }
                    },
                    enabled = selectedPackIndex != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.bookshelf.importPresetTags)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
