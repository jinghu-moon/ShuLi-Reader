package com.shuli.reader.feature.reader.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.reader.ReaderCanvasView
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.feature.reader.ReaderViewModel

/**
 * 阅读器运行时状态相关 LaunchedEffect 集合。
 *
 * 职责：主题颜色、电池电量、TTS 高亮、选区清除、笔记高亮。
 * 仅在 CanvasView 非 null 时生效，绕过 recomposition 直接操作 View。
 */
@Composable
fun ReaderRuntimeEffects(
    canvasView: ReaderCanvasView?,
    themeColors: ThemeColors,
    batteryLevel: Int,
    ttsActiveRange: SelectionRange?,
    selectedRange: SelectionRange?,
    noteHashes: Pair<Int, Int>,
    viewModel: ReaderViewModel,
) {
    // 主题颜色
    LaunchedEffect(themeColors) {
        canvasView?.setThemeColors(themeColors)
    }

    // 电池
    LaunchedEffect(batteryLevel) {
        canvasView?.setBatteryLevel(batteryLevel)
    }

    // TTS 高亮
    LaunchedEffect(ttsActiveRange) {
        canvasView?.setTtsActiveRange(ttsActiveRange)
    }

    // 选区清除
    LaunchedEffect(selectedRange) {
        if (selectedRange == null) {
            canvasView?.clearSelection()
        }
    }

    // 笔记高亮
    LaunchedEffect(noteHashes) {
        canvasView?.setNoteRanges(viewModel.getVisibleNoteRanges())
    }
}
