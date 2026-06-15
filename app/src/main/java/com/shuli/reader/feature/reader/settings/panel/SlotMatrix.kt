package com.shuli.reader.feature.reader.settings.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.shuli.reader.core.reader.model.SlotContent
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 槽位显示元数据。
 *
 * [label] 短文本，显示在槽位按钮内；
 * [preview] 在真实阅读区渲染的示例文本（可为动态值，如 "9:41" / "68%"）。
 */
data class SlotDisplay(
    val label: String,
    val preview: String = label,
)

/**
 * 默认 [SlotContent] → [SlotDisplay] 映射。
 */
fun defaultSlotDisplays(): Map<SlotContent, SlotDisplay> = mapOf(
    SlotContent.NONE to SlotDisplay("留白", " "),
    SlotContent.CHAPTER_TITLE to SlotDisplay("章节"),
    SlotContent.BOOK_TITLE to SlotDisplay("书名"),
    SlotContent.CHAPTER_PROGRESS_FRACTION to SlotDisplay("章节", "12/18"),
    SlotContent.CHAPTER_PROGRESS_PERCENT to SlotDisplay("章节%", "67%"),
    SlotContent.BOOK_PROGRESS_FRACTION to SlotDisplay("全书", "3/5"),
    SlotContent.BOOK_PROGRESS_PERCENT to SlotDisplay("全书%", "68%"),
    SlotContent.TIME to SlotDisplay("时间", "9:41"),
    SlotContent.BATTERY to SlotDisplay("电量", "86%"),
    SlotContent.DATE to SlotDisplay("日期", "6/14"),
)

/**
 * 2×3 页眉/页脚槽位矩阵（对应原型 .matrix）。
 *
 * 第一行 = 页眉（左/中/右），第二行 = 页脚（左/中/右）。点击任一槽位
 * 弹出原位 DropdownMenu，以分组网格展示所有可选内容。
 */
@Composable
fun SlotMatrix(
    headerSlots: Triple<SlotContent, SlotContent, SlotContent>,
    footerSlots: Triple<SlotContent, SlotContent, SlotContent>,
    onHeaderSlotChange: (Int, SlotContent) -> Unit,
    onFooterSlotChange: (Int, SlotContent) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allOptions: List<Pair<SlotContent, SlotDisplay>> = defaultSlotDisplays().entries
        .map { it.key to it.value },
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("SlotMatrix")
            .graphicsLayer { this.alpha = alpha },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MatrixRow(
            label = "页眉",
            slots = headerSlots,
            rowTag = "SlotMatrix_Header",
            onSlotChange = onHeaderSlotChange,
            allOptions = allOptions,
            enabled = enabled,
        )
        MatrixRow(
            label = "页脚",
            slots = footerSlots,
            rowTag = "SlotMatrix_Footer",
            onSlotChange = onFooterSlotChange,
            allOptions = allOptions,
            enabled = enabled,
        )
    }
}

@Composable
private fun MatrixRow(
    label: String,
    slots: Triple<SlotContent, SlotContent, SlotContent>,
    rowTag: String,
    onSlotChange: (Int, SlotContent) -> Unit,
    allOptions: List<Pair<SlotContent, SlotDisplay>>,
    enabled: Boolean = true,
) {
    val colors = LocalReaderColorScheme.current
    Row(
        modifier = Modifier.fillMaxWidth().testTag(rowTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textTertiary,
            modifier = Modifier.widthIn(min = 32.dp),
        )
        val slotList = listOf(slots.first, slots.second, slots.third)
        slotList.forEachIndexed { index, content ->
            SlotButton(
                content = content,
                onSelect = { if (enabled) onSlotChange(index, it) },
                allOptions = allOptions,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${rowTag}_Slot$index"),
            )
        }
    }
}

@Composable
private fun SlotButton(
    content: SlotContent,
    onSelect: (SlotContent) -> Unit,
    allOptions: List<Pair<SlotContent, SlotDisplay>>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalReaderColorScheme.current
    var expanded by remember { mutableStateOf(false) }
    val isEmpty = content == SlotContent.NONE
    val display = allOptions.firstOrNull { it.first == content }?.second
    val labelText = display?.label ?: "留白"

    val shape = RoundedCornerShape(8.dp)

    Box(modifier = modifier.height(36.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .then(
                    if (isEmpty) {
                        Modifier.drawBehind {
                            val stroke = 1.dp.toPx()
                            val pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 3.dp.toPx()),
                                0f,
                            )
                            drawRect(
                                color = colors.divider,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke, pathEffect = pathEffect),
                            )
                        }
                    } else {
                        Modifier.border(BorderStroke(1.dp, colors.divider), shape)
                    },
                )
                .background(
                    color = if (isEmpty) Color.Transparent else colors.divider.copy(alpha = 0.18f),
                    shape = shape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                ) { expanded = !expanded },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                Text(
                    text = labelText,
                    fontSize = 11.sp,
                    color = if (isEmpty) colors.textTertiary else colors.textPrimary,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = "展开",
                    tint = if (isEmpty) colors.textTertiary else colors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (expanded) {
            val density = LocalDensity.current
            val yShiftPx = with(density) { 40.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, yShiftPx),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                val pickerShape = RoundedCornerShape(8.dp)
                Box(
                    modifier = Modifier
                        .widthIn(min = 240.dp)
                        .shadow(12.dp, pickerShape, clip = false)
                        .clip(pickerShape)
                        .drawBehind {
                            drawRect(color = colors.surface)
                            drawRect(
                                color = colors.divider,
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }
                        .padding(8.dp)
                        .testTag("SlotPicker"),
                ) {
                    SlotPickerContent(
                        current = content,
                        allOptions = allOptions,
                        onSelect = { selected ->
                            onSelect(selected)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotPickerContent(
    current: SlotContent,
    allOptions: List<Pair<SlotContent, SlotDisplay>>,
    onSelect: (SlotContent) -> Unit,
) {
    val infoOptions = allOptions.filter {
        it.first == SlotContent.NONE ||
            it.first == SlotContent.CHAPTER_TITLE ||
            it.first == SlotContent.BOOK_TITLE ||
            it.first == SlotContent.TIME ||
            it.first == SlotContent.DATE ||
            it.first == SlotContent.BATTERY
    }
    val progressOptions = allOptions.filter {
        it.first == SlotContent.CHAPTER_PROGRESS_FRACTION ||
            it.first == SlotContent.CHAPTER_PROGRESS_PERCENT ||
            it.first == SlotContent.BOOK_PROGRESS_FRACTION ||
            it.first == SlotContent.BOOK_PROGRESS_PERCENT
    }

    Column(modifier = Modifier.width(240.dp)) {
        PickerGroupLabel("信息")
        PickerGrid(options = infoOptions, current = current, onSelect = onSelect)
        PickerGroupLabel("进度")
        PickerGrid(options = progressOptions, current = current, onSelect = onSelect)
    }
}

@Composable
private fun PickerGroupLabel(text: String) {
    val colors = LocalReaderColorScheme.current
    Text(
        text = text,
        fontSize = 9.sp,
        letterSpacing = 1.sp,
        color = colors.textTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun PickerGrid(
    options: List<Pair<SlotContent, SlotDisplay>>,
    current: SlotContent,
    onSelect: (SlotContent) -> Unit,
) {
    val colors = LocalReaderColorScheme.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        options.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                rowItems.forEach { (slot, display) ->
                    val selected = slot == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                color = if (selected) colors.accent else Color.Transparent,
                            )
                            .clickable { onSelect(slot) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = display.label,
                            fontSize = 11.sp,
                            color = if (selected) colors.surface else colors.textPrimary,
                        )
                    }
                }
                repeat(3 - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
