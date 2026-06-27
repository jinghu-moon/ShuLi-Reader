package com.shuli.reader.core.reader.engine.selection

import com.shuli.reader.core.reader.model.BoxBounds
import com.shuli.reader.core.reader.model.PageLayout
import com.shuli.reader.core.reader.model.TextLine
import com.shuli.reader.core.reader.model.TextPage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CanvasTextSelection 单元测试：验证中立锚点模型、防抖和位置计算。
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CanvasTextSelectionTest {

    private lateinit var selection: CanvasTextSelection
    private val content = "Hello World Test"

    private fun singleLinePage(lineStart: Int = 0, lineEnd: Int = content.length): TextPage {
        val charWidths = FloatArray(lineEnd - lineStart) { 10f }
        val line = TextLine(
            startCharOffset = lineStart,
            endCharOffset = lineEnd,
            baseline = 30f,
            top = 10f,
            bottom = 40f,
            isParagraphEnd = true,
            startXOffset = 0f,
            measuredWidth = (lineEnd - lineStart) * 10f,
            charWidths = charWidths,
        )
        return TextPage(
            startCharOffset = lineStart,
            endCharOffset = lineEnd,
            chapterIndex = 0,
            pageIndex = 0,
            lines = listOf(line),
            layout = PageLayout(
                header = null,
                title = null,
                body = BoxBounds(left = 20f, top = 0f, right = 320f, bottom = 50f),
                footer = null,
                pageWidth = 340f,
                pageHeight = 50f,
            ),
        )
    }

    @Before
    fun setUp() {
        selection = CanvasTextSelection()
    }

    @Test
    fun selectWordAt_setsInitialState() {
        val page = singleLinePage()
        val range = selection.selectWordAt(25f, 25f, page, content, 340f)

        assertNotNull(range)
        assertEquals("Hello", range!!.selectedText)
        assertEquals(0, selection.selectStart)
        assertEquals(5, selection.selectEnd)
        assertTrue(selection.anchorAIsStart)
        assertNull(selection.activeAnchor)
    }

    @Test
    fun moveHandle_normalDrag_extendsSelection() {
        val page = singleLinePage()
        selection.selectWordAt(25f, 25f, page, content, 340f) // 选区 0..5 (A=0, B=5)

        // 初始化把手坐标
        selection.getHandleRects(page, 340f)

        // 拖动 B 锚点
        selection.startHandleDrag(CanvasTextSelection.AnchorId.B)
        val result = selection.moveHandle(11, content)

        assertNotNull(result)
        assertEquals(0, selection.selectStart)
        assertEquals(11, selection.selectEnd)
        assertEquals("Hello World", result!!.range.selectedText)
        assertTrue("正常拖动 A 依然是 START", selection.anchorAIsStart)
    }

    @Test
    fun moveHandle_crossover_swapsVisualRole() {
        val page = singleLinePage()
        selection.selectWordAt(90f, 25f, page, content, 340f) // 选中 "World" (6..11, A=6, B=11)

        selection.getHandleRects(page, 340f)

        // 拖动 A 锚点越过 B (从 6 拖到 14)
        selection.startHandleDrag(CanvasTextSelection.AnchorId.A)
        val result = selection.moveHandle(14, content)

        assertNotNull(result)
        // 穿越后 A(14) > B(11)，因此 A 应该是 END，AIsStart 为 false
        assertFalse("穿越后 anchorA 应该是 END", selection.anchorAIsStart)
        assertEquals(11, selection.selectStart)
        assertEquals(14, selection.selectEnd)
    }

    @Test
    fun moveHandle_hysteresis_maintainsVisualRoleOnCollapse() {
        val page = singleLinePage()
        selection.selectWordAt(25f, 25f, page, content, 340f) // 0..5 (A=0, B=5)

        selection.getHandleRects(page, 340f)

        // 记录拖动前的状态
        assertTrue(selection.anchorAIsStart)

        // 拖动 B 锚点到 A 的位置 (5 拖到 0)
        selection.startHandleDrag(CanvasTextSelection.AnchorId.B)
        val result = selection.moveHandle(0, content)

        assertNotNull(result)
        assertEquals(0, selection.selectStart)
        assertEquals(0, selection.selectEnd)
        
        // 由于 A==B，不应该翻转视觉角色（防抖机制生效）
        assertTrue("两锚点重合时应该保持原有的角色", selection.anchorAIsStart)
    }

    @Test
    fun moveHandle_doubleCrossover_restoresNormal() {
        val page = singleLinePage()
        selection.selectWordAt(25f, 25f, page, content, 340f) // 0..5 (A=0, B=5)

        selection.getHandleRects(page, 340f)

        // 第 1 次穿越：A 从 0 拖到 10
        selection.startHandleDrag(CanvasTextSelection.AnchorId.A)
        selection.moveHandle(10, content)
        assertFalse(selection.anchorAIsStart) // A(10) > B(5)

        // 第 2 次穿越：A 拖回到 2
        selection.moveHandle(2, content)
        assertTrue("再次穿越回去后，A(2) < B(5)，应恢复为 START", selection.anchorAIsStart)
    }

    @Test
    fun moveHandle_overMaxLength_returnsCurrentRangeAndFreezesHandle() {
        val longContent = "a".repeat(CanvasTextSelection.MAX_SELECTION_LENGTH + 100)
        val page = singleLinePage(lineStart = 0, lineEnd = content.length)
        selection.selectWordAt(25f, 25f, page, content, 340f) // 0..5
        selection.startHandleDrag(CanvasTextSelection.AnchorId.B)

        val result = selection.moveHandle(CanvasTextSelection.MAX_SELECTION_LENGTH + 50, longContent)

        assertNotNull(result)
        assertTrue(result!!.limitReached)
        assertEquals("超限时应保持原选区起点", 0, result.range.startPos)
        assertEquals("超限时应保持原选区终点", 5, result.range.endPos)
        assertEquals(0, selection.selectStart)
        assertEquals(5, selection.selectEnd)
    }

    @Test
    fun moveHandle_fastDrag_afterCrossoverUsesVisualRole() {
        val page = singleLinePage()
        selection.selectWordAt(25f, 25f, page, content, 340f) // 0..5
        selection.startHandleDrag(CanvasTextSelection.AnchorId.A)

        selection.moveHandle(10, content) // A crosses B, A becomes visual END.
        assertFalse(selection.anchorAIsStart)

        val result = selection.moveHandle(7, content, page, isFastDrag = true)

        assertNotNull(result)
        assertTrue(result!!.snapped)
        assertEquals("A 是视觉结束把手时，快速拖动应吸附到词尾", 11, selection.selectEnd)
    }

    @Test
    fun pixelToChar_rightOfLine_returnsLineEnd() {
        val page = singleLinePage()

        val charIndex = selection.pixelToChar(500f, 25f, page, content, null)

        assertEquals(content.length, charIndex)
    }

    @Test
    fun getHandleRects_providesCorrectVisualRoles() {
        val page = singleLinePage()
        selection.selectWordAt(25f, 25f, page, content, 340f) // 0..5 (A=0, B=5)

        val infosBefore = selection.getHandleRects(page, 340f)
        assertNotNull(infosBefore)
        
        val aBefore = infosBefore!!.find { it.anchorId == CanvasTextSelection.AnchorId.A }!!
        val bBefore = infosBefore.find { it.anchorId == CanvasTextSelection.AnchorId.B }!!
        
        assertTrue("未穿越前 A 应该是 START", aBefore.isStart)
        assertFalse("未穿越前 B 应该是 END", bBefore.isStart)

        // 穿越：A 从 0 拖到 10
        selection.startHandleDrag(CanvasTextSelection.AnchorId.A)
        selection.moveHandle(10, content)

        val infosAfter = selection.getHandleRects(page, 340f)
        assertNotNull(infosAfter)

        val aAfter = infosAfter!!.find { it.anchorId == CanvasTextSelection.AnchorId.A }!!
        val bAfter = infosAfter.find { it.anchorId == CanvasTextSelection.AnchorId.B }!!

        assertFalse("穿越后 A 应该是 END", aAfter.isStart)
        assertTrue("穿越后 B 应该是 START", bAfter.isStart)
    }

    @Test
    fun clearSelection_resetsAll() {
        val page = singleLinePage()
        selection.selectWordAt(25f, 25f, page, content, 340f)
        selection.clearSelection()

        assertNull(selection.selectedRange)
        assertEquals(-1, selection.selectStart)
        assertEquals(-1, selection.selectEnd)
        assertFalse(selection.isSelecting)
        assertNull(selection.activeAnchor)
        assertTrue(selection.anchorAIsStart)
    }
}
