package com.shuli.reader.core.reader.engine

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.shuli.reader.core.reader.model.BoxSpec
import com.shuli.reader.core.reader.model.PageLayout
// PageLayoutCalculator is in the same package (engine)
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextLine
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.core.reader.model.TitleAlign
import com.shuli.reader.core.reader.text.TextMeasurer
import com.shuli.reader.core.reader.text.WidthWindow
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.toLayoutConfig
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

    companion object {
        /** 页眉内容高度常量（dp），避免魔法数字散落 */
        internal const val HEADER_CONTENT_HEIGHT_DP = 24f
        /** 页脚内容高度常量（dp） */
        internal const val FOOTER_CONTENT_HEIGHT_DP = 24f
        /** 浮点精度容差，防止 maxY == currentY + lineHeight 时因精度丢失跳过最后一行 */
        private const val EPSILON = 0.5f

        /** 禁止出现在行首的标点（右引号、右括号、句末标点等） */
        private val FORBIDDEN_LINE_START_CHARS = setOf(
            '、', '。', '，', '；', '：', '？', '！',
            '）', '】', '》', '」', '』', '…', '．',
            '”', '’', // 右双引号、右单引号
            ')', '>', ']', '}', ',', '.', '?', '!', ':', ';',
        )
        /** 禁止出现在行尾的标点（左引号、左括号等） */
        private val FORBIDDEN_LINE_END_CHARS = setOf(
            '“', '‘', // 左双引号、左单引号
            '（', '《', '【', '「', '『',
            '(', '<', '[', '{',
        )
    }

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

    /**
     * 对章节内容进行分页（盒子模型版本）。
     *
     * PageLayout 按页生成：首页包含 title box，后续页 title=null，body 自动向上扩展。
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

        // 预计算标题 StaticLayout（首页独有）
        val bodyWidth = PageLayoutCalculator.bodyWidth(config.pageSize, config.bodyInsets)
        val titleResult = buildTitleLayout(config, title, bodyWidth)

        val headerHeight = HEADER_CONTENT_HEIGHT_DP * config.density
        val footerHeight = FOOTER_CONTENT_HEIGHT_DP * config.density

        while (currentOffset < content.length) {
            val isFirstPage = pageIndex == 0

            // 按页生成 layout（首页有 title，后续页无 title）
            val layout = PageLayoutCalculator.calculate(
                pageSize = config.pageSize,
                header = BoxSpec(
                    insets = config.headerInsets,
                    innerHeight = headerHeight,
                    placement = BoxSpec.Placement.TOP_DOWN,
                    visible = showHeader,
                ),
                title = BoxSpec(
                    insets = config.titleInsets,
                    innerHeight = if (isFirstPage) titleResult.second else 0f,
                    placement = BoxSpec.Placement.TOP_DOWN,
                    visible = isFirstPage && titleResult.first != null,
                ),
                body = BoxSpec(
                    insets = config.bodyInsets,
                    placement = BoxSpec.Placement.FILL,
                    visible = true,
                ),
                footer = BoxSpec(
                    insets = config.footerInsets,
                    innerHeight = footerHeight,
                    placement = BoxSpec.Placement.BOTTOM_UP,
                    visible = showFooter,
                ),
            )

            val page = paginatePage(
                chapterIndex = chapterIndex,
                pageIndex = pageIndex,
                content = content,
                startOffset = currentOffset,
                widthWindow = widthWindow,
                config = config,
                layout = layout,
                titleLayout = if (isFirstPage) titleResult.first else null,
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

        val bodyWidth = PageLayoutCalculator.bodyWidth(config.pageSize, config.bodyInsets)
        val titleResult = buildTitleLayout(config, chapter.title, bodyWidth)
        val headerHeight = HEADER_CONTENT_HEIGHT_DP * config.density
        val footerHeight = FOOTER_CONTENT_HEIGHT_DP * config.density

        while (currentOffset < content.length) {
            val isFirstPage = pageIndex == 0

            val layout = PageLayoutCalculator.calculate(
                pageSize = config.pageSize,
                header = BoxSpec(
                    insets = config.headerInsets,
                    innerHeight = headerHeight,
                    placement = BoxSpec.Placement.TOP_DOWN,
                    visible = showHeader,
                ),
                title = BoxSpec(
                    insets = config.titleInsets,
                    innerHeight = if (isFirstPage) titleResult.second else 0f,
                    placement = BoxSpec.Placement.TOP_DOWN,
                    visible = isFirstPage && titleResult.first != null,
                ),
                body = BoxSpec(
                    insets = config.bodyInsets,
                    placement = BoxSpec.Placement.FILL,
                    visible = true,
                ),
                footer = BoxSpec(
                    insets = config.footerInsets,
                    innerHeight = footerHeight,
                    placement = BoxSpec.Placement.BOTTOM_UP,
                    visible = showFooter,
                ),
            )

            val page = paginatePage(
                chapterIndex = chapter.chapterIndex,
                pageIndex = pageIndex,
                content = content,
                startOffset = currentOffset,
                widthWindow = widthWindow,
                config = config,
                layout = layout,
                titleLayout = if (isFirstPage) titleResult.first else null,
                chapterTitle = chapter.title,
            )
            chapter.addPage(page)
            emit(page)
            currentOffset = page.endCharOffset
            pageIndex++
        }
    }

    /**
     * 分页单个页面（盒子模型版本）。
     *
     * 使用 layout.body 作为排版区域，保证至少排版一行防死循环。
     */
    private fun paginatePage(
        chapterIndex: Int,
        pageIndex: Int,
        content: String,
        startOffset: Int,
        widthWindow: WidthWindow,
        config: ReaderLayoutConfig,
        layout: PageLayout,
        titleLayout: StaticLayout?,
        chapterTitle: String = "",
    ): TextPage {
        val lines = mutableListOf<TextLine>()
        val body = layout.body
        val availableWidth = body.width
        val lineHeight = textMeasurer.measureTextHeight(config.textSize, config.lineHeight)
        var currentY = body.top
        val maxY = body.bottom
        val indentWidth = calcIndent(config, availableWidth)
        var currentOffset = startOffset
        var mustPlaceAtLeastOneLine = true

        // 按行分页（防死循环：保证至少排版一行）
        while ((currentY + lineHeight <= maxY + EPSILON || mustPlaceAtLeastOneLine) && currentOffset < content.length) {
            mustPlaceAtLeastOneLine = false

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

        // 底部对齐：将剩余垂直空间均匀分配到行间距中
        val finalLines = if (config.bottomJustify && lines.size > 1) {
            val lastBottom = lines.last().bottom
            val remainingSpace = maxY - lastBottom
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
            lines = finalLines,
            layout = layout,
            titleLayout = titleLayout,
            density = config.density,
            chapterContentLength = content.length,
            chapterTitle = chapterTitle,
        )
    }

    /**
     * 预计算标题的 StaticLayout 和总高度。
     * 返回 Pair<StaticLayout?, totalHeight>。
     */
    private fun buildTitleLayout(
        config: ReaderLayoutConfig,
        chapterTitle: String,
        availableWidth: Float,
    ): Pair<StaticLayout?, Float> {
        val ts = config.titleStyle
        if (ts.align == TitleAlign.HIDDEN || chapterTitle.isBlank()) return null to 0f
        val d = config.density
        val titleTextSize = config.titleFontSizePx
        val paint = TextPaint().apply {
            textSize = titleTextSize
            typeface = config.titleTypeface ?: android.graphics.Typeface.DEFAULT
            isFakeBoldText = config.titleIsFakeBold
        }
        val layoutAlign = when (ts.align) {
            TitleAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TitleAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            TitleAlign.HIDDEN -> return null to 0f
        }
        val w = availableWidth.toInt().coerceAtLeast(1)
        val builder = StaticLayout.Builder.obtain(
            chapterTitle, 0, chapterTitle.length, paint, w
        ) ?: return null to (ts.marginTopDp * d + titleTextSize * 1.3f + ts.marginBottomDp * d)
        val layout = builder.setAlignment(layoutAlign).setIncludePad(false).build()
        val totalHeight = ts.marginTopDp * d + layout.height + ts.marginBottomDp * d
        return layout to totalHeight
    }

    /** 计算段落首行缩进宽度 */
    private fun calcIndent(config: ReaderLayoutConfig, availableWidth: Float): Float =
        if (availableWidth > config.indent * 2f) config.indent else 0f

    /** 跳过段落起始处的前导空白字符 */
    private fun skipLeadingSpaces(content: String, offset: Int, isFirstLine: Boolean): Int {
        if (!isFirstLine && (offset <= 0 || content[offset - 1] != '\n')) return offset
        var pos = offset
        while (pos < content.length && content[pos] in " \t　\r") pos++
        return pos
    }

    /** 合并连续换行符为一个段间距 */
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

        val newlineAbsIndex = content.indexOf('\n', startOffset)
        val lineEnd = if (newlineAbsIndex >= 0) newlineAbsIndex - startOffset else content.length - startOffset

        if (lineEnd == 0 && newlineAbsIndex == startOffset) {
            return LineResult(true, 0, 1)
        }

        var currentWidth = 0f
        var charCount = 0

        for (i in 0 until lineEnd) {
            val width = widthWindow[startOffset + i]
            val spacing = if (charCount > 0) letterSpacingPx else 0f
            if (currentWidth + width + spacing > availableWidth) break
            currentWidth += width + spacing
            charCount++
        }

        if (charCount == 0 && lineEnd > 0) {
            charCount = 1
            currentWidth = widthWindow[startOffset]
        }

        if (charCount == 0) {
            return LineResult(false, 0, 0)
        }

        if (useZhLayout) {
            while (charCount > 1 && content[startOffset + charCount - 1] in FORBIDDEN_LINE_END_CHARS) {
                charCount--
                currentWidth -= widthWindow[startOffset + charCount] + letterSpacingPx
            }
            if (charCount < lineEnd && content[startOffset + charCount] in FORBIDDEN_LINE_START_CHARS) {
                currentWidth += widthWindow[startOffset + charCount] + letterSpacingPx
                charCount++
            }
        } else {
            if (charCount < lineEnd && content[startOffset + charCount] in FORBIDDEN_LINE_START_CHARS) {
                currentWidth += widthWindow[startOffset + charCount] + letterSpacingPx
                charCount++
            }
        }

        val consumesLineBreak = newlineAbsIndex >= 0 && charCount == lineEnd
        val consumedChars = charCount + if (consumesLineBreak) 1 else 0
        val isParagraphEnd = consumesLineBreak
        val measuredWidth = if (charCount > 0) currentWidth else 0f

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
        val charCount: Int,
        val consumedChars: Int,
        val charWidths: FloatArray? = null,
        val measuredWidth: Float = 0f,
    )
}
