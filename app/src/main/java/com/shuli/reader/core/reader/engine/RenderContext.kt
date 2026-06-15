package com.shuli.reader.core.reader.engine

import com.shuli.reader.core.reader.model.SlotResolution
import android.graphics.Paint
import com.shuli.reader.core.reader.model.SelectionRange

/**
 * 渲染上下文：页眉页脚文本/槽位、透明度、进度条、电池、选区、笔记高亮。
 *
 * 从 ReaderCanvasView 的 private inner class 提取为包级顶层类，
 * 供 PageBitmapCache 和 ReaderCanvasView 共享。
 */
class RenderContext {
    var headerText: String = ""
    var footerText: String = ""
    var headerSlots: SlotResolution = SlotResolution()
    var footerSlots: SlotResolution = SlotResolution()
    var showProgress: Boolean = true
    var headerAlpha: Float = 0.4f
    var footerAlpha: Float = 0.4f
    var batteryLevel: Int = 100
    var selectedRange: SelectionRange? = null
    var noteRanges: List<Pair<SelectionRange, Paint>> = emptyList()
    var showHeaderLine: Boolean = false
    var showFooterLine: Boolean = false
}
