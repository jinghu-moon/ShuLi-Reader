package com.shuli.reader.core.reader.engine

import com.shuli.reader.core.reader.text.TextMeasurer
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.toLayoutConfig
import com.shuli.reader.core.reader.model.BoxBounds
import com.shuli.reader.core.reader.model.PageLayout
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextColumn
import com.shuli.reader.core.reader.model.TextLine
import com.shuli.reader.core.reader.model.TextPage

/**
 * 竖排分页策略。
 *
 * 文本从上到下排列，列从右到左排列。
 * 每列相当于横排模式的一行，列宽等于行高。
 */
class VerticalPaginationStrategy(
    private val measurer: TextMeasurer,
) : PaginationStrategy {

    override fun paginate(
        chapterIndex: Int,
        title: String,
        content: String,
        prefs: ReaderPreferences,
        pageSize: PageSize,
        density: Float,
        showHeader: Boolean,
        showFooter: Boolean,
    ): TextChapter {
        val config = prefs.toLayoutConfig(pageSize, density)
        return paginateVertical(chapterIndex, title, content, config, pageSize)
    }

    private fun paginateVertical(
        chapterIndex: Int,
        title: String,
        content: String,
        config: ReaderLayoutConfig,
        pageSize: PageSize,
    ): TextChapter {
        val pages = mutableListOf<TextPage>()
        var charOffset = 0
        var pageIndex = 0

        while (charOffset < content.length) {
            val page = paginatePage(
                chapterIndex = chapterIndex,
                pageIndex = pageIndex,
                content = content,
                startOffset = charOffset,
                config = config,
                pageSize = pageSize,
                isTitlePage = pageIndex == 0,
                title = title,
            )
            pages.add(page)
            charOffset = page.endCharOffset
            pageIndex++
        }

        return TextChapter(
            chapterIndex = chapterIndex,
            title = title,
            content = content,
            pages = pages,
        )
    }

    /**
     * 分页单页：列优先，从右到左。
     */
    private fun paginatePage(
        chapterIndex: Int,
        pageIndex: Int,
        content: String,
        startOffset: Int,
        config: ReaderLayoutConfig,
        pageSize: PageSize,
        isTitlePage: Boolean,
        title: String,
    ): TextPage {
        val density = config.density
        val marginTop = config.bodyInsets.top.toInt()
        val marginBottom = config.bodyInsets.bottom.toInt()
        val marginLeft = config.bodyInsets.left.toInt()
        val marginRight = config.bodyInsets.right.toInt()

        // 可用区域
        val availableWidth = pageSize.width - marginLeft - marginRight
        val availableHeight = pageSize.height - marginTop - marginBottom

        // 列宽 = 行高 * textSize（竖排时列宽即横排的行高）
        val columnWidth = (config.textSize * config.lineHeight).toInt()
        val maxColumns = if (columnWidth > 0) (availableWidth / columnWidth).toInt() else 1

        val columns = mutableListOf<TextColumn>()
        val allLines = mutableListOf<TextLine>()
        var currentOffset = startOffset
        var currentColumn = 0
        var lineIndex = 0

        while (currentColumn < maxColumns && currentOffset < content.length) {
            val columnStartLine = lineIndex
            val columnStartOffset = currentOffset

            // 计算列的 X 坐标（从右到左）
            val columnX = pageSize.width - marginRight - (currentColumn + 1) * columnWidth

            // 在当前列中逐行（竖排中的"行"是单个字符或少量字符）填充
            var currentY = marginTop.toFloat()

            while (currentY + config.textSize <= pageSize.height - marginBottom && currentOffset < content.length) {
                // 竖排模式：每行通常是一个字符
                val char = content[currentOffset]
                val charWidth = measurer.measureCharWidth(char, config.textSize)

                val line = TextLine(
                    startCharOffset = currentOffset,
                    endCharOffset = currentOffset + 1,
                    baseline = currentY + config.textSize,
                    top = currentY,
                    bottom = currentY + config.textSize,
                    isParagraphEnd = char == '\n',
                    startXOffset = 0f,
                    measuredWidth = charWidth,
                )
                allLines.add(line)
                currentOffset++
                lineIndex++
                currentY += config.textSize
            }

            if (currentOffset > columnStartOffset) {
                columns.add(TextColumn(
                    startCharOffset = columnStartOffset,
                    endCharOffset = currentOffset,
                    startLine = columnStartLine,
                    endLine = lineIndex,
                ))
            }

            currentColumn++

            // 遇到段落结束或列满时继续下一列
            if (currentOffset < content.length && content[currentOffset] == '\n') {
                currentOffset++ // 跳过换行符
            }
        }

        val pw = pageSize.width.toFloat()
        val ph = pageSize.height.toFloat()
        return TextPage(
            startCharOffset = startOffset,
            endCharOffset = currentOffset,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            lines = allLines,
            layout = PageLayout(
                header = if (config.headerInsets.top > 0f) BoxBounds(0f, 0f, pw, config.headerInsets.top) else null,
                title = null,
                body = BoxBounds(marginLeft.toFloat(), marginTop.toFloat(), (pageSize.width - marginRight).toFloat(), (pageSize.height - marginBottom).toFloat()),
                footer = if (config.footerInsets.bottom > 0f) BoxBounds(0f, ph - config.footerInsets.bottom, pw, ph) else null,
                pageWidth = pw,
                pageHeight = ph,
            ),
            columns = columns,
            density = density,
            chapterContentLength = content.length,
            chapterTitle = if (isTitlePage) title else "",
        )
    }
}
