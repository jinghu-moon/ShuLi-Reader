package com.shuli.reader.feature.reader.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 模块 A: 悬浮工具栏
 *
 * 参考 edit-interface-demo.html 设计：
 * - 毛玻璃效果背景
 * - 圆角 16dp
 * - 输入框组 + 正则/大小写切换
 * - 进度胶囊 + 操作按钮组
 * - 替换栏展开/收起
 */
@Composable
fun EditorFloatingToolbar(
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
    onToggleSidebar: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalReaderColorScheme.current
    val tokens = EditorTokens

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = tokens.ToolbarHorizontalPadding,
                vertical = tokens.ToolbarTopPadding,
            ),
        shape = RoundedCornerShape(tokens.ToolbarCornerRadius),
        color = tokens.GlassBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.Outline),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 第一排：查找栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tokens.ToolbarHeight)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 输入框组
                InputGroup(
                    findText = uiState.findText,
                    onFindTextChange = onFindTextChange,
                    onFind = onFind,
                    isRegex = uiState.isRegex,
                    isCaseSensitive = uiState.isCaseSensitive,
                    onToggleRegex = onToggleRegex,
                    onToggleCaseSensitive = onToggleCaseSensitive,
                    modifier = Modifier.weight(1f),
                )

                // 进度胶囊
                ProgressCapsule(
                    text = if (uiState.isSearching) {
                        uiState.searchProgress
                    } else if (uiState.matches.isNotEmpty()) {
                        "${uiState.currentMatchIndex + 1}/${uiState.matches.size}"
                    } else {
                        "0/0"
                    },
                    onClick = onToggleSidebar,
                )

                // 操作按钮组
                ControlGroup(
                    onFindPrev = onFindPrev,
                    onFindNext = onFindNext,
                    onToggleHistory = onToggleHistory,
                    onToggleReplace = onToggleReplace,
                    onClose = onClose,
                    isReplaceExpanded = uiState.showReplace,
                )
            }

            // 第二排：替换栏（展开时显示）
            AnimatedVisibility(
                visible = uiState.showReplace,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)),
            ) {
                ReplaceRow(
                    replaceText = uiState.replaceText,
                    onReplaceTextChange = onReplaceTextChange,
                    onReplace = onReplace,
                    onReplaceAll = onReplaceAll,
                    hasMatches = uiState.matches.isNotEmpty(),
                )
            }
        }
    }
}

/**
 * 输入框组
 */
@Composable
private fun InputGroup(
    findText: String,
    onFindTextChange: (String) -> Unit,
    onFind: () -> Unit,
    isRegex: Boolean,
    isCaseSensitive: Boolean,
    onToggleRegex: () -> Unit,
    onToggleCaseSensitive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = EditorTokens

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(tokens.InputCornerRadius))
            .background(tokens.SurfaceVariant)
            .border(1.dp, tokens.Outline, RoundedCornerShape(tokens.InputCornerRadius))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tokens.TextSecondary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        BasicTextField(
            value = findText,
            onValueChange = onFindTextChange,
            textStyle = TextStyle(
                fontSize = tokens.SearchInputFontSize,
                color = tokens.TextPrimary,
            ),
            cursorBrush = SolidColor(tokens.Primary),
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onFind() }),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleButton(text = ".*", isActive = isRegex, onClick = onToggleRegex)
            ToggleButton(text = "Aa", isActive = isCaseSensitive, onClick = onToggleCaseSensitive)
        }
    }
}

/**
 * 进度胶囊
 */
@Composable
private fun ProgressCapsule(
    text: String,
    onClick: () -> Unit,
) {
    val tokens = EditorTokens

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = tokens.SurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.Outline),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = text,
                fontSize = tokens.MatchCountFontSize,
                fontWeight = FontWeight.SemiBold,
                color = tokens.TextPrimary,
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = tokens.TextSecondary,
            )
        }
    }
}

/**
 * 操作按钮组
 */
@Composable
private fun ControlGroup(
    onFindPrev: () -> Unit,
    onFindNext: () -> Unit,
    onToggleHistory: () -> Unit,
    onToggleReplace: () -> Unit,
    onClose: () -> Unit,
    isReplaceExpanded: Boolean,
) {
    val tokens = EditorTokens
    val rotation by animateFloatAsState(
        targetValue = if (isReplaceExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "expandRotation",
    )

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = onFindPrev, modifier = Modifier.size(tokens.IconSize)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一个", modifier = Modifier.size(tokens.IconInnerSize))
        }
        IconButton(onClick = onFindNext, modifier = Modifier.size(tokens.IconSize)) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一个", modifier = Modifier.size(tokens.IconInnerSize))
        }
        // 分隔线
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(tokens.DividerHeight)
                .background(tokens.Outline)
        )
        IconButton(onClick = onToggleHistory, modifier = Modifier.size(tokens.IconSize)) {
            Icon(Icons.Filled.History, contentDescription = "历史记录", modifier = Modifier.size(tokens.IconInnerSize))
        }
        IconButton(onClick = onToggleReplace, modifier = Modifier.size(tokens.IconSize)) {
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = "展开替换",
                modifier = Modifier
                    .size(tokens.IconInnerSize)
                    .rotate(rotation),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(tokens.IconSize)) {
            Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(tokens.IconInnerSize))
        }
    }
}

/**
 * 替换栏
 */
@Composable
private fun ReplaceRow(
    replaceText: String,
    onReplaceTextChange: (String) -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    hasMatches: Boolean,
) {
    val tokens = EditorTokens

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
                .clip(RoundedCornerShape(tokens.InputCornerRadius))
                .background(tokens.SurfaceVariant)
                .border(1.dp, tokens.Outline, RoundedCornerShape(tokens.InputCornerRadius))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tokens.TextSecondary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            BasicTextField(
                value = replaceText,
                onValueChange = onReplaceTextChange,
                textStyle = TextStyle(
                    fontSize = tokens.SearchInputFontSize,
                    color = tokens.TextPrimary,
                ),
                cursorBrush = SolidColor(tokens.Primary),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        // 替换按钮
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = tokens.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.Outline),
            modifier = Modifier.clickable(enabled = hasMatches, onClick = onReplace),
        ) {
            Text(
                text = "替换",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = tokens.ButtonFontSize,
                fontWeight = FontWeight.Medium,
                color = tokens.TextPrimary,
            )
        }

        // 全部替换按钮
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = tokens.Primary,
            modifier = Modifier.clickable(enabled = hasMatches, onClick = onReplaceAll),
        ) {
            Text(
                text = "全部替换",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = tokens.ButtonFontSize,
                fontWeight = FontWeight.Medium,
                color = tokens.OnPrimary,
            )
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
) {
    val tokens = EditorTokens

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) tokens.Outline else tokens.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = tokens.ToggleFontSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (isActive) tokens.Primary else tokens.TextSecondary,
        )
    }
}
