package com.shuli.reader.feature.reader.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * 编辑记录面板
 *
 * 显示 diff 视图 + 逐条撤销
 */
@Composable
fun EditHistoryPanel(
    patches: List<EditStore.Patch>,
    onUndo: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (patches.isEmpty()) {
        // 空状态
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "暂无编辑记录",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "编辑记录 (${patches.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onClearAll) {
                Text("全部撤销")
            }
        }

        HorizontalDivider()

        // 编辑记录列表
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        ) {
            items(patches.reversed()) { patch ->
                EditHistoryItem(patch = patch, onUndo = onUndo)
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 章节信息
                Text(
                    text = "第${patch.chapterIndex + 1}章",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Diff 显示
                when (patch) {
                    is EditStore.SinglePatch -> {
                        DiffText(
                            original = patch.delta.originalText,
                            replacement = patch.delta.newText,
                        )
                    }
                    is EditStore.BatchPatch -> {
                        DiffText(
                            original = patch.batch.findText,
                            replacement = "${patch.batch.replaceText} (${patch.batch.ranges.size}处)",
                        )
                    }
                }
            }

            // 撤销按钮
            IconButton(onClick = onUndo) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "撤销",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Diff 文本显示
 */
@Composable
private fun DiffText(
    original: String,
    replacement: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 原文（删除线 + 红色）
        Text(
            text = original.take(20),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textDecoration = TextDecoration.LineThrough,
            maxLines = 1,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "→",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 替换后（粗体 + 主题色）
        Text(
            text = replacement.take(20),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}
