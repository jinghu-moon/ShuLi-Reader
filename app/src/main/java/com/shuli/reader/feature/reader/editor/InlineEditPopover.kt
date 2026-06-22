package com.shuli.reader.feature.reader.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * 内联编辑气泡：精确定位在目标文字上方，实时回调文本变化。
 *
 * @param initialText 初始文本（被编辑的词）
 * @param anchorX 目标文字起始 X（屏幕像素）
 * @param anchorY 目标文字 baseline Y（屏幕像素）
 * @param fontSize 与 Canvas 正文一致的字号
 * @param textColor 与 Canvas 正文一致的字号色
 * @param onTextChange 文本变化实时回调（用于 Canvas 预览）
 * @param onConfirm 确认编辑
 * @param onDismiss 取消编辑
 */
@Composable
fun InlineEditPopover(
    initialText: String,
    anchorX: Float,
    anchorY: Float,
    fontSize: TextUnit,
    textColor: androidx.compose.ui.graphics.Color,
    onTextChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = EditorTokens
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var editText by remember(initialText) { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(initialText) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // 定位：输入框左边缘对齐文字起始 X，底部在 baseline 上方
    val fontSizePx = with(density) { fontSize.toPx() }
    val paddingPx = with(density) { 8.dp.toPx() }
    val lineHeightPx = with(density) { 48.dp.toPx() }  // 弹窗总高度
    val popupOffsetX = (anchorX - paddingPx).toInt()
    val popupOffsetY = (anchorY - lineHeightPx).toInt()  // 在 baseline 上方显示

    Popup(
        offset = IntOffset(popupOffsetX, popupOffsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        BasicTextField(
            value = editText,
            onValueChange = {
                editText = it
                onTextChange(it)
            },
            textStyle = TextStyle(
                fontSize = fontSize,
                color = textColor,
            ),
            cursorBrush = SolidColor(tokens.Primary),
            singleLine = true,
            modifier = modifier
                .drawBehind {
                    val triangleSize = 6.dp.toPx()
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(paddingPx, size.height)
                        lineTo(paddingPx + triangleSize, size.height + triangleSize)
                        lineTo(paddingPx + triangleSize * 2, size.height)
                        close()
                    }
                    drawPath(path, tokens.SurfaceInverse)
                }
                .background(tokens.SurfaceInverse, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onConfirm(editText) },
            ),
        )
    }
}

/**
 * 编辑模式空白区域点击后的光标输入层。
 */
@Composable
fun CursorEditField(
    anchorX: Float,
    anchorY: Float,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var draftText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val popupOffsetX = anchorX.toInt()
    val popupOffsetY = with(density) { (anchorY - 28.dp.toPx()).toInt() }

    Popup(
        offset = IntOffset(popupOffsetX, popupOffsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        BasicTextField(
            value = draftText,
            onValueChange = { draftText = it },
            textStyle = TextStyle(
                fontSize = 20.sp,
                color = EditorTokens.TextPrimary,
            ),
            cursorBrush = SolidColor(EditorTokens.Primary),
            singleLine = true,
            modifier = modifier
                .widthIn(min = 2.dp, max = 220.dp)
                .height(32.dp)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onConfirm(draftText) },
            ),
        )
    }
}
