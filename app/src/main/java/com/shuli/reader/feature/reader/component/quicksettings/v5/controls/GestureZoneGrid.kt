package com.shuli.reader.feature.reader.component.quicksettings.v5.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.shuli.reader.feature.reader.settings.GestureAction
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.feature.reader.settings.TouchZone
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/** 翻页类触控动作。 */
private val PAGE_GESTURE_ACTIONS = listOf(
    GestureAction.PREV_PAGE,
    GestureAction.NEXT_PAGE,
    GestureAction.SCROLL_UP,
    GestureAction.SCROLL_DOWN,
)

/** 阅读工具类触控动作。 */
private val READING_GESTURE_ACTIONS = listOf(
    GestureAction.TOGGLE_TOOLBAR,
    GestureAction.TOGGLE_DIRECTORY,
    GestureAction.ADD_BOOKMARK,
    GestureAction.TOGGLE_THEME,
    GestureAction.TOGGLE_IMMERSIVE,
    GestureAction.NONE,
)

/** 动作中文短标签。 */
internal fun GestureAction.label(): String = when (this) {
    GestureAction.NONE -> "无"
    GestureAction.PREV_PAGE -> "上一页"
    GestureAction.NEXT_PAGE -> "下一页"
    GestureAction.TOGGLE_TOOLBAR -> "工具栏"
    GestureAction.TOGGLE_DIRECTORY -> "目录"
    GestureAction.ADD_BOOKMARK -> "书签"
    GestureAction.TOGGLE_THEME -> "切主题"
    GestureAction.TOGGLE_IMMERSIVE -> "沉浸"
    GestureAction.SCROLL_UP -> "上滚"
    GestureAction.SCROLL_DOWN -> "下滚"
}

/** 九宫格行序（与 [TouchZone] 对应）。 */
private val ZONE_GRID = listOf(
    listOf(TouchZone.TOP_LEFT, TouchZone.TOP_CENTER, TouchZone.TOP_RIGHT),
    listOf(TouchZone.MIDDLE_LEFT, TouchZone.MIDDLE_CENTER, TouchZone.MIDDLE_RIGHT),
    listOf(TouchZone.BOTTOM_LEFT, TouchZone.BOTTOM_CENTER, TouchZone.BOTTOM_RIGHT),
)

/**
 * 触控区域九宫格（对应原型 .gesture-grid）。
 *
 * 每格显示当前手势动作，点击后在触发区域附近弹出动作菜单并回调更新后的 [GestureConfig]。
 *
 * @param config 当前手势配置
 * @param onConfigChange 配置变化回调
 */
@Composable
fun GestureZoneGrid(
    config: GestureConfig,
    onConfigChange: (GestureConfig) -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    showHint: Boolean = true,
) {
    val colors = LocalReaderColorScheme.current
    Column(
        modifier = modifier
            .then(if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .padding(vertical = 8.dp)
            .testTag("GestureZoneGrid"),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ZONE_GRID.forEachIndexed { rowIndex, rowZones ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.weight(1f) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                rowZones.forEachIndexed { columnIndex, zone ->
                    val action = config.getAction(zone)
                    GestureZoneButton(
                        zone = zone,
                        action = action,
                        rowIndex = rowIndex,
                        columnIndex = columnIndex,
                        onActionSelect = { selected ->
                            onConfigChange(config.withZone(zone, selected))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                            .testTag("GestureZone_${zone.name}"),
                        fillHeight = fillHeight,
                    )
                }
            }
        }
        if (showHint) {
            Text(
                text = "点击区域选择动作",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun GestureZoneButton(
    zone: TouchZone,
    action: GestureAction,
    rowIndex: Int,
    columnIndex: Int,
    onActionSelect: (GestureAction) -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
) {
    val colors = LocalReaderColorScheme.current
    var expanded by remember(zone) { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val cellBackground = if (fillHeight) {
        Color.White.copy(alpha = if (expanded) 0.18f else 0.07f)
    } else if (expanded) {
        colors.accent.copy(alpha = 0.10f)
    } else {
        colors.background
    }
    val cellBorder = if (fillHeight) {
        Color.White.copy(alpha = if (expanded) 0.76f else 0.18f)
    } else if (expanded) {
        colors.accent
    } else {
        colors.divider
    }
    val cellText = if (fillHeight) {
        Color.White
    } else if (expanded) {
        colors.textPrimary
    } else {
        colors.textSecondary
    }
    val iconTint = if (fillHeight) Color.White.copy(alpha = 0.72f) else colors.textTertiary

    Box(
        modifier = modifier
            .then(if (fillHeight) Modifier.fillMaxSize() else Modifier.aspectRatio(1.6f))
            .clip(shape)
            .background(cellBackground)
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = cellBorder,
                ),
                shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { expanded = true },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = action.label(),
                style = MaterialTheme.typography.labelSmall,
                color = cellText,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = "展开",
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
        }

        if (expanded) {
            GestureActionMenu(
                current = action,
                rowIndex = rowIndex,
                columnIndex = columnIndex,
                onDismiss = { expanded = false },
                onSelect = { selected ->
                    onActionSelect(selected)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun GestureActionMenu(
    current: GestureAction,
    rowIndex: Int,
    columnIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (GestureAction) -> Unit,
) {
    val xShift = when (columnIndex) {
        0 -> 0.dp
        1 -> (-56).dp
        else -> (-112).dp
    }
    val yShift = if (rowIndex == ZONE_GRID.lastIndex) 0.dp else 2.dp

    InkDropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        selected = current,
        sections = listOf(
            InkDropdownSection(
                title = "翻页",
                columns = 3,
                options = PAGE_GESTURE_ACTIONS.map { action ->
                    InkDropdownOption(
                        value = action,
                        label = action.label(),
                        testTag = "GestureAction_${action.name}",
                    )
                },
            ),
            InkDropdownSection(
                title = "阅读",
                columns = 3,
                options = READING_GESTURE_ACTIONS.map { action ->
                    InkDropdownOption(
                        value = action,
                        label = action.label(),
                        testTag = "GestureAction_${action.name}",
                    )
                },
            ),
        ),
        onSelect = onSelect,
        offset = DpOffset(x = xShift, y = yShift),
        minWidth = 240.dp,
        testTag = "GestureActionPicker",
    )
}

/** 返回替换了指定 [zone] 动作后的新配置。 */
internal fun GestureConfig.withZone(zone: TouchZone, action: GestureAction): GestureConfig = when (zone) {
    TouchZone.TOP_LEFT -> copy(topLeft = action)
    TouchZone.TOP_CENTER -> copy(topCenter = action)
    TouchZone.TOP_RIGHT -> copy(topRight = action)
    TouchZone.MIDDLE_LEFT -> copy(middleLeft = action)
    TouchZone.MIDDLE_CENTER -> copy(middleCenter = action)
    TouchZone.MIDDLE_RIGHT -> copy(middleRight = action)
    TouchZone.BOTTOM_LEFT -> copy(bottomLeft = action)
    TouchZone.BOTTOM_CENTER -> copy(bottomCenter = action)
    TouchZone.BOTTOM_RIGHT -> copy(bottomRight = action)
}
