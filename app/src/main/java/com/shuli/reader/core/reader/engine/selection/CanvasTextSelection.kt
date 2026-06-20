package com.shuli.reader.core.reader.engine.selection

import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextLine
import com.shuli.reader.core.reader.model.TextPage

/**
 * 文本选区辅助：行命中检测、字符级选词、选区状态管理、选区范围调整。
 *
 * 从 ReaderCanvasView 拆出，独立测试选区逻辑。
 */
class CanvasTextSelection {

    /** 当前选区，null 表示无选中 */
    var selectedRange: SelectionRange? = null
        private set

    /** 选区起始字符偏移 */
    var selectStart: Int = -1
        private set

    /** 选区结束字符偏移（不含） */
    var selectEnd: Int = -1
        private set

    /** 是否正在拖动把手 */
    var isSelecting: Boolean = false
        private set

    /** 当前正在拖动的把手类型 */
    var activeHandle: HandleType? = null
        private set

    /** 把手是否交叉反转 */
    var reverseHandles: Boolean = false
        private set

    /** 选区起始把手屏幕坐标 */
    var startHandleScreenX: Float = 0f
        private set
    var startHandleScreenY: Float = 0f
        private set

    /** 选区结束把手屏幕坐标 */
    var endHandleScreenX: Float = 0f
        private set
    var endHandleScreenY: Float = 0f
        private set

    /**
     * 把手类型
     */
    enum class HandleType {
        START, END
    }

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
        setSelection(range)
        return range
    }

    /**
     * 字符级选词：检测长按位置命中的单词/词组。
     */
    fun selectWordAt(
        x: Float,
        y: Float,
        page: TextPage,
        content: CharSequence,
        viewWidth: Float,
        paint: Paint? = null,
    ): SelectionRange? {
        // 1. 命中检测：找到触摸点所在的行
        val line = page.lines.firstOrNullIndexed { index, _ ->
            val bounds = lineBounds(index, page, viewWidth)
            x >= 0f && x <= viewWidth && y >= bounds.top && y <= bounds.bottom
        } ?: return null

        // 2. 字符级定位：在行内找到触摸点对应的字符索引
        val charIndex = findCharIndexInLine(x, line, page, content, paint)
            ?: return null

        // 3. 词边界扩展：向两侧扩展找到完整单词
        val (wordStart, wordEnd) = findWordBoundary(content, charIndex)

        if (wordStart >= wordEnd) return null

        val selectedText = content.substring(wordStart, wordEnd)
        val range = SelectionRange(
            chapterIndex = page.chapterIndex,
            startPos = wordStart,
            endPos = wordEnd,
            selectedText = selectedText,
        )
        setSelection(range)
        return range
    }

    /**
     * 设置选区范围
     */
    private fun setSelection(range: SelectionRange) {
        selectedRange = range
        selectStart = range.startPos
        selectEnd = range.endPos
        reverseHandles = false
    }

    /**
     * 检测触摸点是否命中选区把手
     *
     * @param x 触摸点 x 坐标
     * @param y 触摸点 y 坐标
     * @param handleRadius 把手检测半径
     * @return 命中的把手类型，未命中返回 null
     */
    fun hitTestHandle(
        x: Float,
        y: Float,
        handleRadius: Float = HANDLE_TOUCH_RADIUS,
    ): HandleType? {
        // 检测起始把手
        val dxStart = x - startHandleScreenX
        val dyStart = y - startHandleScreenY
        val distStartSq = dxStart * dxStart + dyStart * dyStart
        if (distStartSq <= handleRadius * handleRadius) {
            return if (reverseHandles) HandleType.END else HandleType.START
        }

        // 检测结束把手
        val dxEnd = x - endHandleScreenX
        val dyEnd = y - endHandleScreenY
        val distEndSq = dxEnd * dxEnd + dyEnd * dyEnd
        if (distEndSq <= handleRadius * handleRadius) {
            return if (reverseHandles) HandleType.START else HandleType.END
        }

        return null
    }

    /**
     * 开始拖动选区把手
     */
    fun startHandleDrag(handleType: HandleType) {
        activeHandle = handleType
        isSelecting = true
    }

    /**
     * 更新选区把手位置（拖动中）
     *
     * @param charIndex 新的字符位置
     * @param content 页面文本内容
     * @return 更新后的选区
     */
    fun moveHandle(charIndex: Int, content: CharSequence): SelectionRange? {
        val handle = activeHandle ?: return selectedRange

        when (handle) {
            HandleType.START -> {
                // 拖动起始把手
                val newStart = charIndex.coerceIn(0, selectEnd - 1)
                if (newStart == selectStart) return selectedRange
                selectStart = newStart
            }
            HandleType.END -> {
                // 拖动结束把手（end 是不含的，所以要 +1）
                val newEnd = (charIndex + 1).coerceIn(selectStart + 1, content.length)
                if (newEnd == selectEnd) return selectedRange
                selectEnd = newEnd
            }
        }

        // 检查是否需要反转把手
        if (selectStart >= selectEnd) {
            val temp = selectStart
            selectStart = selectEnd - 1
            selectEnd = temp + 1
            reverseHandles = !reverseHandles
            // 切换活跃把手
            activeHandle = if (activeHandle == HandleType.START) HandleType.END else HandleType.START
        }

        // 更新选区
        val selectedText = content.substring(selectStart, selectEnd)
        selectedRange = SelectionRange(
            chapterIndex = selectedRange?.chapterIndex ?: 0,
            startPos = selectStart,
            endPos = selectEnd,
            selectedText = selectedText,
        )
        return selectedRange
    }

    /**
     * 结束拖动选区把手
     */
    fun endHandleDrag() {
        isSelecting = false
        activeHandle = null
    }

    /**
     * 获取把手的像素矩形（用于绘制和命中检测）
     *
     * @return Pair<startRect, endRect>，如果没有选区返回 null
     */
    fun getHandleRects(page: TextPage, viewWidth: Float): Pair<RectF, RectF>? {
        if (selectStart < 0 || selectEnd <= 0) return null

        val bodyLeft = page.layout.body.left

        // 找到起始位置所在的行
        val startLine = page.lines.firstOrNull { line ->
            selectStart >= line.startCharOffset && selectStart < line.endCharOffset
        }
        // 找到结束位置所在的行
        val endCharIndex = selectEnd - 1
        val endLine = page.lines.firstOrNull { line ->
            endCharIndex >= line.startCharOffset && endCharIndex < line.endCharOffset
        }

        if (startLine == null || endLine == null) return null

        // 起始把手圆点位于选区左上角，结束把手圆点位于选区右下角。
        val startX = bodyLeft + startLine.startXOffset + getCharXOffset(startLine, selectStart)
        startHandleScreenX = startX
        startHandleScreenY = startLine.top
        val startRect = RectF(
            startX - HANDLE_DOT_RADIUS,
            startLine.top - HANDLE_DOT_RADIUS,
            startX + HANDLE_DOT_RADIUS,
            startLine.bottom
        )

        val endX = bodyLeft + endLine.startXOffset + getCharXOffset(endLine, selectEnd)
        endHandleScreenX = endX
        endHandleScreenY = endLine.bottom
        val endRect = RectF(
            endX - HANDLE_DOT_RADIUS,
            endLine.top,
            endX + HANDLE_DOT_RADIUS,
            endLine.bottom + HANDLE_DOT_RADIUS
        )

        return Pair(startRect, endRect)
    }

    /**
     * 根据字符偏移计算屏幕像素坐标
     */
    fun charToPixel(charOffset: Int, page: TextPage): PointF? {
        val line = page.lines.firstOrNull { line ->
            charOffset >= line.startCharOffset && charOffset < line.endCharOffset
        } ?: return null

        val bodyLeft = page.layout.body.left
        val x = bodyLeft + line.startXOffset + getCharXOffset(line, charOffset)
        val y = line.bottom
        return PointF(x, y)
    }

    /**
     * 根据像素坐标计算字符偏移
     */
    fun pixelToChar(
        x: Float,
        y: Float,
        page: TextPage,
        content: CharSequence,
        paint: Paint?,
    ): Int? {
        // 找到触摸点所在的行
        val line = page.lines.firstOrNullIndexed { _, line ->
            y >= line.top && y <= line.bottom
        } ?: return null

        return findCharIndexInLine(x, line, page, content, paint)
    }

    /**
     * 计算字符在行内的 x 偏移
     */
    private fun getCharXOffset(line: TextLine, charIndex: Int): Float {
        val lineStart = line.startCharOffset
        if (charIndex < lineStart) return 0f

        val charWidths = line.charWidths
        if (charWidths != null && charWidths.size == (line.endCharOffset - lineStart)) {
            var offset = 0f
            val relIndex = charIndex - lineStart
            for (i in 0 until relIndex.coerceAtMost(charWidths.size)) {
                offset += charWidths[i]
            }
            return offset
        }

        // 回退：估算
        return 0f
    }

    /**
     * 在行内定位触摸点对应的字符索引
     */
    private fun findCharIndexInLine(
        touchX: Float,
        line: TextLine,
        page: TextPage,
        content: CharSequence,
        paint: Paint?,
    ): Int? {
        val lineStart = line.startCharOffset
        val lineEnd = line.endCharOffset
        if (lineStart >= lineEnd) return null

        val bodyLeft = page.layout.body.left
        val relativeX = touchX - bodyLeft

        // 使用 charWidths 进行精确定位（如果有）
        val charWidths = line.charWidths
        if (charWidths != null && charWidths.size == (lineEnd - lineStart)) {
            var accX = line.startXOffset
            for (i in charWidths.indices) {
                val charWidth = charWidths[i]
                if (relativeX >= accX && relativeX < accX + charWidth) {
                    return lineStart + i
                }
                accX += charWidth
            }
            // 触摸点超出字符范围，返回最后一个字符
            return lineEnd - 1
        }

        // 回退：使用 Paint 测量
        if (paint == null) return lineStart

        val lineText = content.substring(lineStart, lineEnd)
        var accX = line.startXOffset
        for (i in lineText.indices) {
            val charWidth = paint.measureText(lineText, i, i + 1)
            if (relativeX >= accX && relativeX < accX + charWidth) {
                return lineStart + i
            }
            accX += charWidth
        }
        return lineEnd - 1
    }

    /**
     * 从指定字符位置向两侧扩展，找到词边界
     */
    private fun findWordBoundary(content: CharSequence, charIndex: Int): Pair<Int, Int> {
        if (charIndex < 0 || charIndex >= content.length) return Pair(charIndex, charIndex)

        val ch = content[charIndex]

        // 空格或标点：不选词
        if (ch.isWhitespace() || isPunctuation(ch)) {
            return Pair(charIndex, charIndex)
        }

        // CJK 字符：使用前向最大匹配
        if (isCJK(ch)) {
            return findCJKWordBoundary(content, charIndex)
        }

        // 英文字母/数字：向两侧扩展到非字母数字（保留内部连字符和撇号）
        var start = charIndex
        var end = charIndex + 1
        while (start > 0 && isWordChar(content[start - 1])) start--
        while (end < content.length && isWordChar(content[end])) end++
        return Pair(start, end)
    }

    /**
     * CJK 词边界检测（前向最大匹配）
     */
    private fun findCJKWordBoundary(content: CharSequence, charIndex: Int): Pair<Int, Int> {
        val maxWordLen = 4
        val start = charIndex

        // 向前找到 CJK 词的起始位置
        var wordStart = start
        while (wordStart > 0 && isCJK(content[wordStart - 1]) && (start - wordStart) < maxWordLen - 1) {
            wordStart--
        }

        // 向后找到 CJK 词的结束位置
        var wordEnd = start + 1
        while (wordEnd < content.length && isCJK(content[wordEnd]) && (wordEnd - wordStart) < maxWordLen) {
            wordEnd++
        }

        // 如果只有一个字符，尝试向前扩展
        if (wordEnd - wordStart <= 1 && wordStart > 0 && isCJK(content[wordStart - 1])) {
            wordStart--
        }

        return Pair(wordStart, wordEnd)
    }

    private fun isWordChar(ch: Char): Boolean {
        return ch.isLetterOrDigit() || ch == '-' || ch == '\''
    }

    private fun isCJK(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0x20000..0x2A6DF ||
            code in 0xF900..0xFAFF ||
            code in 0x2F800..0x2FA1F ||
            code in 0x3040..0x309F ||
            code in 0x30A0..0x30FF ||
            code in 0xAC00..0xD7AF
    }

    private fun isPunctuation(ch: Char): Boolean {
        return ch.category in setOf(
            CharCategory.DASH_PUNCTUATION,
            CharCategory.START_PUNCTUATION,
            CharCategory.END_PUNCTUATION,
            CharCategory.OTHER_PUNCTUATION,
            CharCategory.CONNECTOR_PUNCTUATION,
            CharCategory.INITIAL_QUOTE_PUNCTUATION,
            CharCategory.FINAL_QUOTE_PUNCTUATION,
        ) || ch in ".,;:!?\"'()[]{}<>/\\@#$%^&*+-=~|`"
    }

    /** 清除选区 */
    fun clearSelection(): SelectionRange? {
        val old = selectedRange
        selectedRange = null
        selectStart = -1
        selectEnd = -1
        isSelecting = false
        activeHandle = null
        reverseHandles = false
        return old
    }

    /**
     * 检测触摸点是否在选区内部
     *
     * @param x 触摸点 x 坐标
     * @param y 触摸点 y 坐标
     * @return true 表示在选区内部
     */
    fun isPointInSelection(x: Float, y: Float): Boolean {
        val range = selectedRange ?: return false

        // 简化检测：检查触摸点是否在选区的 Y 范围内
        // 由于选区可能跨行，我们检查把手的 Y 坐标范围
        val padding = HANDLE_TOUCH_RADIUS
        val minY = minOf(startHandleScreenY, endHandleScreenY) - padding
        val maxY = maxOf(startHandleScreenY, endHandleScreenY) + padding

        return y in minY..maxY
    }

    /** 计算行的选区矩形 */
    fun lineBounds(index: Int, page: TextPage, viewWidth: Float): RectF {
        val line = page.lines.getOrNull(index) ?: return RectF()
        val startX = page.layout.body.left + line.startXOffset
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
        /** 把手检测半径（像素） */
        const val HANDLE_TOUCH_RADIUS = SelectionVisualStyle.HANDLE_TOUCH_RADIUS
        /** 把手视觉大小（像素） */
        const val HANDLE_DOT_RADIUS = SelectionVisualStyle.HANDLE_DOT_RADIUS
    }
}
