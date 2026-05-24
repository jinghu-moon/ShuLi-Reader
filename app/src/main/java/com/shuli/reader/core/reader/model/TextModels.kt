package com.shuli.reader.core.reader.model

import com.shuli.reader.core.canvasrecorder.CanvasRecorder
import com.shuli.reader.core.canvasrecorder.CanvasRecorderFactory

/**
 * 页面尺寸配置
 */
data class PageSize(
    val width: Int,
    val height: Int,
)

/**
 * 阅读器布局配置
 */
data class ReaderLayoutConfig(
    val pageSize: PageSize,
    val textSize: Float,
    val lineHeight: Float,
    val paragraphSpacing: Float,
    val marginHorizontal: Float,
    val marginVertical: Float,
    val indent: Float,
    val density: Float = 3f,
)

/**
 * 文本列，支持字符级坐标
 */
data class TextColumn(
    val startCharOffset: Int,
    val endCharOffset: Int,
    val startLine: Int,
    val endLine: Int,
)

/**
 * 文本行
 */
data class TextLine(
    val text: String,
    val baseline: Float,
    val top: Float,
    val bottom: Float,
    val isParagraphEnd: Boolean,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val startXOffset: Float = 0f,
)

/**
 * 文本页
 *
 * 非 data class：每页持有独立的 CanvasRecorder 用于渲染缓存，
 * equals/hashCode 使用引用比较，copy 不再需要。
 */
class TextPage(
    val startCharOffset: Int,
    val endCharOffset: Int,
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageSize: PageSize,
    val marginHorizontal: Float,
    val lines: List<TextLine>,
    val columns: List<TextColumn>,
    val density: Float = 3f,
    /** 章节正文总字符数（不含标题），用于计算阅读进度 */
    val chapterContentLength: Int = 0,
) {
    /** 渲染缓存，每页一份，跨章节共享池资源。 */
    @Transient
    val canvasRecorder: CanvasRecorder = CanvasRecorderFactory.create(locked = true)

    /** 标记 recorder 失效，下次绘制时会重录。 */
    fun invalidate() = canvasRecorder.invalidate()

    /** 释放 recorder 对应的 RenderNode/Picture 回池。 */
    fun recycleRecorders() = canvasRecorder.recycle()

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * 文本章节
 */
data class TextChapter(
    val chapterIndex: Int,
    val title: String,
    val content: String,
    val pages: List<TextPage>,
) {
    companion object {
        /** 非最后一页的进度上限 */
        private const val MAX_NON_FINAL_PROGRESS = 0.999f
    }

    /**
     * 根据字符偏移获取页码
     */
    fun getPageIndexByCharIndex(charIndex: Int): Int {
        if (pages.isEmpty()) return 0

        // 边界检查
        if (charIndex < 0) return 0
        if (charIndex >= content.length) return pages.size - 1

        // 查找包含该字符的页面
        for (i in pages.indices) {
            val page = pages[i]
            if (charIndex >= page.startCharOffset && charIndex < page.endCharOffset) {
                return i
            }
        }

        // 如果找不到，返回最后一页
        return pages.size - 1
    }

    /**
     * 根据字符偏移计算阅读进度（0.0 ~ 1.0）
     * 非最后一页上限 99.9%，最后一页返回 100%
     */
    fun readProgress(charIndex: Int): Float {
        if (pages.isEmpty()) return 0f
        if (content.isEmpty()) return 1.0f

        val clampedIndex = charIndex.coerceIn(0, content.length)
        val pageIndex = getPageIndexByCharIndex(clampedIndex)
        val isLastPage = pageIndex >= pages.size - 1

        if (isLastPage) return 1.0f

        val totalLength = content.length
        val progress = clampedIndex.toFloat() / totalLength
        return progress.coerceAtMost(MAX_NON_FINAL_PROGRESS)
    }
}