package com.shuli.reader.core.reader.model

import com.shuli.reader.core.canvasrecorder.CanvasRecorder
import com.shuli.reader.core.canvasrecorder.CanvasRecorderFactory
import com.shuli.reader.core.canvasrecorder.record
import com.shuli.reader.core.reader.TitleStyleConfig

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
    val letterSpacingPx: Float = 0f,  // 字距，已转为绝对像素
    val titleStyle: TitleStyleConfig = TitleStyleConfig(),
    val useZhLayout: Boolean = false,  // 自定义中文分行（标点避头尾）
    val bottomJustify: Boolean = false, // 底部对齐（均匀分布行间距）
    val headerMarginTop: Float = 48f,
    val footerMarginBottom: Float = 48f,
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
 * 文本行（区间模型）
 *
 * 不持有文本内容，通过 [startCharOffset, endCharOffset) 引用 TextChapter.content。
 * 渲染时由 PageRenderContext 提供 content。
 *
 * 支持 per-line CanvasRecorder：选区/TTS 高亮变化时仅重画受影响的行。
 *
 * ⚠️ data class 的 copy() 会丢失 @Transient canvasRecorder 引用，
 *    请勿对 TextLine 使用 copy()，如需不可变副本请手动构造。
 */
data class TextLine(
    /** 行在 content 中的起始偏移（含） */
    val startCharOffset: Int,
    /** 行在 content 中的结束偏移（不含） */
    val endCharOffset: Int,
    val baseline: Float,
    val top: Float,
    val bottom: Float,
    val isParagraphEnd: Boolean,
    val startXOffset: Float = 0f,
    /**
     * 文本实际占用宽度（分页时缓存）。
     *
     * 口径定义：measuredWidth = Σ(charWidth[i]) + letterSpacingPx × (charCount - 1)
     * - 包含：字符原始宽度 + 基础字距
     * - 不包含：两端对齐额外拉伸量
     */
    val measuredWidth: Float = 0f,
    /**
     * 字符宽度数组（不含字距），仅两端对齐模式且非段落末行时有值。
     * 长度 = endCharOffset - startCharOffset，每个元素为对应字符的原始宽度。
     *
     * 渲染辅助缓存，不是内容模型。不参与持久化、序列化、equals/hashCode。
     */
    val charWidths: FloatArray? = null,
) {
    /** 每行独立的渲染缓存，选区/TTS 变化时仅 invalidate 受影响行 */
    @Transient
    var canvasRecorder: CanvasRecorder? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextLine) return false
        return startCharOffset == other.startCharOffset &&
            endCharOffset == other.endCharOffset &&
            baseline == other.baseline &&
            top == other.top &&
            bottom == other.bottom &&
            isParagraphEnd == other.isParagraphEnd &&
            startXOffset == other.startXOffset &&
            measuredWidth == other.measuredWidth
    }

    override fun hashCode(): Int {
        var result = startCharOffset
        result = 31 * result + endCharOffset
        result = 31 * result + baseline.hashCode()
        result = 31 * result + top.hashCode()
        result = 31 * result + bottom.hashCode()
        result = 31 * result + isParagraphEnd.hashCode()
        result = 31 * result + startXOffset.hashCode()
        result = 31 * result + measuredWidth.hashCode()
        return result
    }

    /** 标记本行 recorder 失效 */
    fun invalidateSelf() {
        canvasRecorder?.invalidate()
    }

    /** 释放本行 recorder */
    fun recycleRecorder() {
        canvasRecorder?.recycle()
        canvasRecorder = null
    }

    /**
     * 判断本行是否与选区相交
     */
    fun intersects(range: SelectionRange): Boolean {
        return startCharOffset < range.endPos && endCharOffset > range.startPos
    }
}

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
    val columns: List<TextColumn> = emptyList(),
    val density: Float = 3f,
    /** 章节正文总字符数（不含标题），用于计算阅读进度 */
    val chapterContentLength: Int = 0,
    /** 章节标题，仅首页（pageIndex == 0）有值，渲染时用于绘制标题 */
    val chapterTitle: String = "",
    /** 正文起始 Y 坐标（已含 marginVertical + headerHeight + titleAreaHeight），渲染器据此定位标题基线 */
    val topContentY: Float = 0f,
    val headerMarginTop: Float = 48f,
    val footerMarginBottom: Float = 48f,
) {
    /** 内容渲染缓存（文本、标题、TTS/选区高亮），排版变化时重录。 */
    @Transient
    val canvasRecorder: CanvasRecorder = CanvasRecorderFactory.create(locked = true)

    /** 壳层渲染缓存（背景、页眉、页脚、电池、进度条），排版变化时保持不变。 */
    @Transient
    val shellRecorder: CanvasRecorder = CanvasRecorderFactory.create(locked = true)

    /** 合并渲染缓存（壳层+内容层叠加），翻页动画时使用，避免双层绘制冲突。 */
    @Transient
    val compositeRecorder: CanvasRecorder = CanvasRecorderFactory.create(locked = true)

    /**
     * 录制合并 recorder：先壳层后内容层叠加。
     * 仅在翻页动画触发时调用，静止状态不产生开销。
     */
    fun recordComposite(width: Int, height: Int) {
        if (!compositeRecorder.needRecord()) return
        compositeRecorder.record(width, height) {
            shellRecorder.draw(this)
            canvasRecorder.draw(this)
        }
    }

    /** 标记内容 recorder 及所有行级 recorder 失效，下次绘制时会重录。 */
    fun invalidate() {
        canvasRecorder.invalidate()
        compositeRecorder.invalidate()
        lines.forEach { it.invalidateSelf() }
    }

    /** 标记壳层 recorder 失效。 */
    fun invalidateShell() {
        shellRecorder.invalidate()
        compositeRecorder.invalidate()
    }

    /** 标记内容 + 壳层 + 所有行级 recorder 失效。 */
    fun invalidateAll() {
        canvasRecorder.invalidate()
        shellRecorder.invalidate()
        compositeRecorder.invalidate()
        lines.forEach { it.invalidateSelf() }
    }

    /** 释放所有 recorder。 */
    fun recycleRecorders() {
        canvasRecorder.recycle()
        shellRecorder.recycle()
        compositeRecorder.recycle()
        lines.forEach { it.recycleRecorder() }
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)

    companion object {
        /** 空页面占位符，用于页面工厂返回默认值 */
        val EMPTY = TextPage(
            startCharOffset = 0,
            endCharOffset = 0,
            chapterIndex = 0,
            pageIndex = 0,
            pageSize = PageSize(0, 0),
            marginHorizontal = 0f,
            lines = emptyList(),
            columns = emptyList(),
        )
    }
}

/**
 * 文本章节
 *
 * 支持流式分页：pages 列表在分页过程中逐步填充，
 * isCompleted 标记分页是否完成。
 */
class TextChapter(
    val chapterIndex: Int,
    val title: String,
    val content: String,
    pages: List<TextPage> = emptyList(),
) {
    private val _pages = mutableListOf<TextPage>().apply { addAll(pages) }
    val pages: List<TextPage> get() = synchronized(_pages) { _pages.toList() }

    /** 分页是否已完成 */
    @Volatile
    var isCompleted = pages.isNotEmpty()
        private set

    /** 分页事件监听器 */
    var layoutListener: LayoutListener? = null

    interface LayoutListener {
        fun onPageReady(index: Int, page: TextPage)
        fun onLayoutCompleted()
    }

    companion object {
        /** 非最后一页的进度上限 */
        private const val MAX_NON_FINAL_PROGRESS = 0.999f
    }

    /**
     * 添加页面（流式分页调用）
     */
    fun addPage(page: TextPage) {
        synchronized(_pages) { _pages.add(page) }
        layoutListener?.onPageReady(page.pageIndex, page)
    }

    /**
     * 标记分页完成
     */
    fun markCompleted() {
        isCompleted = true
        layoutListener?.onLayoutCompleted()
    }

    /**
     * 获取指定索引的页面
     */
    fun getPage(index: Int): TextPage? = synchronized(_pages) { _pages.getOrNull(index) }

    /** 最后一个有效索引 */
    val lastIndex: Int get() = synchronized(_pages) { _pages.lastIndex }

    /** 当前已分页的页面数 */
    val pageSize: Int get() = synchronized(_pages) { _pages.size }

    /**
     * 根据字符偏移获取页码
     */
    fun getPageIndexByCharIndex(charIndex: Int): Int {
        if (charIndex < 0) return 0
        return synchronized(_pages) {
            if (_pages.isEmpty()) return@synchronized 0
            val index = _pages.indexOfFirst { charIndex >= it.startCharOffset && charIndex < it.endCharOffset }
            if (index >= 0) index else _pages.lastIndex
        }
    }

    /**
     * 根据字符偏移计算阅读进度（0.0 ~ 1.0）
     * 非最后一页上限 99.9%，最后一页返回 100%
     */
    fun readProgress(charIndex: Int): Float {
        if (pageSize == 0) return 0f
        if (content.isEmpty()) return 1.0f

        val clampedIndex = charIndex.coerceIn(0, content.length)
        val pageIndex = getPageIndexByCharIndex(clampedIndex)
        val isLastPage = pageIndex >= lastIndex

        if (isLastPage) return 1.0f

        val totalLength = content.length
        val progress = clampedIndex.toFloat() / totalLength
        return progress.coerceAtMost(MAX_NON_FINAL_PROGRESS)
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}