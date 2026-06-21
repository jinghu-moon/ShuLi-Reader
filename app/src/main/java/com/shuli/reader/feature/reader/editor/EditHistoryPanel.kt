package com.shuli.reader.feature.reader.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 编辑记录面板
 *
 * 参考 edit-interface-demo.html 设计：
 * - 底部弹出抽屉
 * - 按章节分组显示
 * - Diff 视图（删除线 + 新增）
 * - 逐条撤销 + 全部撤销 + 保存
 */
@Composable
fun EditHistoryPanel(
    patches: List<EditStore.Patch>,
    onUndo: () -> Unit,
    onClearAll: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    val materialColors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = colors.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.textPrimary,
                    )
                    Text(
                        text = "编辑记录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    // 徽章
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = materialColors.surfaceVariant,
                    ) {
                        Text(
                            text = "${patches.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = colors.divider)

            // 内容区
            if (patches.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无编辑记录",
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 按章节分组
                    val grouped = patches.groupBy { it.chapterIndex }
                    grouped.forEach { (chapterIndex, chapterPatches) ->
                        // 章节标题
                        item {
                            Text(
                                text = "第 ${chapterIndex + 1} 章",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }

                        // 编辑记录
                        items(chapterPatches.reversed()) { patch ->
                            EditHistoryItem(
                                patch = patch,
                                onUndo = onUndo,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.divider)

            // 底部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 全部撤销按钮
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
                    onClick = onClearAll,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "全部撤销",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                        )
                    }
                }

                // 保存修改按钮
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = materialColors.primary,
                    onClick = onSave,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "保存修改",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = materialColors.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单条编辑记录
 */
@Composable
private fun EditHistoryItem(
    patch: EditStore.Patch,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Diff 详情
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (patch) {
                    is EditStore.SinglePatch -> {
                        DiffLine(
                            prefix = "-",
                            text = patch.delta.originalText,
                            isOriginal = true,
                        )
                        DiffLine(
                            prefix = "+",
                            text = patch.delta.newText,
                            isOriginal = false,
                        )
                    }
                    is EditStore.BatchPatch -> {
                        DiffLine(
                            prefix = "-",
                            text = "${patch.batch.findText} (${patch.batch.ranges.size}处)",
                            isOriginal = true,
                        )
                        DiffLine(
                            prefix = "+",
                            text = "${patch.batch.replaceText} (${patch.batch.ranges.size}处)",
                            isOriginal = false,
                        )
                    }
                }
            }

            // 撤销按钮
            IconButton(
                onClick = onUndo,
                modifier = Modifier
                    .size(28.dp)
                    .border(1.dp, colors.divider, RoundedCornerShape(6.dp)),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "撤销",
                    modifier = Modifier.size(16.dp),
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * Diff 行
 */
@Composable
private fun DiffLine(
    prefix: String,
    text: String,
    isOriginal: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 前缀
        Text(
            text = prefix,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (isOriginal) Color(0xFF9B3525) else Color(0xFF2D7A52),
        )

        // 内容
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (isOriginal) colors.textSecondary else colors.textPrimary,
            maxLines = 2,
        )
    }
}
