package com.shuli.reader.feature.reader.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 模块 F: 内联编辑气泡（精确定位）
 *
 * 参考 edit-interface-demo.html 设计：
 * - 绝对定位在高亮文字上方
 * - 深色反转主题（T800 背景）
 * - 底部带小三角指向原文
 * - 无边框输入框 + 撤销按钮
 *
 * @param initialText 初始文本
 * @param anchorX 选区中心 X 坐标（屏幕像素）
 * @param anchorY 选区底部 Y 坐标（屏幕像素）
 * @param onConfirm 确认编辑回调
 * @param onDismiss 取消编辑回调
 */
@Composable
fun InlineEditPopover(
    initialText: String,
    anchorX: Float,
    anchorY: Float,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = EditorTokens
    val density = LocalDensity.current
    var editText by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    // 自动获取焦点
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 计算弹窗偏移（在选区上方，水平居中）
    val popupOffsetX = with(density) { (anchorX - 60.dp.toPx()).toInt() }  // 假设弹窗宽度约120dp
    val popupOffsetY = with(density) { (anchorY - 60.dp.toPx()).toInt() }  // 在选区上方50dp

    Popup(
        offset = IntOffset(popupOffsetX, popupOffsetY),
    ) {
        Box(
            modifier = modifier
                .drawBehind {
                    // 绘制底部小三角
                    val triangleSize = 8.dp.toPx()
                    val centerX = size.width / 2

                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(centerX - triangleSize, size.height)
                        lineTo(centerX, size.height + triangleSize)
                        lineTo(centerX + triangleSize, size.height)
                        close()
                    }

                    drawPath(path, tokens.SurfaceInverse)
                }
                .background(
                    tokens.SurfaceInverse,
                    RoundedCornerShape(8.dp),
                )
                .padding(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 输入框
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = tokens.OnSurfaceInverse,
                    ),
                    cursorBrush = SolidColor(tokens.OnSurfaceInverse),
                    singleLine = true,
                    modifier = Modifier
                        .width(120.dp)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onConfirm(editText) },
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .background(
                                    tokens.OnSurfaceInverse.copy(alpha = 0.1f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            innerTextField()
                        }
                    },
                )

                // 撤销按钮
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "撤销",
                        modifier = Modifier.size(16.dp),
                        tint = tokens.OnSurfaceInverse,
                    )
                }
            }
        }
    }
}
