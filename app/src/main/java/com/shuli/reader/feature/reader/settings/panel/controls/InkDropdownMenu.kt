package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 下拉菜单选项。
 */
data class InkDropdownOption<T>(
    val value: T,
    val label: String,
    val testTag: String? = null,
)

/**
 * 下拉菜单分组。
 */
data class InkDropdownSection<T>(
    val title: String? = null,
    val options: List<InkDropdownOption<T>>,
    val columns: Int = 1,
)

/**
 * 基于 Material3 官方 [DropdownMenu] 的项目通用下拉菜单。
 *
 * 只负责菜单本体，不负责触发器；调用方把它放在触发器所在的 Box 内，
 * 通过 [offset] 微调菜单相对触发区域的位置。
 */
@Composable
fun <T> InkDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    selected: T,
    sections: List<InkDropdownSection<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    minWidth: Dp = 180.dp,
    gridCellWidth: Dp = 72.dp,
    gridGap: Dp = 3.dp,
    testTag: String = "InkDropdownMenu",
) {
    val colors = LocalReaderColorScheme.current
    val maxColumns = sections.maxOfOrNull { it.columns.coerceAtLeast(1) } ?: 1
    val gridContentWidth = (
        gridCellWidth.value * maxColumns +
            gridGap.value * (maxColumns - 1) +
            16f
        ).dp
    val menuMinWidth = maxOf(minWidth, gridContentWidth)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .widthIn(min = menuMinWidth)
            .testTag(testTag),
        offset = offset,
        shape = RoundedCornerShape(10.dp),
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, colors.divider),
        properties = PopupProperties(focusable = true),
    ) {
        sections.forEach { section ->
            if (section.title != null) {
                Text(
                    text = section.title,
                    fontSize = 10.sp,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
                )
            }
            val columns = section.columns.coerceAtLeast(1)
            if (columns == 1) {
                section.options.forEach { option ->
                    InkDropdownListItem(
                        option = option,
                        selected = option.value == selected,
                        onClick = {
                            onSelect(option.value)
                            onDismissRequest()
                        },
                    )
                }
            } else {
                InkDropdownGrid(
                    options = section.options,
                    selected = selected,
                    columns = columns,
                    width = menuMinWidth,
                    gap = gridGap,
                    onClick = { value ->
                        onSelect(value)
                        onDismissRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun <T> InkDropdownListItem(
    option: InkDropdownOption<T>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalReaderColorScheme.current
    DropdownMenuItem(
        text = {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) colors.accent else colors.textPrimary,
            )
        },
        onClick = onClick,
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.accent,
                )
            }
        } else {
            null
        },
        modifier = option.testTag?.let {
            Modifier.testTag(it)
        } ?: Modifier,
    )
}

@Composable
private fun <T> InkDropdownGrid(
    options: List<InkDropdownOption<T>>,
    selected: T,
    columns: Int,
    width: Dp,
    gap: Dp,
    onClick: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        options.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                rowItems.forEach { option ->
                    InkDropdownGridItem(
                        option = option,
                        selected = option.value == selected,
                        onClick = { onClick(option.value) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun <T> InkDropdownGridItem(
    option: InkDropdownOption<T>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .then(option.testTag?.let { Modifier.testTag(it) } ?: Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.label,
            fontSize = 11.sp,
            color = if (selected) colors.surface else colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
