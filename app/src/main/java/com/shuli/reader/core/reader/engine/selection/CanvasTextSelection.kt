package com.shuli.reader.core.reader.engine.selection

import android.graphics.Paint
import android.graphics.RectF
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextPage

/**
 * 文本选区辅助：行命中检测、选区计算、选区状态管理。
 *
 * 从 ReaderCanvasView 拆出，独立测试选区逻辑。
 */
class CanvasTextSelection {

    /** 当前选区，null 表示无选中 */
    var selectedRange: SelectionRange? = null
        private set

    /** 检测长按位置是否命中某行，命中则计算选区并返回 */
    fun selectLineAt(
        x: Float,
        y: Float,
        page: TextPage,
        content: CharSequence,
        viewWidth: Float,
    ): SelectionRange? {
        val line = page.lines.firstOrNullIndexed { index, _ ->
            val bounds = lineBounds(index, page, viewWidth)
            x >= 0f && x <= viewWidth && y >= bounds.top && y <= bounds.bottom
        } ?: return null

        val selectedText = content.substring(line.startCharOffset, line.endCharOffset)
        val range = SelectionRange(
            chapterIndex = page.chapterIndex,
            startPos = line.startCharOffset,
            endPos = line.endCharOffset,
            selectedText = selectedText,
        )
        selectedRange = range
        return range
    }

    /** 清除选区 */
    fun clearSelection(): SelectionRange? {
        val old = selectedRange
        selectedRange = null
        return old
    }

    /** 计算行的选区矩形 */
    fun lineBounds(index: Int, page: TextPage, viewWidth: Float): RectF {
        val line = page.lines.getOrNull(index) ?: return RectF()
        val startX = page.marginHorizontal + line.startXOffset
        val right = (startX + line.measuredWidth).coerceAtMost(viewWidth - TEXT_END_PADDING)
        return RectF(
            startX - SELECTION_HORIZONTAL_PADDING,
            line.top,
            right + SELECTION_HORIZONTAL_PADDING,
            line.bottom,
        )
    }

    private inline fun <T> List<T>.firstOrNullIndexed(predicate: (Int, T) -> Boolean): T? {
        for (index in indices) {
            val item = this[index]
            if (predicate(index, item)) return item
        }
        return null
    }

    companion object {
        private const val TEXT_END_PADDING = 20f
        private const val SELECTION_HORIZONTAL_PADDING = 6f
    }
}
