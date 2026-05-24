package com.shuli.reader.core.reader

import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextLine
import com.shuli.reader.core.reader.model.TextPage

/**
 * 分页器，负责将文本内容分页
 */
class Paginator(
    private val textMeasurer: TextMeasurer,
) {
    private companion object {
        private val FORBIDDEN_LINE_START_CHARS = setOf(
            '\u3001',
            '\u3002',
            '\uff0c',
            '\uff1b',
            '\uff1a',
            '\uff1f',
            '\uff01',
            '\uff09',
            '\u3011',
            '\u300b',
            '\u300d',
            '\u300f',
            '\u2026',
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
     * 分页单个页面
     */
    private fun paginatePage(
        chapterIndex: Int,
        pageIndex: Int,
        content: String,
        startOffset: Int,
        config: ReaderLayoutConfig,
    ): TextPage {
        val lines = mutableListOf<TextLine>()
        val columns = mutableListOf<com.shuli.reader.core.reader.model.TextColumn>()

        val lineHeight = textMeasurer.measureTextHeight(config.textSize, config.lineHeight)
        val availableWidth = config.pageSize.width - config.marginHorizontal * 2
        val density = config.density
        val headerHeight = 24f * density
        val footerHeight = 24f * density
        val maxAvailableY = config.pageSize.height - config.marginVertical - footerHeight

        var currentY = config.marginVertical + headerHeight
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
            )

            val startY = config.marginVertical + headerHeight
            val line = TextLine(
                text = lineResult.text,
                baseline = startY + lineHeight * 0.8f,
                top = startY,
                bottom = startY + lineHeight,
                isParagraphEnd = lineResult.isParagraphEnd,
                startCharOffset = currentOffset,
                endCharOffset = currentOffset + skippedSpaces + lineResult.consumedChars,
                startXOffset = if (isParagraphStart) indentWidth else 0f,
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

        // 计算能容纳的字符数
        var currentWidth = 0f
        var charCount = 0

        for (i in 0 until lineEnd) {
            val charWidth = textMeasurer.measureCharWidth(remaining[i], textSize)
            if (currentWidth + charWidth > availableWidth) {
                break
            }
            currentWidth += charWidth
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

        if (charCount < lineEnd && remaining[charCount] in FORBIDDEN_LINE_START_CHARS) {
            charCount++
        }

        val consumesLineBreak = newlineIndex >= 0 && charCount == lineEnd
        val text = remaining.substring(0, charCount)
        val consumedChars = charCount + if (consumesLineBreak) 1 else 0
        val isParagraphEnd = consumesLineBreak

        return LineResult(text, isParagraphEnd, consumedChars)
    }

    /**
     * 行计算结果
     */
    private data class LineResult(
        val text: String,
        val isParagraphEnd: Boolean,
        val consumedChars: Int,
    )
}
