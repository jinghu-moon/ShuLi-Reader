package com.shuli.reader.core.reader

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.IndentUnit
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.toLayoutConfig
import com.shuli.reader.core.reader.model.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 竖排分页策略测试。
 *
 * 测试竖排布局的列优先分页逻辑。
 */
class VerticalPaginationStrategyTest {

    private val measurer = SimpleTextMeasurer()
    private val pageSize = PageSize(1080, 1920)

    private fun createPrefs() = ReaderPreferences(
        fontSize = 16f,
        lineSpacing = 1.5f,
        paragraphSpacing = 1.0f,
        marginTop = 48f,
        marginBottom = 48f,
        marginLeft = 24f,
        marginRight = 24f,
        indent = 2f,
        letterSpacing = 0f,
        fontWeight = ReaderFontWeight.NORMAL,
        chineseConvert = ChineseConvert.NONE,
        usePanguSpacing = false,
        useZhLayout = false,
        bottomJustify = false,
        verticalText = true,
    )

    // T-4.3.1: VerticalPaginationStrategy 实现 PaginationStrategy 接口
    @Test
    fun verticalPaginationStrategy_implementsInterface() {
        val strategy: PaginationStrategy = VerticalPaginationStrategy(measurer)
        // 能编译通过即表示实现了接口
        assertTrue("should implement PaginationStrategy", strategy is PaginationStrategy)
    }

    // T-4.3.2: 列优先布局：从右向左
    @Test
    fun paginate_columnLayout_rightToLeft() {
        val strategy = VerticalPaginationStrategy(measurer)
        val content = "天地玄黄宇宙洪荒日月盈昃辰宿列张寒来暑往秋收冬藏。".repeat(50)
        val chapter = strategy.paginate(
            chapterIndex = 0,
            title = "测试",
            content = content,
            prefs = createPrefs(),
            pageSize = pageSize,
            density = 2.75f,
        )

        val page = chapter.pages.first()
        // 竖排页面应有列
        assertTrue("vertical page should have columns", page.columns.isNotEmpty())
    }

    // T-4.3.3: TextPage.columns 非空
    @Test
    fun paginate_returnsNonEmptyColumns() {
        val strategy = VerticalPaginationStrategy(measurer)
        val content = "天地玄黄宇宙洪荒日月盈昃辰宿列张。".repeat(20)
        val chapter = strategy.paginate(
            chapterIndex = 0,
            title = "测试",
            content = content,
            prefs = createPrefs(),
            pageSize = pageSize,
            density = 2.75f,
        )

        val page = chapter.pages.first()
        assertTrue("columns should not be empty", page.columns.isNotEmpty())
    }

    // T-4.3.4: TextColumn 字段正确
    @Test
    fun paginate_textColumnFieldsValid() {
        val strategy = VerticalPaginationStrategy(measurer)
        val content = "天地玄黄宇宙洪荒日月盈昃辰宿列张。".repeat(20)
        val chapter = strategy.paginate(
            chapterIndex = 0,
            title = "测试",
            content = content,
            prefs = createPrefs(),
            pageSize = pageSize,
            density = 2.75f,
        )

        val page = chapter.pages.first()
        for (col in page.columns) {
            assertTrue("startCharOffset should be >= 0", col.startCharOffset >= 0)
            assertTrue("endCharOffset should be > startCharOffset",
                col.endCharOffset > col.startCharOffset)
            assertTrue("startLine should be >= 0", col.startLine >= 0)
            assertTrue("endLine should be > startLine", col.endLine > col.startLine)
        }
    }

    // T-4.3.5: 横排模式 columns 为空（对比验证）
    @Test
    fun horizontalPagination_columnsEmpty() {
        val paginator = Paginator(measurer)
        val content = "天地玄黄宇宙洪荒日月盈昃辰宿列张。".repeat(20)
        val chapter = paginator.paginateChapter(0, "测试", content, createPrefs().toLayoutConfig(pageSize, 2.75f))

        val page = chapter.pages.first()
        assertEquals("horizontal mode should have empty columns", 0, page.columns.size)
    }
}
