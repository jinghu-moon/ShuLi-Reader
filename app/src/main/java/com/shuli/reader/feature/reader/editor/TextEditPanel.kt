package com.shuli.reader.feature.reader.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * Chrome 式查找悬浮条
 *
 * 参考 edit-interface-demo.html 设计：
 * - 毛玻璃效果背景
 * - 圆角 16dp
 * - 输入框组 + 正则/大小写切换
 * - 进度胶囊 + 操作按钮组
 * - 替换栏展开/收起
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
    val colors = LocalReaderColorScheme.current
    // 使用 MaterialTheme 作为后备颜色
    val materialColors = MaterialTheme.colorScheme
    var isReplaceExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // 自动获取焦点
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 第一排：查找栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 输入框组
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(materialColors.surfaceVariant)
                        .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicTextField(
                        value = uiState.findText,
                        onValueChange = onFindTextChange,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = colors.textPrimary,
                        ),
                        cursorBrush = SolidColor(materialColors.primary),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onFind() }),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // 正则/大小写切换按钮
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ToggleButton(
                            text = ".*",
                            isActive = uiState.isRegex,
                            onClick = onToggleRegex,
                        )
                        ToggleButton(
                            text = "Aa",
                            isActive = uiState.isCaseSensitive,
                            onClick = onToggleCaseSensitive,
                        )
                    }
                }

                // 进度胶囊
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = materialColors.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.clickable { onToggleFindScope() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = if (uiState.isSearching) {
                                uiState.searchProgress
                            } else if (uiState.matches.isNotEmpty()) {
                                "${uiState.currentMatchIndex + 1}/${uiState.matches.size}"
                            } else {
                                "0/0"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = colors.textSecondary,
                        )
                    }
                }

                // 操作按钮组
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onFindPrev, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一个", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onFindNext, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一个", modifier = Modifier.size(18.dp))
                    }
                    // 分隔线
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(colors.divider)
                    )
                    IconButton(onClick = onToggleHistory, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.History, contentDescription = "历史记录", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { isReplaceExpanded = !isReplaceExpanded },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = "展开替换",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 第二排：替换栏（展开时显示）
            AnimatedVisibility(
                visible = isReplaceExpanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 替换输入框
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(materialColors.surfaceVariant)
                            .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicTextField(
                            value = uiState.replaceText,
                            onValueChange = onReplaceTextChange,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = colors.textPrimary,
                            ),
                            cursorBrush = SolidColor(materialColors.primary),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 替换按钮
                    TextButton(
                        onClick = onReplace,
                        enabled = uiState.matches.isNotEmpty(),
                    ) {
                        Text("替换")
                    }

                    // 全部替换按钮
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = materialColors.primary,
                        modifier = Modifier.clickable(enabled = uiState.matches.isNotEmpty()) { onReplaceAll() },
                    ) {
                        Text(
                            text = "全部替换",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = materialColors.onPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 切换按钮（正则/大小写）
 */
@Composable
private fun ToggleButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    val materialColors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) colors.divider else materialColors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) materialColors.primary else colors.textSecondary,
        )
    }
}
