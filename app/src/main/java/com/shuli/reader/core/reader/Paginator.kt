package com.shuli.reader.core.reader

import com.shuli.reader.core.reader.model.CharColumn
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextLine
import com.shuli.reader.core.reader.model.TextPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 分页器，负责将文本内容分页
 */
class Paginator(
    private val textMeasurer: TextMeasurer,
) {
    private companion object {
        /** \u7981\u6b62\u51fa\u73b0\u5728\u884c\u9996\u7684\u6807\u70b9\uff08\u53f3\u5f15\u53f7\u3001\u53f3\u62ec\u53f7\u3001\u53e5\u672b\u6807\u70b9\u7b49\uff09 */
        private val FORBIDDEN_LINE_START_CHARS = setOf(
            '\u3001',  // \u3001
            '\u3002',  // \u3002
            '\uff0c',  // \uff0c
            '\uff1b',  // \uff1b
            '\uff1a',  // \uff1a
            '\uff1f',  // \uff1f
            '\uff01',  // \uff01
            '\uff09',  // \uff09
            '\u3011',  // \u3011
            '\u300b',  // \u300b
            '\u300d',  // \u300d
            '\u300f',  // \u300f
            '\u2026',  // \u2026
            '\uff0e',  // \uff0e
            '\u201d',  // "
            '\u2019',  // '
            ')', '>', ']', '}',
            ',', '.', '?', '!', ':', ';',
        )

        /** \u7981\u6b62\u51fa\u73b0\u5728\u884c\u5c3e\u7684\u6807\u70b9\uff08\u5de6\u5f15\u53f7\u3001\u5de6\u62ec\u53f7\u7b49\uff09 */
        private val FORBIDDEN_LINE_END_CHARS = setOf(
            '\u201c',  // "
            '\u2018',  // '
            '\uff08',  // \uff08
            '\u300a',  // \u300a
            '\u3010',  // \u3010
            '\u300c',  // \u300c
            '\u300e',  // \u300e
            '(', '<', '[', '{',
        )
    }

    /**
     * 对章节内容进行分页
     */
    fun paginateChapter(
        chapterIndex: Int,
        title: String,
        content: String,
        config: ReaderLayoutConfig,
        showHeader: Boolean = true,
        showFooter: Boolean = true,
    ): TextChapter {
        val pages = mutableListOf<TextPage>()
        var currentOffset = 0
        var pageIndex = 0

        while (currentOffset < content.length) {
            val page = paginatePage(
                chapterIndex = chapterIndex,
                pageIndex = pageIndex,
                content = content,
                startOffset = currentOffset,
                config = config,
                showHeader = showHeader,
                showFooter = showFooter,
                chapterTitle = title,
            )
            pages.add(page)
            currentOffset = page.endCharOffset
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
     * 流式分页。每生成一页 emit 一次。
     * 调用方应在背景 Dispatcher 里 collect。
     */
    fun paginateStreaming(
        chapter: TextChapter,
        content: String,
        config: ReaderLayoutConfig,
        showHeader: Boolean = true,
        showFooter: Boolean = true,
    ): Flow<TextPage> = flow {
        var currentOffset = 0
        var pageIndex = 0

        while (currentOffset < content.length) {
            val page = paginatePage(
                chapterIndex = chapter.chapterIndex,
                pageIndex = pageIndex,
                content = content,
                startOffset = currentOffset,
                config = config,
                showHeader = showHeader,
                showFooter = showFooter,
                chapterTitle = chapter.title,
            )
            chapter.addPage(page)
            emit(page)
            currentOffset = page.endCharOffset
            pageIndex++
        }
    }

    /**
     * 分页单个页面
     */
    private fun paginatePage(
        chapterIndex: Int,
        pageIndex: Int,
        content: String,
        startOffset: Int,
        config: ReaderLayoutConfig,
        showHeader: Boolean = true,
        showFooter: Boolean = true,
        chapterTitle: String = "",
    ): TextPage {
        val lines = mutableListOf<TextLine>()
        val columns = mutableListOf<com.shuli.reader.core.reader.model.TextColumn>()

        val lineHeight = textMeasurer.measureTextHeight(config.textSize, config.lineHeight)
        val availableWidth = config.pageSize.width - config.marginHorizontal * 2
        val density = config.density
        val headerHeight = if (showHeader) 24f * density else 0f
        val footerHeight = if (showFooter) 24f * density else 0f
        val maxAvailableY = config.pageSize.height - config.marginVertical - footerHeight

        // S1: 首页计算标题区域高度
        val titleStyle = config.titleStyle
        val titleAreaHeight = if (pageIndex == 0 && titleStyle.align != TitleAlign.HIDDEN) {
            val titleTextSize = config.textSize + titleStyle.sizeOffsetSp * density
            titleStyle.marginTopDp * density + titleTextSize + titleStyle.marginBottomDp * density
        } else {
            0f
        }

        var currentY = config.marginVertical + headerHeight + titleAreaHeight
        var currentOffset = startOffset

        // 按行分页
        while (currentY + lineHeight <= maxAvailableY && currentOffset < content.length) {
            val isParagraphStart = currentOffset == 0 || (currentOffset > 0 && content[currentOffset - 1] == '\n')
            var tempOffset = currentOffset
            if (isParagraphStart) {
                while (tempOffset < content.length && (content[tempOffset] == ' ' || content[tempOffset] == '\t' || content[tempOffset] == '\u3000' || content[tempOffset] == '\r')) {
                    tempOffset++
                }
            }
            val skippedSpaces = tempOffset - currentOffset
            val indentWidth = if (availableWidth > config.indent * config.textSize * 2f) {
                config.indent * config.textSize
            } else {
                0f
            }

            val lineResult = calculateLine(
                content = content,
                startOffset = tempOffset,
                availableWidth = if (isParagraphStart) availableWidth - indentWidth else availableWidth,
                textSize = config.textSize,
                letterSpacingPx = config.letterSpacingPx,
                useZhLayout = config.useZhLayout,
            )

            val line = TextLine(
                text = lineResult.text,
                baseline = currentY + lineHeight * 0.8f,
                top = currentY,
                bottom = currentY + lineHeight,
                isParagraphEnd = lineResult.isParagraphEnd,
                startCharOffset = currentOffset,
                endCharOffset = currentOffset + skippedSpaces + lineResult.consumedChars,
                startXOffset = if (isParagraphStart) indentWidth else 0f,
                charColumns = lineResult.charColumns,
                measuredWidth = lineResult.measuredWidth,
            )
            lines.add(line)

            currentY += lineHeight
            if (lineResult.isParagraphEnd) {
                currentY += config.paragraphSpacing
            }
            currentOffset += skippedSpaces + lineResult.consumedChars
        }

        // 如果没有行，至少添加一行
        if (lines.isEmpty() && currentOffset < content.length) {
            val isParagraphStart = currentOffset == 0 || (currentOffset > 0 && content[currentOffset - 1] == '\n')
            var tempOffset = currentOffset
            if (isParagraphStart) {
                while (tempOffset < content.length && (content[tempOffset] == ' ' || content[tempOffset] == '\t' || content[tempOffset] == '\u3000' || content[tempOffset] == '\r')) {
                    tempOffset++
                }
            }
            val skippedSpaces = tempOffset - currentOffset
            val indentWidth = if (availableWidth > config.indent * config.textSize * 2f) {
                config.indent * config.textSize
            } else {
                0f
            }

            val lineResult = calculateLine(
                content = content,
                startOffset = tempOffset,
                availableWidth = if (isParagraphStart) availableWidth - indentWidth else availableWidth,
                textSize = config.textSize,
                letterSpacingPx = config.letterSpacingPx,
                useZhLayout = config.useZhLayout,
            )

            val startY = config.marginVertical + headerHeight + titleAreaHeight
            val line = TextLine(
                text = lineResult.text,
                baseline = startY + lineHeight * 0.8f,
                top = startY,
                bottom = startY + lineHeight,
                isParagraphEnd = lineResult.isParagraphEnd,
                startCharOffset = currentOffset,
                endCharOffset = currentOffset + skippedSpaces + lineResult.consumedChars,
                startXOffset = if (isParagraphStart) indentWidth else 0f,
                charColumns = lineResult.charColumns,
                measuredWidth = lineResult.measuredWidth,
            )
            lines.add(line)
            currentOffset += skippedSpaces + lineResult.consumedChars
        }

        return TextPage(
            startCharOffset = startOffset,
            endCharOffset = currentOffset,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            pageSize = config.pageSize,
            marginHorizontal = config.marginHorizontal,
            lines = lines,
            columns = columns,
            density = config.density,
            chapterContentLength = content.length,
            chapterTitle = if (pageIndex == 0) chapterTitle else "",
            topContentY = config.marginVertical + headerHeight + titleAreaHeight,
        )
    }

    /**
     * 计算一行能容纳的文本
     */
    private fun calculateLine(
        content: String,
        startOffset: Int,
        availableWidth: Float,
        textSize: Float,
        letterSpacingPx: Float = 0f,
        useZhLayout: Boolean = false,
    ): LineResult {
        val remaining = content.substring(startOffset)
        if (remaining.isEmpty()) {
            return LineResult("", false, 0)
        }

        // 查找换行符
        val newlineIndex = remaining.indexOf('\n')
        val lineEnd = if (newlineIndex >= 0) newlineIndex else remaining.length

        if (lineEnd == 0 && newlineIndex == 0) {
            return LineResult("", true, 1)
        }

        // 计算能容纳的字符数（含字距），同时收集字符宽度
        var currentWidth = 0f
        var charCount = 0
        val charWidths = mutableListOf<Float>()

        for (i in 0 until lineEnd) {
            val charWidth = textMeasurer.measureCharWidth(remaining[i], textSize)
            val spacing = if (charCount > 0) letterSpacingPx else 0f
            if (currentWidth + charWidth + spacing > availableWidth) {
                break
            }
            currentWidth += charWidth + spacing
            charWidths.add(charWidth)
            charCount++
        }

        // 如果没有任何字符，至少添加一个
        if (charCount == 0 && lineEnd > 0) {
            charCount = 1
        }

        // 如果仍然没有字符，返回空结果
        if (charCount == 0) {
            return LineResult("", false, 0)
        }

        if (useZhLayout) {
            // 中文分行：回溯避免行尾出现禁头标点，前推避免行首出现禁尾标点
            // 回溯：行尾不能是 FORBIDDEN_LINE_END_CHARS（左引号、左括号等）
            while (charCount > 1 && remaining[charCount - 1] in FORBIDDEN_LINE_END_CHARS) {
                charCount--
                charWidths.removeAt(charWidths.lastIndex)
            }
            // 前推：行首不能是 FORBIDDEN_LINE_START_CHARS（右引号、右括号、句末标点等）
            if (charCount < lineEnd && remaining[charCount] in FORBIDDEN_LINE_START_CHARS) {
                charWidths.add(textMeasurer.measureCharWidth(remaining[charCount], textSize))
                charCount++
            }
        } else {
            // 默认行为：仅处理行首禁尾标点
            if (charCount < lineEnd && remaining[charCount] in FORBIDDEN_LINE_START_CHARS) {
                charWidths.add(textMeasurer.measureCharWidth(remaining[charCount], textSize))
                charCount++
            }
        }

        val consumesLineBreak = newlineIndex >= 0 && charCount == lineEnd
        val text = remaining.substring(0, charCount)
        val consumedChars = charCount + if (consumesLineBreak) 1 else 0
        val isParagraphEnd = consumesLineBreak

        // 构建 CharColumn 列表（charWidths 已随 charCount 调整同步更新，无需重新测量）
        val finalCharWidths = charWidths
        val charColumns = text.mapIndexed { index: Int, char: Char ->
            CharColumn(char.toString(), finalCharWidths[index])
        }
        val measuredWidth = if (finalCharWidths.isNotEmpty()) {
            finalCharWidths.sum() + letterSpacingPx * (finalCharWidths.size - 1).coerceAtLeast(0)
        } else {
            0f
        }

        return LineResult(text, isParagraphEnd, consumedChars, charColumns, measuredWidth)
    }

    /**
     * 行计算结果
     */
    private data class LineResult(
        val text: String,
        val isParagraphEnd: Boolean,
        val consumedChars: Int,
        /** 字符级宽度信息，用于两端对齐绘制 */
        val charColumns: List<com.shuli.reader.core.reader.model.CharColumn> = emptyList(),
        /** 文本总宽度（缓存） */
        val measuredWidth: Float = 0f,
    )
}
