package com.shuli.reader.feature.reader.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Chrome 式查找悬浮条
 *
 * 极简设计，不遮挡阅读内容
 */
@Composable
fun TextEditPanel(
    uiState: TextEditViewModel.EditUiState,
    onFindTextChange: (String) -> Unit,
    onReplaceTextChange: (String) -> Unit,
    onFind: () -> Unit,
    onFindNext: () -> Unit,
    onFindPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onToggleRegex: () -> Unit,
    onToggleCaseSensitive: () -> Unit,
    onToggleReplace: () -> Unit,
    onToggleHistory: () -> Unit,
    onToggleFindScope: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            // ── Row 1: 查找核心 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 查找输入框
                BasicTextField(
                    value = uiState.findText,
                    onValueChange = onFindTextChange,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onFind() }),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.shapes.small,
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            innerTextField()
                        }
                    },
                )

                // 匹配计数或搜索进度
                if (uiState.isSearching) {
                    Text(
                        text = uiState.searchProgress,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (uiState.matches.isNotEmpty()) {
                    Text(
                        text = "${uiState.currentMatchIndex + 1}/${uiState.matches.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (uiState.searchProgress.isNotEmpty()) {
                    Text(
                        text = uiState.searchProgress,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 导航按钮
                IconButton(onClick = onFindPrev, enabled = uiState.matches.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一个")
                }
                IconButton(onClick = onFindNext, enabled = uiState.matches.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一个")
                }

                // 关闭按钮
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            // ── Row 2: 工具栏 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // 查找范围切换
                TextButton(onClick = onToggleFindScope) {
                    Text(
                        text = if (uiState.findScope == TextEditViewModel.FindScope.CHAPTER) "本章" else "全书",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                // 正则开关
                IconButton(onClick = onToggleRegex) {
                    Text(
                        text = ".*",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.isRegex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 大小写敏感开关
                IconButton(onClick = onToggleCaseSensitive) {
                    Text(
                        text = "Aa",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.isCaseSensitive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 替换展开按钮
                IconButton(onClick = onToggleReplace) {
                    Icon(
                        Icons.Filled.FindReplace,
                        contentDescription = "替换",
                        tint = if (uiState.showReplace) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 撤销/重做
                IconButton(onClick = onUndo, enabled = uiState.editState.canUndo) {
                    Icon(Icons.Filled.Undo, contentDescription = "撤销")
                }
                IconButton(onClick = onRedo, enabled = uiState.editState.canRedo) {
                    Icon(Icons.Filled.Redo, contentDescription = "重做")
                }

                // 编辑历史
                IconButton(onClick = onToggleHistory) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = "编辑历史",
                        tint = if (uiState.showHistory) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 替换栏（展开时显示）
            AnimatedVisibility(
                visible = uiState.showReplace,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 替换输入框
                    BasicTextField(
                        value = uiState.replaceText,
                        onValueChange = onReplaceTextChange,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.shapes.small,
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                innerTextField()
                            }
                        },
                    )

                    // 替换按钮
                    TextButton(
                        onClick = onReplace,
                        enabled = uiState.matches.isNotEmpty(),
                    ) {
                        Text("替换")
                    }

                    // 全部替换按钮
                    TextButton(
                        onClick = onReplaceAll,
                        enabled = uiState.matches.isNotEmpty(),
                    ) {
                        Text("全部替换")
                    }
                }
            }
        }
    }
}
