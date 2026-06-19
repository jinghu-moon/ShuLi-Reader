package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.ReadingFont

private data class FontRowItem(
    val key: String,
    val label: String,
    val family: FontFamily,
    val entry: FontManager.FontEntry?,
)

/**
 * 字体选择横向瓦片列表。
 *
 * 每个瓦片上方为用对应字体渲染的「永」字，下方为字体名称。
 * 点击瓦片选中字体；长按自定义字体可删除。
 *
 * 导入按钮已迁移至字体卡片标题右侧，不再由本组件承载。
 */
@OptIn(ExperimentalFoundationApi::class)
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
            add(FontRowItem("harmony", strings.fontHarmonyShort, ReadingFont, null))
            add(FontRowItem("system", strings.fontSystemShort, FontFamily.Default, null))
            customFonts.forEach { entry ->
                add(
                    FontRowItem(
                        key = entry.key,
                        label = entry.name,
                        family = FontFamily(Font(entry.file)),
                        entry = entry,
                    )
                )
            }
        }
    }

    var fontToDelete by remember { mutableStateOf<FontManager.FontEntry?>(null) }

    if (fontToDelete != null) {
        AlertDialog(
            onDismissRequest = { fontToDelete = null },
            title = { Text(strings.deleteFontTitle) },
            text = { Text(strings.deleteFontConfirm(fontToDelete?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    fontToDelete?.let { entry -> onDelete(entry) }
                    fontToDelete = null
                }) {
                    Text(strings.deleteLabel, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fontToDelete = null }) {
                    Text(strings.cancelLabel)
                }
            }
        )
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        items(items, key = { it.key }) { item ->
            FontTile(
                item = item,
                isSelected = item.key == selectedKey,
                onClick = { onSelect(item.key) },
                onLongClick = {
                    if (item.entry != null) fontToDelete = item.entry
                },
            )
        }
    }
}

/**
 * 字体瓦片组件
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FontTile(
    item: FontRowItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current

    val borderColor = if (isSelected) colors.accent else colors.divider
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val bgColor = if (isSelected) colors.accent.copy(alpha = 0.06f) else colors.surface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(72.dp)
            .testTag("FontTile_${item.key}")
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor, RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        // 上部：大号「永」字
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                text = "永",
                fontFamily = item.family,
                fontSize = 24.sp,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            // 选中态：右上角 ✓ 图标
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .padding(2.dp),
                )
            }
        }

        // 下部：字体名称
        Text(
            text = item.label,
            fontSize = 10.sp,
            color = if (isSelected) colors.accent else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}
