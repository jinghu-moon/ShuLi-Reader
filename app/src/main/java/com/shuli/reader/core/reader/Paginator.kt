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
        /** 禁止出现在行首的标点（右引号、右括号、句末标点等） */
        private val FORBIDDEN_LINE_START_CHARS = setOf(
            '、',  // 、
            '。',  // 。
            '，',  // ，
            '；',  // ；
            '：',  // ：
            '？',  // ？
            '！',  // ！
            '）',  // ）
            '】',  // 〕
            '》',  // 》
            '」',  // 」
            '』',  // 』
            '…',  // …
            '．',  // ．
            '”',  // "
            '’',  // '
            ')', '>', ']', '}',
            ',', '.', '?', '!', ':', ';',
        )

        /** 禁止出现在行尾的标点（左引号、左括号等） */
        private val FORBIDDEN_LINE_END_CHARS = setOf(
            '“',  // "
            '‘',  // '
            '（',  // （
            '《',  // 《
            '【',  // 【
            '「',  // 「
            '『',  // 『
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
        val charWidths = textMeasurer.measureTextWidths(content, config.textSize)
        val pages = mutableListOf<TextPage>()
        var currentOffset = 0
        var pageIndex = 0

        while (currentOffset < content.length) {
            val page = paginatePage(
                chapterIndex = chapterIndex,
                pageIndex = pageIndex,
                content = content,
                startOffset = currentOffset,
                charWidths = charWidths,
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
        val charWidths = textMeasurer.measureTextWidths(content, config.textSize)
        var currentOffset = 0
        var pageIndex = 0

        while (currentOffset < content.length) {
            val page = paginatePage(
                chapterIndex = chapter.chapterIndex,
                pageIndex = pageIndex,
                content = content,
                startOffset = currentOffset,
                charWidths = charWidths,
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
        charWidths: FloatArray,
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
                while (tempOffset < content.length && (content[tempOffset] == ' ' || content[tempOffset] == '\t' || content[tempOffset] == '　' || content[tempOffset] == '\r')) {
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
                charWidths = charWidths,
                availableWidth = if (isParagraphStart) availableWidth - indentWidth else availableWidth,
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
                while (tempOffset < content.length && (content[tempOffset] == ' ' || content[tempOffset] == '\t' || content[tempOffset] == '　' || content[tempOffset] == '\r')) {
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
                charWidths = charWidths,
                availableWidth = if (isParagraphStart) availableWidth - indentWidth else availableWidth,
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
     * 计算一行能容纳的文本。
     *
     * charWidths 由调用方预计算（measureTextWidths 一次性批量测量），
     * 此方法只做索引查找 + 分行决策，不调用任何测量 API。
     */
    private fun calculateLine(
        content: String,
        startOffset: Int,
        charWidths: FloatArray,
        availableWidth: Float,
        letterSpacingPx: Float = 0f,
        useZhLayout: Boolean = false,
    ): LineResult {
        if (startOffset >= content.length) {
            return LineResult("", false, 0)
        }

        // 查找换行符（基于原始 content 索引，避免 substring 分配）
        val newlineAbsIndex = content.indexOf('\n', startOffset)
        val lineEnd = if (newlineAbsIndex >= 0) newlineAbsIndex - startOffset else content.length - startOffset

        if (lineEnd == 0 && newlineAbsIndex == startOffset) {
            return LineResult("", true, 1)
        }

        // 计算能容纳的字符数（含字距），纯查表
        var currentWidth = 0f
        var charCount = 0

        for (i in 0 until lineEnd) {
            val width = charWidths[startOffset + i]
            val spacing = if (charCount > 0) letterSpacingPx else 0f
            if (currentWidth + width + spacing > availableWidth) {
                break
            }
            currentWidth += width + spacing
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
            while (charCount > 1 && content[startOffset + charCount - 1] in FORBIDDEN_LINE_END_CHARS) {
                charCount--
            }
            if (charCount < lineEnd && content[startOffset + charCount] in FORBIDDEN_LINE_START_CHARS) {
                charCount++
            }
        } else {
            // 默认行为：仅处理行首禁尾标点
            if (charCount < lineEnd && content[startOffset + charCount] in FORBIDDEN_LINE_START_CHARS) {
                charCount++
            }
        }

        val consumesLineBreak = newlineAbsIndex >= 0 && charCount == lineEnd
        val text = content.substring(startOffset, startOffset + charCount)
        val consumedChars = charCount + if (consumesLineBreak) 1 else 0
        val isParagraphEnd = consumesLineBreak

        // 构建 CharColumn 列表（从预计算数组取宽度）
        val charColumns = ArrayList<CharColumn>(charCount)
        for (i in 0 until charCount) {
            charColumns.add(CharColumn(content[startOffset + i].toString(), charWidths[startOffset + i]))
        }
        val measuredWidth = if (charCount > 0) {
            var w = 0f
            for (i in 0 until charCount) w += charWidths[startOffset + i]
            w + letterSpacingPx * (charCount - 1).coerceAtLeast(0)
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
