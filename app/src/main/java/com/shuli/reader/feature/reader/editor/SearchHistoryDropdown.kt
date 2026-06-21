package com.shuli.reader.feature.reader.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 模块 B: 查找历史下拉菜单
 *
 * 参考 edit-interface-demo.html 设计：
 * - 定位在工具栏下方
 * - 搜索输入框获焦时显示
 * - 列表项：时钟图标 + 文字 + 删除按钮
 * - 底部：清空搜索历史
 */
@Composable
fun SearchHistoryDropdown(
    visible: Boolean,
    history: List<TextEditViewModel.FindHistory>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = EditorTokens

    AnimatedVisibility(
        visible = visible && history.isNotEmpty(),
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -10 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -10 },
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    start = tokens.ToolbarHorizontalPadding,
                    end = tokens.ToolbarHorizontalPadding,
                    top = tokens.HistoryDropdownTopOffset,
                ),
            shape = RoundedCornerShape(tokens.HistoryDropdownCornerRadius),
            color = tokens.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.Outline),
            shadowElevation = 12.dp,
        ) {
            LazyColumn(
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                items(history) { item ->
                    HistoryItem(
                        text = item.text,
                        onClick = { onSelect(item.text) },
                        onRemove = { onRemove(item.text) },
                    )
                }

                // 底部：清空历史
                item {
                    HorizontalDivider(color = tokens.Outline)
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "清空搜索历史",
                            fontSize = 12.sp,
                            color = tokens.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 历史记录项
 */
@Composable
private fun HistoryItem(
    text: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = EditorTokens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.AccessTime,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tokens.TextSecondary,
        )
        Text(
            text = text,
            fontSize = tokens.HistoryTextFontSize,
            color = tokens.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(20.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "删除",
                modifier = Modifier.size(14.dp),
                tint = tokens.TextSecondary,
            )
        }
    }
}
