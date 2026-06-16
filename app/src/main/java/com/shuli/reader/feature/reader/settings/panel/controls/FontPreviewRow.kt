package com.shuli.reader.feature.reader.settings.panel.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
 * 字体选择列表：垂直行布局。
 *
 * 每行左侧为字体名称，右侧为使用对应字体渲染的示例文本。
 * 点击行选中字体；长按自定义字体可删除。
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

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val isSelected = item.key == selectedKey
            val rowBg = if (isSelected) colors.accent.copy(alpha = 0.10f)
                       else colors.surface
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("FontRow_${item.key}")
                    .combinedClickable(
                        onClick = { onSelect(item.key) },
                        onLongClick = {
                            if (item.entry != null) fontToDelete = item.entry
                        },
                    )
                    .background(rowBg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) colors.accent else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    text = strings.fontPreviewSample,
                    fontFamily = item.family,
                    fontSize = 15.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.divider),
                )
            }
        }
    }
}
