package com.shuli.reader.core.reader

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.toLayoutConfig
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextLine
import com.shuli.reader.core.reader.model.TextPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 分页器，负责将文本内容分页。
 *
 * 支持策略模式：通过 [strategy] 可切换横排/竖排分页算法。
 * 未设置 strategy 时使用内置横排逻辑（向后兼容）。
 */
class Paginator(
    var textMeasurer: TextMeasurer,
    var strategy: PaginationStrategy? = null,
) {
    /**
     * 策略模式入口：从 ReaderPreferences 解析参数并委托给 strategy。
     * 如无 strategy 则构建 ReaderLayoutConfig 后走内置 paginateChapter。
     */
    fun paginateChapter(
        chapterIndex: Int,
        title: String,
        content: String,
        prefs: ReaderPreferences,
        pageSize: PageSize,
        density: Float,
        showHeader: Boolean = true,
        showFooter: Boolean = true,
    ): TextChapter {
        val s = strategy
        if (s != null) {
            return s.paginate(chapterIndex, title, content, prefs, pageSize, density, showHeader, showFooter)
        }
        val config = prefs.toLayoutConfig(pageSize, density)
        return paginateChapter(chapterIndex, title, content, config, showHeader, showFooter)
    }

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
        val widthWindow = WidthWindow(content, config.textSize, textMeasurer)
        val pages = mutableListOf<TextPage>()
        var currentOffset = 0
        var pageIndex = 0

        while (currentOffset < content.length) {
            val page = paginatePage(
                chapterIndex = chapterIndex,
                pageIndex = pageIndex,
                content = content,
                startOffset = currentOffset,
                widthWindow = widthWindow,
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
        val widthWindow = WidthWindow(content, config.textSize, textMeasurer)
        var currentOffset = 0
        var pageIndex = 0

        while (currentOffset < content.length) {
            val page = paginatePage(
                chapterIndex = chapter.chapterIndex,
                pageIndex = pageIndex,
                content = content,
                startOffset = currentOffset,
                widthWindow = widthWindow,
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
        widthWindow: WidthWindow,
        config: ReaderLayoutConfig,
        showHeader: Boolean = true,
        showFooter: Boolean = true,
        chapterTitle: String = "",
    ): TextPage {
        val lines = mutableListOf<TextLine>()

        val lineHeight = textMeasurer.measureTextHeight(config.textSize, config.lineHeight)
        val availableWidth = config.pageSize.width - config.marginLeft - config.marginRight
        val headerHeight = if (showHeader) 24f * config.density else 0f
        val footerHeight = if (showFooter) 24f * config.density else 0f
        val maxAvailableY = config.pageSize.height - config.marginBottom - footerHeight
        val titleAreaHeight = calcTitleAreaHeight(config, pageIndex, chapterTitle, availableWidth)

        val startY = config.marginTop + headerHeight + titleAreaHeight
        val indentWidth = calcIndent(config, availableWidth)
        var currentY = startY
        var currentOffset = startOffset

        // 按行分页
        while (currentY + lineHeight <= maxAvailableY && currentOffset < content.length) {
            val collapsedOffset = collapseConsecutiveNewlines(content, currentOffset)
            if (collapsedOffset != currentOffset) {
                currentOffset = collapsedOffset
                currentY += config.paragraphSpacing
                if (currentOffset >= content.length) break
                continue
            }

            val isFirstLine = lines.isEmpty()
            val textStart = skipLeadingSpaces(content, currentOffset, isFirstLine)
            val skippedSpaces = textStart - currentOffset
            val isParagraphStart = textStart != currentOffset

            val lineResult = calculateLine(
                content = content,
                startOffset = textStart,
                widthWindow = widthWindow,
                availableWidth = if (isParagraphStart) availableWidth - indentWidth else availableWidth,
                letterSpacingPx = config.letterSpacingPx,
                useZhLayout = config.useZhLayout,
            )

            // 只有当行有实际内容时才创建行对象并增加行高
            if (lineResult.charCount > 0) {
                val line = buildLine(lineResult, textStart, currentY, lineHeight, isParagraphStart, indentWidth)
                lines.add(line)
                currentY += lineHeight
            }

            // 段落结束时增加段间距（无论是否有实际内容）
            if (lineResult.isParagraphEnd) {
                currentY += config.paragraphSpacing
            }
            currentOffset += skippedSpaces + lineResult.consumedChars
        }

        // 至少一行：保证页面不空
        if (lines.isEmpty() && currentOffset < content.length) {
            val textStart = skipLeadingSpaces(content, currentOffset, isFirstLine = true)
            val lineResult = calculateLine(
                content = content,
                startOffset = textStart,
                widthWindow = widthWindow,
                availableWidth = availableWidth - indentWidth,
                letterSpacingPx = config.letterSpacingPx,
                useZhLayout = config.useZhLayout,
            )
            lines.add(buildLine(lineResult, textStart, startY, lineHeight, true, indentWidth))
            currentOffset += (textStart - currentOffset) + lineResult.consumedChars
        }

        // 底部对齐：将剩余垂直空间均匀分配到行间距中
        val finalLines = if (config.bottomJustify && lines.size > 1) {
            val lastBottom = lines.last().bottom
            val remainingSpace = maxAvailableY - lastBottom
            if (remainingSpace > 0f) {
                val extraPerGap = remainingSpace / (lines.size - 1)
                lines.mapIndexed { index, line ->
                    val offset = extraPerGap * index
                    TextLine(
                        startCharOffset = line.startCharOffset,
                        endCharOffset = line.endCharOffset,
                        baseline = line.baseline + offset,
                        top = line.top + offset,
                        bottom = line.bottom + offset,
                        isParagraphEnd = line.isParagraphEnd,
                        startXOffset = line.startXOffset,
                        measuredWidth = line.measuredWidth,
                        charWidths = line.charWidths,
                    )
                }
            } else {
                lines
            }
        } else {
            lines
        }

        return TextPage(
            startCharOffset = startOffset,
            endCharOffset = currentOffset,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            pageSize = config.pageSize,
            marginHorizontal = config.marginHorizontal,
            lines = finalLines,
            density = config.density,
            chapterContentLength = content.length,
            chapterTitle = if (pageIndex == 0) chapterTitle else "",
            topContentY = config.marginTop + headerHeight + titleAreaHeight,
            headerMarginTop = config.headerMarginTop,
            footerMarginBottom = config.footerMarginBottom,
        )
    }

    /** 计算段落首行缩进宽度（config.indent 已由 ReaderTextMeasurerFactory 转为 px） */
    private fun calcIndent(config: ReaderLayoutConfig, availableWidth: Float): Float =
        if (availableWidth > config.indent * 2f) config.indent else 0f

    /** 首页标题区域高度（含上下边距，支持多行自动换行） */
    private fun calcTitleAreaHeight(
        config: ReaderLayoutConfig,
        pageIndex: Int,
        chapterTitle: String,
        availableWidth: Float,
    ): Float {
        val ts = config.titleStyle
        if (pageIndex != 0 || ts.align == TitleAlign.HIDDEN || chapterTitle.isBlank()) return 0f
        val d = config.density
        val titleTextSize = config.textSize + ts.sizeOffsetSp * d
        val titleLineHeight = titleTextSize * 1.3f
        val titleWidth = textMeasurer.measureTextWidth(chapterTitle, titleTextSize) * 1.05f
        val lineCount = if (availableWidth > 0) ((titleWidth / availableWidth).toInt() + 1).coerceAtLeast(1) else 1
        return ts.marginTopDp * d + lineCount * titleLineHeight + ts.marginBottomDp * d
    }

    /** 跳过段落起始处的前导空白字符，返回可见文本起始偏移 */
    private fun skipLeadingSpaces(content: String, offset: Int, isFirstLine: Boolean): Int {
        if (!isFirstLine && (offset <= 0 || content[offset - 1] != '\n')) return offset
        var pos = offset
        while (pos < content.length && content[pos] in " \t　\r") pos++
        return pos
    }

    /** 合并连续换行符为一个段间距，返回跳过后的偏移；若无连续换行则返回原值 */
    private fun collapseConsecutiveNewlines(content: String, offset: Int): Int {
        if (offset <= 0 || offset >= content.length) return offset
        if (content[offset - 1] != '\n' || content[offset] != '\n') return offset
        var pos = offset
        while (pos < content.length && content[pos] == '\n') pos++
        return pos
    }

    /** 根据行计算结果构建 TextLine */
    private fun buildLine(
        result: LineResult,
        textStart: Int,
        y: Float,
        lineHeight: Float,
        isParagraphStart: Boolean,
        indentWidth: Float,
    ): TextLine = TextLine(
        startCharOffset = textStart,
        endCharOffset = textStart + result.charCount,
        baseline = y + lineHeight * 0.8f,
        top = y,
        bottom = y + lineHeight,
        isParagraphEnd = result.isParagraphEnd,
        startXOffset = if (isParagraphStart) indentWidth else 0f,
        measuredWidth = result.measuredWidth,
        charWidths = result.charWidths,
    )

    /**
     * 计算一行能容纳的文本。
     *
     * widthWindow 按需分块缓存字符宽度，此方法只做索引查找 + 分行决策。
     * 首次访问新块时触发同步测量并缓存，后续访问命中缓存。
     *
     * 单遍遍历：同时完成 charCount 判定和 measuredWidth 累计。
     */
    private fun calculateLine(
        content: String,
        startOffset: Int,
        widthWindow: WidthWindow,
        availableWidth: Float,
        letterSpacingPx: Float = 0f,
        useZhLayout: Boolean = false,
    ): LineResult {
        if (startOffset >= content.length) {
            return LineResult(false, 0, 0)
        }

        // 查找换行符（基于原始 content 索引，避免 substring 分配）
        val newlineAbsIndex = content.indexOf('\n', startOffset)
        val lineEnd = if (newlineAbsIndex >= 0) newlineAbsIndex - startOffset else content.length - startOffset

        if (lineEnd == 0 && newlineAbsIndex == startOffset) {
            return LineResult(true, 0, 1)
        }

        // 唯一遍历：判定 charCount + 累计 measuredWidth
        var currentWidth = 0f
        var charCount = 0

        for (i in 0 until lineEnd) {
            val width = widthWindow[startOffset + i]
            val spacing = if (charCount > 0) letterSpacingPx else 0f
            if (currentWidth + width + spacing > availableWidth) break
            currentWidth += width + spacing
            charCount++
        }

        // 如果没有任何字符，至少添加一个
        if (charCount == 0 && lineEnd > 0) {
            charCount = 1
        }

        // 如果仍然没有字符，返回空结果
        if (charCount == 0) {
            return LineResult(false, 0, 0)
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
        val consumedChars = charCount + if (consumesLineBreak) 1 else 0
        val isParagraphEnd = consumesLineBreak

        // measuredWidth 已在循环中累计（currentWidth），但标点调整可能改变 charCount，
        // 需要重新计算精确的 measuredWidth
        val measuredWidth = if (charCount > 0) {
            var w = 0f
            for (i in 0 until charCount) {
                w += widthWindow[startOffset + i]
            }
            w + letterSpacingPx * (charCount - 1).coerceAtLeast(0)
        } else {
            0f
        }

        // charWidths 在 charCount 确定后分配精确大小，避免 lineEnd != charCount 的浪费
        val charWidths = if (charCount > 0 && availableWidth - measuredWidth > 0.5f) {
            FloatArray(charCount) { widthWindow[startOffset + it] }
        } else {
            null
        }

        return LineResult(isParagraphEnd, charCount, consumedChars, charWidths, measuredWidth)
    }

    /**
     * 行计算结果
     */
    private data class LineResult(
        val isParagraphEnd: Boolean,
        /** 可见字符数（不含尾部换行符） */
        val charCount: Int,
        /** 消耗的总字符数（含尾部换行符），用于推进 currentOffset */
        val consumedChars: Int,
        /** 字符宽度数组（不含字距），仅非段落末行且有剩余空间时有值 */
        val charWidths: FloatArray? = null,
        /** 文本总宽度（含基础字距，不含两端对齐拉伸） */
        val measuredWidth: Float = 0f,
    )
}
