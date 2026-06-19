package com.shuli.reader.core.reader.engine.selection

import android.graphics.Paint
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

    /** 选区起始把手位置（用于拖动调整） */
    var startHandlePosition: HandlePosition? = null
        private set

    /** 选区结束把手位置（用于拖动调整） */
    var endHandlePosition: HandlePosition? = null
        private set

    /** 当前正在拖动的把手类型 */
    var activeHandle: HandleType? = null
        private set

    /**
     * 把手类型
     */
    enum class HandleType {
        START, END
    }

    /**
     * 把手位置信息
     */
    data class HandlePosition(
        val x: Float,
        val y: Float,
        val lineIndex: Int,
        val charIndex: Int,
    )

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
        updateHandlePositions(page, content, viewWidth)
        return range
    }

    /**
     * 字符级选词：检测长按位置命中的单词/词组。
     *
     * 利用 TextLine.charWidths 在行内定位字符，然后向两侧扩展找到词边界。
     * - CJK 连续字符视为一个词
     * - 标点/空格为边界
     *
     * @param x 触摸点 x 坐标
     * @param y 触摸点 y 坐标
     * @param page 当前页面
     * @param content 页面文本内容
     * @param viewWidth 视图宽度
     * @param paint 文本画笔（用于测量字符宽度）
     * @return 词级选区，未命中返回 null
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
        selectedRange = range
        updateHandlePositions(page, content, viewWidth)
        return range
    }

    /**
     * 检测触摸点是否命中选区把手
     *
     * @param x 触摸点 x 坐标
     * @param y 触摸点 y 坐标
     * @param handleRadius 把手检测半径
     * @return 命中的把手类型，未命中返回 null
     */
    fun hitTestHandle(x: Float, y: Float, handleRadius: Float = HANDLE_RADIUS): HandleType? {
        val startHandle = startHandlePosition
        val endHandle = endHandlePosition

        if (startHandle != null) {
            val dx = x - startHandle.x
            val dy = y - startHandle.y
            if (dx * dx + dy * dy <= handleRadius * handleRadius) {
                return HandleType.START
            }
        }

        if (endHandle != null) {
            val dx = x - endHandle.x
            val dy = y - endHandle.y
            if (dx * dx + dy * dy <= handleRadius * handleRadius) {
                return HandleType.END
            }
        }

        return null
    }

    /**
     * 开始拖动选区把手
     *
     * @param handleType 要拖动的把手类型
     */
    fun startHandleDrag(handleType: HandleType) {
        activeHandle = handleType
    }

    /**
     * 更新选区把手位置（拖动中）
     *
     * @param x 触摸点 x 坐标
     * @param y 触摸点 y 坐标
     * @param page 当前页面
     * @param content 页面文本内容
     * @param viewWidth 视图宽度
     * @param paint 文本画笔
     * @return 更新后的选区
     */
    fun updateHandleDrag(
        x: Float,
        y: Float,
        page: TextPage,
        content: CharSequence,
        viewWidth: Float,
        paint: Paint? = null,
    ): SelectionRange? {
        val handle = activeHandle ?: return selectedRange

        // 找到触摸点所在的行
        val line = page.lines.firstOrNullIndexed { index, _ ->
            val bounds = lineBounds(index, page, viewWidth)
            x >= 0f && x <= viewWidth && y >= bounds.top && y <= bounds.bottom
        }

        if (line == null) {
            // 触摸点超出文本区域，扩展到行首或行尾
            return selectedRange
        }

        // 找到触摸点对应的字符索引
        val charIndex = findCharIndexInLine(x, line, page, content, paint)
            ?: return selectedRange

        // 更新选区范围
        val currentRange = selectedRange ?: return null
        val newRange = when (handle) {
            HandleType.START -> {
                // 拖动起始把手，确保不超过结束位置
                val newStart = minOf(charIndex, currentRange.endPos - 1)
                if (newStart == currentRange.startPos) return selectedRange
                val selectedText = content.substring(newStart, currentRange.endPos)
                SelectionRange(
                    chapterIndex = currentRange.chapterIndex,
                    startPos = newStart,
                    endPos = currentRange.endPos,
                    selectedText = selectedText,
                )
            }
            HandleType.END -> {
                // 拖动结束把手，确保不小于起始位置
                val newEnd = maxOf(charIndex + 1, currentRange.startPos + 1)
                if (newEnd == currentRange.endPos) return selectedRange
                val selectedText = content.substring(currentRange.startPos, newEnd)
                SelectionRange(
                    chapterIndex = currentRange.chapterIndex,
                    startPos = currentRange.startPos,
                    endPos = newEnd,
                    selectedText = selectedText,
                )
            }
        }

        selectedRange = newRange
        updateHandlePositions(page, content, viewWidth)
        return newRange
    }

    /**
     * 结束拖动选区把手
     */
    fun endHandleDrag() {
        activeHandle = null
    }

    /**
     * 更新把手位置
     */
    private fun updateHandlePositions(page: TextPage, content: CharSequence, viewWidth: Float) {
        val range = selectedRange ?: return

        // 找到起始位置所在的行
        val startLineIndex = page.lines.indexOfFirst { line ->
            range.startPos >= line.startCharOffset && range.startPos < line.endCharOffset
        }
        if (startLineIndex >= 0) {
            val startLine = page.lines[startLineIndex]
            val bodyLeft = page.layout.body.left
            val startX = bodyLeft + startLine.startXOffset + getCharOffsetInRange(startLine, range.startPos, content)
            startHandlePosition = HandlePosition(
                x = startX,
                y = startLine.bottom,
                lineIndex = startLineIndex,
                charIndex = range.startPos,
            )
        }

        // 找到结束位置所在的行
        val endCharIndex = range.endPos - 1
        val endLineIndex = page.lines.indexOfFirst { line ->
            endCharIndex >= line.startCharOffset && endCharIndex < line.endCharOffset
        }
        if (endLineIndex >= 0) {
            val endLine = page.lines[endLineIndex]
            val bodyLeft = page.layout.body.left
            val endX = bodyLeft + endLine.startXOffset + getCharOffsetInRange(endLine, endCharIndex, content)
            endHandlePosition = HandlePosition(
                x = endX,
                y = endLine.bottom,
                lineIndex = endLineIndex,
                charIndex = endCharIndex,
            )
        }
    }

    /**
     * 计算字符在行内的 x 偏移
     */
    private fun getCharOffsetInRange(line: TextLine, charIndex: Int, content: CharSequence): Float {
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
     *
     * 优先使用 charWidths 数组（两端对齐模式），否则用 Paint 测量
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
     *
     * 规则：
     * - CJK 字符：使用前向最大匹配，最多 4 个连续字符
     * - 英文字母/数字连续序列视为一个词（保留内部连字符和撇号）
     * - 标点、空格为边界
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
     *
     * 从 charIndex 开始，尝试匹配 2-4 个连续 CJK 字符
     * 返回最长匹配的词边界
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

    /**
     * 判断是否为单词字符（字母、数字、连字符、撇号）
     */
    private fun isWordChar(ch: Char): Boolean {
        return ch.isLetterOrDigit() || ch == '-' || ch == '\''
    }

    /** 判断是否为 CJK 字符（中文、日文、韩文） */
    private fun isCJK(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF ||   // CJK Unified Ideographs
            code in 0x3400..0x4DBF ||   // CJK Unified Ideographs Extension A
            code in 0x20000..0x2A6DF || // CJK Unified Ideographs Extension B
            code in 0xF900..0xFAFF ||   // CJK Compatibility Ideographs
            code in 0x2F800..0x2FA1F || // CJK Compatibility Ideographs Supplement
            code in 0x3040..0x309F ||   // Hiragana
            code in 0x30A0..0x30FF ||   // Katakana
            code in 0xAC00..0xD7AF     // Hangul Syllables
    }

    /** 判断是否为标点符号 */
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
        startHandlePosition = null
        endHandlePosition = null
        activeHandle = null
        return old
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
        const val HANDLE_RADIUS = 24f
        /** 把手视觉大小（像素） */
        const val HANDLE_SIZE = 12f
    }
}
