package com.shuli.reader.feature.reader.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import java.io.File

/**
 * 标签宽度（中文 4 字宽度，单行不换行）
 */
val SETTINGS_LABEL_WIDTH = 64.dp

/**
 * 分段选择行
 */
@Composable
fun <T> ReaderSegmentedRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readerColors = LocalReaderColorScheme.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(SETTINGS_LABEL_WIDTH),
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            options.forEachIndexed { index, (key, label) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    onClick = { onSelect(key) },
                    selected = selected == key,
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = readerColors.accent,
                        activeContentColor = readerColors.background,
                        inactiveContainerColor = readerColors.surface,
                        inactiveContentColor = readerColors.textPrimary,
                    ),
                )
            }
        }
    }
}

/**
 * Chip 行（字体选择、预设等）
 */
@Composable
fun <T> ReaderChipRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readerColors = LocalReaderColorScheme.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(SETTINGS_LABEL_WIDTH),
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
        options.forEach { (key, chipLabel) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(chipLabel) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = readerColors.surface,
                    labelColor = readerColors.textPrimary,
                    selectedContainerColor = readerColors.accent,
                    selectedLabelColor = readerColors.background,
                ),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * 开关行
 */
@Composable
fun ReaderSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    val readerColors = LocalReaderColorScheme.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * 选择器行：点击弹出下拉菜单
 *
 * 适用于选项 > 4 的场景（如页眉脚槽位 8 选项），替代拥挤的 SegmentedButton。
 * 选中项带背景色 + 勾选图标。
 */
@Composable
fun <T> ReaderPickerRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readerColors = LocalReaderColorScheme.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: ""

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { expanded = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(SETTINGS_LABEL_WIDTH),
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textSecondary,
        )
        Box {
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = readerColors.textSecondary,
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = readerColors.surface,
            ) {
                options.forEach { (key, optionLabel) ->
                    val isSelected = key == selected
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        null,
                                        tint = readerColors.accent,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                Text(
                                    text = optionLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) readerColors.accent else readerColors.textPrimary,
                                )
                            }
                        },
                        onClick = {
                            onSelect(key)
                            expanded = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = if (isSelected) readerColors.accent else readerColors.textPrimary,
                        ),
                        modifier = if (isSelected) {
                            Modifier.background(readerColors.accent.copy(alpha = 0.08f))
                        } else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * 表单式选择行：iOS Form 风格
 *
 * 行尾显示当前值 + ›，点击弹出半屏 BottomSheet 选择面板。
 * 适合所有 3+ 选项的单选场景，视觉极度统一，永不挤压。
 */
@Composable
fun <T> ReaderFormPickerRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    sheetTitle: String = label,
    fontFiles: Map<T, File>? = null,
) {
    val readerColors = LocalReaderColorScheme.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: ""

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(SETTINGS_LABEL_WIDTH),
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = readerColors.textSecondary,
            maxLines = 1,
            softWrap = false,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = readerColors.textSecondary,
            modifier = Modifier.padding(start = 4.dp).size(20.dp),
        )
    }

    if (expanded) {
        if (fontFiles != null) {
            FontPickerSheet(
                title = sheetTitle,
                options = options,
                fontFiles = fontFiles,
                selected = selected,
                onSelect = onSelect,
                onDismiss = { expanded = false },
            )
        } else {
            PickerSheet(
                title = sheetTitle,
                options = options,
                selected = selected,
                onSelect = onSelect,
                onDismiss = { expanded = false },
            )
        }
    }
}
