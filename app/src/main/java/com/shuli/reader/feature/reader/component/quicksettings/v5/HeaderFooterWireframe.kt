package com.shuli.reader.feature.reader.component.quicksettings.v5

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import androidx.compose.foundation.Canvas

/**
 * 页眉/页脚槽位选择 wireframe。
 *
 * 三段式结构：页眉 | 页面主体 | 页脚。
 * 点击页眉/页脚的某个 slot 弹出气泡选择器。
 *
 * @param headerSlots 页眉 3 个 slot 内容（左/中/右）
 * @param footerSlots 页脚 3 个 slot 内容
 * @param onHeaderSlotChange 页眉 slot 变化回调 (position, newValue)
 * @param onFooterSlotChange 页脚 slot 变化回调
 */
@Composable
fun HeaderFooterWireframe(
    headerSlots: Triple<SlotContent, SlotContent, SlotContent>,
    footerSlots: Triple<SlotContent, SlotContent, SlotContent>,
    onHeaderSlotChange: (Int, SlotContent) -> Unit,
    onFooterSlotChange: (Int, SlotContent) -> Unit,
    modifier: Modifier = Modifier,
    allOptions: List<Pair<SlotContent, String>> = defaultSlotOptions(),
) {
    val readerColors = LocalReaderColorScheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("HeaderFooterWireframe"),
    ) {
        // 页眉
        WireframeRow(
            label = "页眉",
            slots = headerSlots,
            isHeader = true,
            onSlotChange = onHeaderSlotChange,
            allOptions = allOptions,
        )
        // 页面主体
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(vertical = 4.dp)
                .testTag("HeaderFooterWireframe_PageBody"),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = readerColors.textSecondary.copy(alpha = 0.3f),
                    size = size,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            Text(
                text = "页面主体",
                style = MaterialTheme.typography.bodySmall,
                color = readerColors.textSecondary,
            )
        }
        // 页脚
        WireframeRow(
            label = "页脚",
            slots = footerSlots,
            isHeader = false,
            onSlotChange = onFooterSlotChange,
            allOptions = allOptions,
        )
    }
}

@Composable
private fun WireframeRow(
    label: String,
    slots: Triple<SlotContent, SlotContent, SlotContent>,
    isHeader: Boolean,
    onSlotChange: (Int, SlotContent) -> Unit,
    allOptions: List<Pair<SlotContent, String>>,
) {
    val readerColors = LocalReaderColorScheme.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = readerColors.textSecondary,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val slotList = listOf(slots.first, slots.second, slots.third)
            slotList.forEachIndexed { index, content ->
                SlotChip(
                    content = content,
                    onSelect = { newContent -> onSlotChange(index, newContent) },
                    allOptions = allOptions,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("${if (isHeader) "Header" else "Footer"}Slot_$index"),
                )
            }
        }
    }
}

@Composable
private fun SlotChip(
    content: SlotContent,
    onSelect: (SlotContent) -> Unit,
    allOptions: List<Pair<SlotContent, String>>,
    modifier: Modifier = Modifier,
) {
    val readerColors = LocalReaderColorScheme.current
    var expanded by remember { mutableStateOf(false) }
    val isEmpty = content == SlotContent.NONE
    val labelText = allOptions.firstOrNull { it.first == content }?.second ?: "空"
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isEmpty) 1.dp else 0.dp,
                    color = readerColors.textSecondary.copy(alpha = if (isEmpty) 0.4f else 0f),
                    shape = RoundedCornerShape(4.dp),
                )
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isEmpty) readerColors.textSecondary else readerColors.textPrimary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            allOptions.forEach { (option, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun defaultSlotOptions(): List<Pair<SlotContent, String>> = listOf(
    SlotContent.NONE to "空",
    SlotContent.CHAPTER_TITLE to "章节",
    SlotContent.BOOK_TITLE to "书名",
    SlotContent.CHAPTER_PROGRESS_FRACTION to "章节进度",
    SlotContent.CHAPTER_PROGRESS_PERCENT to "章节%",
    SlotContent.BOOK_PROGRESS_FRACTION to "全书进度",
    SlotContent.BOOK_PROGRESS_PERCENT to "全书%",
    SlotContent.TIME to "时间",
    SlotContent.BATTERY to "电量",
    SlotContent.DATE to "日期",
)
