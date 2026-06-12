package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.toLayoutConfig
import com.shuli.reader.core.reader.Paginator
import com.shuli.reader.core.reader.SimpleTextMeasurer
import com.shuli.reader.core.reader.VerticalPaginationStrategy
import com.shuli.reader.core.reader.model.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 竖排渲染数据模型测试。
 *
 * 验证竖排分页产生的列数据结构正确，支持渲染层使用。
 */
class VerticalRenderTest {

    private val measurer = SimpleTextMeasurer()
    private val pageSize = PageSize(1080, 1920)

    private fun createVerticalPrefs() = ReaderPreferences(
        fontSize = 16f,
        lineSpacing = 1.5f,
        paragraphSpacing = 1.0f,
        marginTop = 48f,
        marginBottom = 48f,
        marginLeft = 24f,
        marginRight = 24f,
        indent = 2f,
        letterSpacing = 0f,
        wordSpacing = 0f,
        fontWeight = ReaderFontWeight.NORMAL,
        chineseConvert = ChineseConvert.NONE,
        usePanguSpacing = false,
        useZhLayout = false,
        bottomJustify = false,
        verticalText = true,
    )

    // T-4.3.6: 竖排分页产生列数据
    @Test
    fun verticalPagination_producesColumns() {
        val strategy = VerticalPaginationStrategy(measurer)
        val content = "天地玄黄宇宙洪荒日月盈昃辰宿列张寒来暑往秋收冬藏。".repeat(50)
        val chapter = strategy.paginate(
            chapterIndex = 0,
            title = "测试",
            content = content,
            prefs = createVerticalPrefs(),
            pageSize = pageSize,
            density = 2.75f,
        )

        val page = chapter.pages.first()
        // 竖排页面应有列
        assertTrue("vertical page should have columns", page.columns.isNotEmpty())
        // 每列应有行
        for (col in page.columns) {
            assertTrue("column should have lines", col.endLine > col.startLine)
        }
    }

    // T-4.3.7: CJK 文本竖排布局正确
    @Test
    fun verticalPagination_cjkText_correctLayout() {
        val strategy = VerticalPaginationStrategy(measurer)
        val content = "你好世界，这是一个测试。"
        val chapter = strategy.paginate(
            chapterIndex = 0,
            title = "测试",
            content = content,
            prefs = createVerticalPrefs(),
            pageSize = pageSize,
            density = 2.75f,
        )

        val page = chapter.pages.first()
        // 每个字符应对应一行（竖排中的一行 = 一个字符）
        val totalChars = page.columns.sumOf { it.endCharOffset - it.startCharOffset }
        assertEquals("each char should be a line", content.length, totalChars)
    }

    // T-4.3.8: 列从右到左排列
    @Test
    fun verticalPagination_columnsRightToLeft() {
        val strategy = VerticalPaginationStrategy(measurer)
        val content = "天地玄黄宇宙洪荒日月盈昃辰宿列张寒来暑往秋收冬藏。".repeat(50)
        val chapter = strategy.paginate(
            chapterIndex = 0,
            title = "测试",
            content = content,
            prefs = createVerticalPrefs(),
            pageSize = pageSize,
            density = 2.75f,
        )

        val page = chapter.pages.first()
        // 列的 startCharOffset 应该递增（从右到左对应文本的从前往后）
        for (i in 1 until page.columns.size) {
            assertTrue(
                "columns should progress through text",
                page.columns[i].startCharOffset >= page.columns[i - 1].startCharOffset
            )
        }
    }

    // 横排模式不产生列（对比验证）
    @Test
    fun horizontalPagination_noColumns() {
        val paginator = Paginator(measurer)
        val content = "天地玄黄宇宙洪荒日月盈昃辰宿列张。".repeat(20)
        val chapter = paginator.paginateChapter(
            0, "测试", content,
            createVerticalPrefs().toLayoutConfig(pageSize, 2.75f)
        )

        val page = chapter.pages.first()
        assertEquals("horizontal mode should have no columns", 0, page.columns.size)
    }
}
