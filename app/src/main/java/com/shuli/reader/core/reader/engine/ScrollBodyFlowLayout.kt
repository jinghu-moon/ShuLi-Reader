package com.shuli.reader.core.reader.engine

import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.core.reader.model.TitleStyleConfig
import com.shuli.reader.core.reader.model.BoxBounds
import kotlin.math.max

/**
 * 连续滚动模式下的正文流布局。
 *
 * 普通翻页按 PageLayout 的绝对坐标绘制；连续滚动需要把每个分页转换成局部内容片段，
 * 让章节尾页、下一章标题、下一章正文按实际内容高度首尾相接。
 */
internal object ScrollBodyFlowLayout {
    private const val LAST_PAGE_END_PADDING_DP = 20f

    fun segmentFor(page: TextPage, titleStyle: TitleStyleConfig): ScrollBodySegment {
        val body = page.layout.body
        val titleTop = titleFlowTop(page)
        val titleContentTop = titleContentTop(page, titleStyle)
        val titleBottom = titleContentTop?.let { top -> top + (page.titleLayout?.height ?: 0) }

        if (page.lines.isEmpty() && titleTop == null) {
            return ScrollBodySegment(
                sourceLeft = body.left,
                sourceTop = body.top,
                height = body.height.coerceAtLeast(1f),
            )
        }

        val sourceTop = titleTop ?: body.top
        val lineBottom = page.lines.maxOfOrNull { it.bottom } ?: sourceTop
        val contentBottom = max(lineBottom, titleBottom ?: sourceTop)
        val contentHeight = (contentBottom - sourceTop).coerceAtLeast(1f)
        val height = contentHeight + if (page.isChapterEnd()) LAST_PAGE_END_PADDING_DP * page.density else 0f

        return ScrollBodySegment(
            sourceLeft = body.left,
            sourceTop = sourceTop,
            height = height.coerceAtLeast(1f),
        )
    }

    fun viewportFor(page: TextPage): BoxBounds {
        val body = page.layout.body
        val top = page.layout.title?.top ?: body.top
        return BoxBounds(
            left = body.left,
            top = top.coerceAtMost(body.bottom),
            right = body.right,
            bottom = body.bottom,
        )
    }

    private fun titleFlowTop(page: TextPage): Float? {
        if (page.titleLayout == null) return null
        return page.layout.title?.top
    }

    private fun titleContentTop(page: TextPage, titleStyle: TitleStyleConfig): Float? {
        val titleBox = page.layout.title ?: return null
        if (page.titleLayout == null) return null
        return titleBox.top + titleStyle.marginTopDp * page.density
    }

    private fun TextPage.isChapterEnd(): Boolean {
        return chapterContentLength <= 0 || endCharOffset >= chapterContentLength
    }
}

internal data class ScrollBodySegment(
    val sourceLeft: Float,
    val sourceTop: Float,
    val height: Float,
)
