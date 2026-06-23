package com.shuli.reader.feature.reader.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.Alignment
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
 * 内联编辑气泡：精确定位在目标文字上方/下方，实时回调文本变化。
 *
 * 定位逻辑与选区菜单（ReaderSelectionActionBar）完全一致：
 * - 使用 Alignment.TopCenter + 偏移量
 * - 水平居中对齐选区中心
 * - 垂直方向优先显示在选区下方，空间不足时显示在上方
 *
 * @param initialText 初始文本（被编辑的词）
 * @param anchorStartX 选区起始 X（屏幕像素）
 * @param anchorEndX 选区结束 X（屏幕像素）
 * @param anchorY 选区 Y 坐标（屏幕像素）
 * @param screenWidth 屏幕宽度（像素）
 * @param screenHeight 屏幕高度（像素）
 * @param fontSize 与 Canvas 正文一致的字号
 * @param textColor 文字颜色（弹窗深色背景上固定使用浅色，此参数保留兼容）
 * @param onTextChange 文本变化实时回调（用于 Canvas 预览）
 * @param onConfirm 确认编辑
 * @param onDismiss 取消编辑
 */
@Composable
fun InlineEditPopover(
    initialText: String,
    anchorStartX: Float,
    anchorEndX: Float,
    anchorY: Float,
    screenWidth: Float,
    screenHeight: Float,
    fontSize: TextUnit,
    textColor: androidx.compose.ui.graphics.Color,
    onTextChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = EditorTokens
    val keyboardController = LocalSoftwareKeyboardController.current
    var editText by remember(initialText) { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(initialText) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // ── 定位：与选区菜单（ReaderSelectionActionBar）完全一致 ──
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
    val visibleBottom = if (screenHeight > 0f) {
        (screenHeight - imeBottomPx).coerceAtLeast(0f)
    } else {
        0f
    }
    val handleGapPx = with(density) { EditorTokens.InlineEditTopOffset.roundToPx() }

    // 估算弹窗宽度
    val maxPopupWidthDp = 300.dp
    val maxPopupWidthPx = with(density) { maxPopupWidthDp.roundToPx() }
    val charCount = initialText.length.coerceAtLeast(2)
    val estimatedTextWidthPx = with(density) {
        (fontSize.value * charCount * 0.65f + 32f).dp.roundToPx()
    }
    val popupWidthPx = estimatedTextWidthPx.coerceAtMost(maxPopupWidthPx)

    // 弹窗高度：字号 + 上下 padding + 三角箭头
    val popupHeightPx = with(density) {
        (fontSize.value + 28f).dp.roundToPx()
    }

    // 选区中心 X 和 Y
    val selCenterX = (anchorStartX + anchorEndX) / 2f
    val selY = anchorY

    // ── 垂直定位：优先选区下方；键盘弹出时限制在键盘上方 ──
    val availableBottom = if (visibleBottom > 0f) visibleBottom else screenHeight
    val preferBelow = availableBottom <= 0f ||
        selY + handleGapPx + popupHeightPx <= availableBottom - handleGapPx ||
        selY < popupHeightPx + handleGapPx

    val popupOffsetY = if (preferBelow) {
        selY + handleGapPx
    } else {
        selY - popupHeightPx - handleGapPx
    }
    val clampedOffsetY = if (availableBottom > 0f) {
        val maxY = (availableBottom - popupHeightPx - handleGapPx).coerceAtLeast(handleGapPx.toFloat())
        popupOffsetY.coerceIn(handleGapPx.toFloat(), maxY)
    } else {
        popupOffsetY
    }
    val popupBelowAnchor = clampedOffsetY >= selY

    // ── 水平定位：弹窗中心对齐选区中心（与选区菜单一致）──
    val popupOffsetX = if (screenWidth > 0f && selCenterX > 0f) {
        val minCenterX = popupWidthPx / 2f + handleGapPx
        val maxCenterX = (screenWidth - popupWidthPx / 2f - handleGapPx).coerceAtLeast(minCenterX)
        selCenterX.coerceIn(minCenterX, maxCenterX) - screenWidth / 2f
    } else {
        0f
    }

    // popup 深色背景上的文字始终用浅色，确保可读
    val effectiveTextColor = tokens.OnSurfaceInverse

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(popupOffsetX.toInt(), clampedOffsetY.toInt()),
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
                color = effectiveTextColor,
            ),
            cursorBrush = SolidColor(tokens.Primary),
            singleLine = true,
            modifier = modifier
                .widthIn(min = 100.dp, max = maxPopupWidthDp)
                .drawBehind {
                    val triangleSize = 6.dp.toPx()
                    val path = androidx.compose.ui.graphics.Path().apply {
                        if (popupBelowAnchor) {
                            // 弹窗在选区下方，三角朝上
                            moveTo(size.width / 2 - triangleSize, 0f)
                            lineTo(size.width / 2, -triangleSize)
                            lineTo(size.width / 2 + triangleSize, 0f)
                        } else {
                            // 弹窗在选区上方，三角朝下
                            moveTo(size.width / 2 - triangleSize, size.height)
                            lineTo(size.width / 2, size.height + triangleSize)
                            lineTo(size.width / 2 + triangleSize, size.height)
                        }
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
    screenWidth: Float,
    screenHeight: Float,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = EditorTokens
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var draftText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // ── 定位：与选区菜单一致 ──
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
    val visibleBottom = if (screenHeight > 0f) {
        (screenHeight - imeBottomPx).coerceAtLeast(0f)
    } else {
        0f
    }
    val handleGapPx = with(density) { EditorTokens.InlineEditTopOffset.roundToPx() }
    val maxPopupWidthDp = 300.dp
    val popupWidthPx = with(density) { 120.dp.roundToPx() }
    val popupHeightPx = with(density) { (16f + 28f).dp.roundToPx() }

    val selY = anchorY
    val availableBottom = if (visibleBottom > 0f) visibleBottom else screenHeight
    val preferBelow = availableBottom <= 0f ||
        selY + handleGapPx + popupHeightPx <= availableBottom - handleGapPx ||
        selY < popupHeightPx + handleGapPx

    val popupOffsetY = if (preferBelow) {
        selY + handleGapPx
    } else {
        selY - popupHeightPx - handleGapPx
    }
    val clampedOffsetY = if (availableBottom > 0f) {
        val maxY = (availableBottom - popupHeightPx - handleGapPx).coerceAtLeast(handleGapPx.toFloat())
        popupOffsetY.coerceIn(handleGapPx.toFloat(), maxY)
    } else {
        popupOffsetY
    }

    val popupOffsetX = if (screenWidth > 0f && anchorX > 0f) {
        val minCenterX = popupWidthPx / 2f + handleGapPx
        val maxCenterX = (screenWidth - popupWidthPx / 2f - handleGapPx).coerceAtLeast(minCenterX)
        anchorX.coerceIn(minCenterX, maxCenterX) - screenWidth / 2f
    } else {
        0f
    }

    val effectiveTextColor = tokens.OnSurfaceInverse

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(popupOffsetX.toInt(), clampedOffsetY.toInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        BasicTextField(
            value = draftText,
            onValueChange = { draftText = it },
            textStyle = TextStyle(
                fontSize = 16.sp,
                color = effectiveTextColor,
            ),
            cursorBrush = SolidColor(tokens.Primary),
            singleLine = true,
            modifier = modifier
                .widthIn(min = 100.dp, max = maxPopupWidthDp)
                .background(tokens.SurfaceInverse, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onConfirm(draftText) },
            ),
        )
    }
}
