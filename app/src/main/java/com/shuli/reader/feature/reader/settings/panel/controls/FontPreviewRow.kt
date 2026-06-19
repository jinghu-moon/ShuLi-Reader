package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.ReadingFont

private data class FontItem(
    val key: String,
    val label: String,
    val family: FontFamily,
    val entry: FontManager.FontEntry?,
)

/**
 * 字体选择下拉菜单。
 *
 * 左侧标签"阅读字体"，右侧下拉菜单选择字体，菜单项用对应字体渲染。
 * 使用项目通用的 InkDropdownMenu 组件。
 */
@Composable
fun FontPreviewRow(
    selectedKey: String,
    customFonts: List<FontManager.FontEntry>,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: (FontManager.FontEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    val strings = LocalAppStrings.current.reader

    val items = remember(customFonts) {
        buildList {
            add(FontItem("harmony", strings.fontHarmonyShort, ReadingFont, null))
            add(FontItem("system", strings.fontSystemShort, FontFamily.Default, null))
            customFonts.forEach { entry ->
                add(
                    FontItem(
                        key = entry.key,
                        label = entry.name,
                        family = FontFamily(Font(entry.file)),
                        entry = entry,
                    )
                )
            }
        }
    }

    val selectedItem = items.find { it.key == selectedKey } ?: items.firstOrNull()
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧标签
        Text(
            text = strings.readingFont,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )

        // 右侧下拉菜单触发器
        Box {
            Row(
                modifier = Modifier
                    .widthIn(max = 160.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.background)
                    .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedItem?.label ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = selectedItem?.family,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.textTertiary,
                )
            }

            // 下拉菜单
            InkDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                selected = selectedKey,
                sections = listOf(
                    InkDropdownSection(
                        options = items.map { item ->
                            InkDropdownOption(
                                value = item.key,
                                label = item.label,
                            )
                        },
                    ),
                ),
                onSelect = { key ->
                    onSelect(key)
                    expanded = false
                },
            )
        }
    }
}
