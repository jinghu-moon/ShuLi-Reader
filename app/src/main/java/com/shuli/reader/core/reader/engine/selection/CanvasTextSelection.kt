package com.shuli.reader.core.reader.engine.selection

import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextLine
import com.shuli.reader.core.reader.model.TextPage

/**
 * 文本选区辅助：纯中立锚点架构
 */
class CanvasTextSelection {

    /** 当前选区，null 表示无选中 */
    var selectedRange: SelectionRange? = null
        private set

    /** 选区起始字符偏移（左/上） */
    var selectStart: Int = -1
        private set

    /** 选区结束字符偏移（右/下，不含） */
    var selectEnd: Int = -1
        private set

    /** 是否正在拖动把手 */
    var isSelecting: Boolean = false
        private set

    enum class AnchorId {
        A, B
    }

    // 中立锚点：A 和 B，代表文本中的真实偏移量
    private var anchorA: Int = -1
    private var anchorB: Int = -1
    
    // 当前正在被拖动的锚点
    var activeAnchor: AnchorId? = null
        private set

    // 防抖视觉映射：A 是否是视觉上的 START 把手
    // 当 anchorA == anchorB 时，保持此值不变，实现视觉角色的 hysteresis
    var anchorAIsStart: Boolean = true
        private set

    // 锚点的屏幕坐标
    private var anchorAX: Float = 0f
    private var anchorAY: Float = 0f
    private var anchorBX: Float = 0f
    private var anchorBY: Float = 0f

    /**
     * 把手的绘制和坐标信息
     */
    data class HandleInfo(
        val rect: RectF,
        val isStart: Boolean,
        val anchorId: AnchorId
    )

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

        return setSelection(line.startCharOffset, line.endCharOffset, page.chapterIndex, content)
    }

    fun selectWordAt(
        x: Float,
        y: Float,
        page: TextPage,
        content: CharSequence,
        viewWidth: Float,
        paint: Paint? = null,
    ): SelectionRange? {
        val line = page.lines.firstOrNullIndexed { index, _ ->
            val bounds = lineBounds(index, page, viewWidth)
            x >= 0f && x <= viewWidth && y >= bounds.top && y <= bounds.bottom
        } ?: return null

        val charIndex = findCharIndexInLine(x, line, page, content, paint)
            ?: return null

        val (wordStart, wordEnd) = findWordBoundary(content, charIndex)
        if (wordStart >= wordEnd) return null

        return setSelection(wordStart, wordEnd, page.chapterIndex, content)
    }

    private fun setSelection(start: Int, end: Int, chapterIndex: Int, content: CharSequence): SelectionRange {
        anchorA = start
        anchorB = end
        anchorAIsStart = true
        activeAnchor = null
        isSelecting = false
        return syncSelectionFromAnchors(chapterIndex, content)!!
    }

    /**
     * 命中检测：直接判定触摸点距离哪个锚点的屏幕坐标更近
     */
    fun hitTestHandle(
        x: Float,
        y: Float,
        handleRadius: Float = HANDLE_TOUCH_RADIUS,
    ): AnchorId? {
        val dxA = x - anchorAX
        val dyA = y - anchorAY
        val distASq = dxA * dxA + dyA * dyA

        val dxB = x - anchorBX
        val dyB = y - anchorBY
        val distBSq = dxB * dxB + dyB * dyB

        val radiusSq = handleRadius * handleRadius
        val hitA = distASq <= radiusSq
        val hitB = distBSq <= radiusSq

        return when {
            hitA && hitB -> if (distASq <= distBSq) AnchorId.A else AnchorId.B
            hitA -> AnchorId.A
            hitB -> AnchorId.B
            else -> null
        }
    }

    fun startHandleDrag(anchorId: AnchorId) {
        activeAnchor = anchorId
        isSelecting = true
    }

    fun moveHandle(charIndex: Int, content: CharSequence): SelectionRange? {
        if (content.isEmpty() || activeAnchor == null) return selectedRange
        val clamped = charIndex.coerceIn(0, content.length)

        if (activeAnchor == AnchorId.A) {
            anchorA = clamped
        } else {
            anchorB = clamped
        }

        // 更新视觉映射，带有防抖：相等时不交换角色
        if (anchorA < anchorB) {
            anchorAIsStart = true
        } else if (anchorA > anchorB) {
            anchorAIsStart = false
        }

        return syncSelectionFromAnchors(selectedRange?.chapterIndex ?: 0, content)
    }

    private fun syncSelectionFromAnchors(chapterIndex: Int, content: CharSequence): SelectionRange? {
        if (anchorA < 0 || anchorB < 0 || anchorA > content.length || anchorB > content.length) {
            return selectedRange
        }

        selectStart = minOf(anchorA, anchorB)
        selectEnd = maxOf(anchorA, anchorB)

        // 允许选区为空（光标态），或者如果不允许，则在这里做判定
        // 为了防坍缩体验，拖动时可以允许 start == end
        
        val selectedText = if (selectStart < selectEnd) content.substring(selectStart, selectEnd) else ""
        val range = SelectionRange(
            chapterIndex = chapterIndex,
            startPos = selectStart,
            endPos = selectEnd,
            selectedText = selectedText,
        )
        selectedRange = range
        return range
    }

    fun endHandleDrag() {
        isSelecting = false
        activeAnchor = null
    }

    fun clearSelection(): SelectionRange? {
        val old = selectedRange
        selectedRange = null
        selectStart = -1
        selectEnd = -1
        isSelecting = false
        activeAnchor = null
        return old
    }

    /**
     * 获取把手的绘制信息
     */
    fun getHandleRects(page: TextPage, viewWidth: Float): List<HandleInfo>? {
        if (anchorA < 0 || anchorB < 0) return null

        val bodyLeft = page.layout.body.left

        // 计算 A 锚点的位置
        val (rectA, pxA, pyA) = calcAnchorRect(anchorA, page, bodyLeft, anchorAIsStart) ?: return null
        anchorAX = pxA
        anchorAY = pyA

        // 计算 B 锚点的位置
        val (rectB, pxB, pyB) = calcAnchorRect(anchorB, page, bodyLeft, !anchorAIsStart) ?: return null
        anchorBX = pxB
        anchorBY = pyB

        return listOf(
            HandleInfo(rectA, anchorAIsStart, AnchorId.A),
            HandleInfo(rectB, !anchorAIsStart, AnchorId.B)
        )
    }

    /**
     * 计算单个锚点的屏幕位置和 Rect
     * @param offset 锚点字符偏移
     * @param isStart 是否在视觉上作为 START 把手（决定了它的基准坐标取自 char index 之前还是之后，以及圆点朝上还是朝下）
     */
    private fun calcAnchorRect(
        offset: Int,
        page: TextPage,
        bodyLeft: Float,
        isStart: Boolean
    ): Triple<RectF, Float, Float>? {
        // 如果作为结束把手，它的视觉位置贴靠在上一个字符的末尾
        val searchOffset = if (!isStart && offset > 0) offset - 1 else offset
        val line = page.lines.firstOrNull { line ->
            searchOffset >= line.startCharOffset && searchOffset < line.endCharOffset
        } ?: return null

        val x = bodyLeft + line.startXOffset + getCharXOffset(line, offset)
        // 视觉上的 START 把手圆点朝上，坐标在 top；END 把手圆点朝下，坐标在 bottom
        val screenX = x
        val screenY = if (isStart) line.top else line.bottom

        val rect = if (isStart) {
            RectF(
                x - HANDLE_DOT_RADIUS,
                line.top - HANDLE_DOT_RADIUS,
                x + HANDLE_DOT_RADIUS,
                line.bottom
            )
        } else {
            RectF(
                x - HANDLE_DOT_RADIUS,
                line.top,
                x + HANDLE_DOT_RADIUS,
                line.bottom + HANDLE_DOT_RADIUS
            )
        }
        return Triple(rect, screenX, screenY)
    }

    /**
     * 获取选区最后一行的 X 范围（用于小三角指向最后一行中间）。
     */
    fun getLastLineXRange(page: TextPage, viewWidth: Float): Pair<Float, Float>? {
        if (selectStart < 0 || selectEnd <= 0) return null

        val bodyLeft = page.layout.body.left

        val startLine = page.lines.firstOrNull { line ->
            selectStart >= line.startCharOffset && selectStart < line.endCharOffset
        }
        val endCharIndex = selectEnd - 1
        val endLine = page.lines.firstOrNull { line ->
            endCharIndex >= line.startCharOffset && endCharIndex < line.endCharOffset
        }

        if (startLine == null || endLine == null) return null

        val endX = bodyLeft + endLine.startXOffset + getCharXOffset(endLine, selectEnd)

        val isSameLine = startLine === endLine ||
            (startLine.startCharOffset == endLine.startCharOffset && startLine.endCharOffset == endLine.endCharOffset)

        return if (isSameLine) {
            val startX = bodyLeft + startLine.startXOffset + getCharXOffset(startLine, selectStart)
            Pair(startX, endX)
        } else {
            val lastLineLeftX = bodyLeft + endLine.startXOffset
            Pair(lastLineLeftX, endX)
        }
    }

    fun isPointInSelection(x: Float, y: Float): Boolean {
        val range = selectedRange ?: return false
        val padding = HANDLE_TOUCH_RADIUS
        val minY = minOf(anchorAY, anchorBY) - padding
        val maxY = maxOf(anchorAY, anchorBY) + padding
        if (y !in minY..maxY) return false

        val xPadding = HANDLE_TOUCH_RADIUS * 2
        val minX = minOf(anchorAX, anchorBX) - xPadding
        val maxX = maxOf(anchorAX, anchorBX) + xPadding
        return x in minX..maxX
    }

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

    fun charToPixel(charOffset: Int, page: TextPage): PointF? {
        val line = page.lines.firstOrNull { line ->
            charOffset >= line.startCharOffset && charOffset < line.endCharOffset
        } ?: return null

        val bodyLeft = page.layout.body.left
        val x = bodyLeft + line.startXOffset + getCharXOffset(line, charOffset)
        val y = line.bottom
        return PointF(x, y)
    }

    fun pixelToChar(
        x: Float,
        y: Float,
        page: TextPage,
        content: CharSequence,
        paint: Paint?,
    ): Int? {
        val line = page.lines.firstOrNullIndexed { _, line ->
            y >= line.top && y <= line.bottom
        } ?: return null
        return findCharIndexInLine(x, line, page, content, paint)
    }

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
        return 0f
    }

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
            return lineEnd - 1
        }

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

    private fun findWordBoundary(content: CharSequence, charIndex: Int): Pair<Int, Int> {
        if (charIndex < 0 || charIndex >= content.length) return Pair(charIndex, charIndex)

        val ch = content[charIndex]
        if (ch.isWhitespace() || isPunctuation(ch)) {
            return Pair(charIndex, charIndex)
        }

        if (isCJK(ch)) {
            return findCJKWordBoundary(content, charIndex)
        }

        var start = charIndex
        var end = charIndex + 1
        while (start > 0 && isWordChar(content[start - 1])) start--
        while (end < content.length && isWordChar(content[end])) end++
        return Pair(start, end)
    }

    private fun findCJKWordBoundary(content: CharSequence, charIndex: Int): Pair<Int, Int> {
        val maxWordLen = 4
        val start = charIndex

        var wordStart = start
        while (wordStart > 0 && isCJK(content[wordStart - 1]) && (start - wordStart) < maxWordLen - 1) {
            wordStart--
        }

        var wordEnd = start + 1
        while (wordEnd < content.length && isCJK(content[wordEnd]) && (wordEnd - wordStart) < maxWordLen) {
            wordEnd++
        }

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
        const val HANDLE_TOUCH_RADIUS = SelectionVisualStyle.HANDLE_TOUCH_RADIUS
        const val HANDLE_DOT_RADIUS = SelectionVisualStyle.HANDLE_DOT_RADIUS
    }
}
