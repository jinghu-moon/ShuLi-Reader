package com.shuli.reader.core.reader.engine.selection

import android.graphics.Paint
import android.graphics.RectF
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextLine
import com.shuli.reader.core.reader.model.TextPage

/**
 * 文本选区辅助：行命中检测、字符级选词、选区状态管理。
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
        return range
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
     * - CJK 字符连续序列视为一个词
     * - 英文字母/数字连续序列视为一个词
     * - 标点、空格为边界
     */
    private fun findWordBoundary(content: CharSequence, charIndex: Int): Pair<Int, Int> {
        if (charIndex < 0 || charIndex >= content.length) return Pair(charIndex, charIndex)

        val ch = content[charIndex]

        // 空格或标点：不选词
        if (ch.isWhitespace() || isPunctuation(ch)) {
            return Pair(charIndex, charIndex)
        }

        // CJK 字符：向两侧扩展到非 CJK 字符
        if (isCJK(ch)) {
            var start = charIndex
            var end = charIndex + 1
            while (start > 0 && isCJK(content[start - 1])) start--
            while (end < content.length && isCJK(content[end])) end++
            return Pair(start, end)
        }

        // 英文字母/数字：向两侧扩展到非字母数字
        var start = charIndex
        var end = charIndex + 1
        while (start > 0 && content[start - 1].isLetterOrDigit()) start--
        while (end < content.length && content[end].isLetterOrDigit()) end++
        return Pair(start, end)
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
    }
}
