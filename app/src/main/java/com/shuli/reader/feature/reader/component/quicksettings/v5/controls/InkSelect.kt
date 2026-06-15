package com.shuli.reader.feature.reader.component.quicksettings.v5.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 内联下拉选择（对应原型 .inline-select）。
 *
 * 灰底描边胶囊触发器 + 下拉箭头，点击展开 [DropdownMenu]。泛型支持任意枚举 / 字符串选项。
 *
 * @param options 选项列表（值 → 显示文案）
 * @param selected 当前选中值
 * @param onSelect 选中回调
 * @param testTag 测试 tag
 */
@Composable
fun <T> InkSelect(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "InkSelect",
) {
    val colors = LocalReaderColorScheme.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: ""
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colors.background)
                .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = colors.textTertiary,
            )
        }
        InkDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            selected = selected,
            sections = listOf(
                InkDropdownSection(
                    options = options.map { (value, label) ->
                        InkDropdownOption(value = value, label = label)
                    },
                ),
            ),
            onSelect = onSelect,
            testTag = "${testTag}_Menu",
        )
    }
}
