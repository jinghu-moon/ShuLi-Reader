package com.shuli.reader.core.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.shuli.reader.R
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.reader.animation.PageDelegate
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.core.canvasrecorder.CanvasRecorder
import com.shuli.reader.core.canvasrecorder.recordIfNeeded
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme

/**
 * 阅读器 Canvas 视图
 *
 * 渲染架构：每页持有独立的 CanvasRecorder（RenderNode/Picture），
 * 选区/TTS 高亮变化仅 invalidate recorder 而非重绘 Bitmap，
 * 内存从 ~30 MB 降至 < 1 MB。
 */
class ReaderCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val defaultColors = ReaderTheme.PAPER.toReaderColorScheme().toCanvasThemeColors()

    // 画笔
    private val backgroundPaint = Paint().apply {
        color = defaultColors.backgroundColor
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = defaultColors.textColor
        textSize = 48f
        isAntiAlias = true
    }

    private val headerPaint = Paint().apply {
        color = defaultColors.headerColor
        textSize = 36f
        isAntiAlias = true
    }

    private val footerPaint = Paint().apply {
        color = defaultColors.footerColor
        textSize = 36f
        isAntiAlias = true
    }

    private val progressPaint = Paint().apply {
        color = defaultColors.progressColor
        style = Paint.Style.FILL
    }

    private val selectionPaint = Paint().apply {
        color = defaultColors.progressColor.withAlpha(SELECTION_ALPHA)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val ttsHighlightPaint = Paint().apply {
        color = defaultColors.progressColor.withAlpha(TTS_HIGHLIGHT_ALPHA)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 页面引用
    private var currentPage: TextPage? = null
    private var nextPage: TextPage? = null
    private var prevPage: TextPage? = null

    // 渲染参数（recordPage 闭包上下文）
    private val renderContext = RenderContext()

    private inner class RenderContext {
        var headerText: String = ""
        var footerText: String = ""
        var showProgress: Boolean = true
        var batteryLevel: Int = 100
        var ttsActiveRange: SelectionRange? = null
        var selectedRange: SelectionRange? = null
    }

    private val pageRenderer = ReaderPageRenderer(textPaint, headerPaint, footerPaint, progressPaint)

    fun setBatteryLevel(level: Int) {
        if (renderContext.batteryLevel == level) return
        renderContext.batteryLevel = level
        if (isAnimating()) return
        currentPage?.invalidate()
        invalidate()
    }

    // 翻页动画委托
    private var pageDelegate: PageDelegate? = null

    // 翻页回调
    var onPageChanged: ((PageDelegate.Direction) -> Unit)? = null

    // 文本选区回调
    var onTextSelected: ((SelectionRange) -> Unit)? = null

    // 中心区域点击回调
    var onCenterClicked: (() -> Unit)? = null

    private var isTextSelectionGesture = false

    /** 录制单页：命中缓存则直接 draw，不会重录。 */
    private fun recordPage(page: TextPage) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        page.canvasRecorder.recordIfNeeded(w, h) {
            pageRenderer.render(
                canvas = this,
                page = page,
                headerText = renderContext.headerText,
                footerText = renderContext.footerText,
                showProgress = renderContext.showProgress,
                batteryLevel = renderContext.batteryLevel,
                ttsActiveRange = renderContext.ttsActiveRange,
                selectedRange = renderContext.selectedRange,
                ttsHighlightPaint = ttsHighlightPaint,
                selectionPaint = selectionPaint,
                backgroundPaint = backgroundPaint,
            )
        }
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onLongPress(event: MotionEvent) {
                pageDelegate?.abort()
                selectLineAt(event.x, event.y)
            }

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                val x = event.x
                val y = event.y
                val w = width.toFloat()
                val h = height.toFloat()

                val isCenter = x > w / 3f && x < w * 2f / 3f && y > h / 3f && y < h * 2f / 3f

                if (isCenter) {
                    onCenterClicked?.invoke()
                    return true
                } else if (x <= w / 3f) {
                    pageDelegate?.startPrev() ?: onPageChanged?.invoke(PageDelegate.Direction.PREV)
                    return true
                } else if (x >= w * 2f / 3f) {
                    pageDelegate?.startNext() ?: onPageChanged?.invoke(PageDelegate.Direction.NEXT)
                    return true
                }
                return false
            }
        },
    )

    /**
     * 设置页面内容。
     * CanvasRecorder 自带缓存，仅在 invalidate 后下次绘制时重录。
     */
    fun setPage(page: TextPage, next: TextPage? = null, prev: TextPage? = null) {
        val changed = currentPage !== page || nextPage !== next || prevPage !== prev
        currentPage = page
        nextPage = next
        prevPage = prev

        if (changed) {
            renderContext.selectedRange = null
        }
        invalidate()
    }

    private fun isAnimating(): Boolean {
        return pageDelegate?.let {
            it.state == PageDelegate.State.DRAGGING || it.state == PageDelegate.State.ANIMATING
        } == true
    }

    private fun isAnimationDisabled(): Boolean {
        return try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 设置翻页动画委托
     */
    fun setPageDelegate(delegate: PageDelegate?) {
        val actualDelegate = if (isAnimationDisabled() && delegate != null && delegate !is com.shuli.reader.core.reader.animation.NoAnimPageDelegate) {
            com.shuli.reader.core.reader.animation.NoAnimPageDelegate()
        } else {
            delegate
        }

        pageDelegate = actualDelegate
        actualDelegate?.setCallback(object : PageDelegate.Callback {
            override fun onPageChanged(direction: PageDelegate.Direction) {
                onPageChanged?.invoke(direction)
            }

            override fun invalidate() {
                this@ReaderCanvasView.invalidate()
            }
        })
    }

    /**
     * 设置页眉文本
     */
    fun setHeaderText(text: String) {
        if (renderContext.headerText == text) return
        renderContext.headerText = text
        if (isAnimating()) return
        currentPage?.invalidate()
        invalidate()
    }

    /**
     * 设置页脚文本
     */
    fun setFooterText(text: String) {
        if (renderContext.footerText == text) return
        renderContext.footerText = text
        if (isAnimating()) return
        currentPage?.invalidate()
        invalidate()
    }

    /**
     * 设置是否显示进度条
     */
    fun setShowProgress(show: Boolean) {
        if (renderContext.showProgress == show) return
        renderContext.showProgress = show
        if (isAnimating()) return
        currentPage?.invalidate()
        invalidate()
    }

    fun setTextSizePx(textSize: Float) {
        if (textPaint.textSize == textSize) return
        textPaint.textSize = textSize
        headerPaint.textSize = textSize * HEADER_TEXT_RATIO
        footerPaint.textSize = textSize * FOOTER_TEXT_RATIO
        invalidateAllRecorders()
        invalidate()
    }

    /**
     * 设置阅读字体（"system" = 系统默认，其他 = LXGW 文楷）
     */
    fun setFontFamily(fontKey: String) {
        val typeface = when (fontKey) {
            "system" -> Typeface.DEFAULT
            else -> try {
                ResourcesCompat.getFont(context, R.font.lxgw_wenkai_regular)
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
        if (textPaint.typeface == typeface) return
        textPaint.typeface = typeface
        invalidateAllRecorders()
        invalidate()
    }

    fun clearSelection() {
        if (renderContext.selectedRange == null) return
        renderContext.selectedRange = null
        if (isAnimating()) return
        currentPage?.invalidate()
        invalidate()
    }

    fun setTtsActiveRange(range: SelectionRange?) {
        if (renderContext.ttsActiveRange == range) return
        renderContext.ttsActiveRange = range
        if (isAnimating()) return
        currentPage?.invalidate()
        invalidate()
    }

    /**
     * 设置主题
     */
    fun setTheme(
        backgroundColor: Int,
        textColor: Int,
        headerColor: Int,
        footerColor: Int,
        progressColor: Int,
    ) {
        if (
            backgroundPaint.color == backgroundColor &&
            textPaint.color == textColor &&
            headerPaint.color == headerColor &&
            footerPaint.color == footerColor &&
            progressPaint.color == progressColor
        ) {
            return
        }
        backgroundPaint.color = backgroundColor
        textPaint.color = textColor
        headerPaint.color = headerColor
        footerPaint.color = footerColor
        progressPaint.color = progressColor
        selectionPaint.color = progressColor.withAlpha(SELECTION_ALPHA)
        ttsHighlightPaint.color = progressColor.withAlpha(TTS_HIGHLIGHT_ALPHA)
        invalidateAllRecorders()
        invalidate()
    }

    /**
     * 通过 ThemeColors 统一设置主题
     */
    fun setThemeColors(colors: ThemeColors) {
        setTheme(
            backgroundColor = colors.backgroundColor,
            textColor = colors.textColor,
            headerColor = colors.headerColor,
            footerColor = colors.footerColor,
            progressColor = colors.progressColor,
        )
    }

    /** 使所有页面 recorder 失效（字体/主题/尺寸等全局变化时使用） */
    private fun invalidateAllRecorders() {
        currentPage?.invalidate()
        nextPage?.invalidate()
        prevPage?.invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val gestureHandled = gestureDetector.onTouchEvent(event)
        if (isTextSelectionGesture) {
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                isTextSelectionGesture = false
            }
            return true
        }

        val x = event.x
        val w = width.toFloat()
        val isInCenterZone = x > w / 3f && x < w * 2f / 3f

        val delegate = pageDelegate
        if (delegate != null) {
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                val delegateActive = delegate.state == PageDelegate.State.DRAGGING ||
                    delegate.state == PageDelegate.State.ANIMATING
                if (delegateActive) {
                    return delegate.onTouch(event)
                }
                return gestureHandled
            }
            if (!isInCenterZone) {
                return delegate.onTouch(event)
            }
            return gestureHandled
        }
        return super.onTouchEvent(event)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) {
            invalidateAllRecorders()
        }
    }

    override fun onDetachedFromWindow() {
        currentPage?.recycleRecorders()
        nextPage?.recycleRecorders()
        prevPage?.recycleRecorders()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = currentPage ?: return
        recordPage(current)

        val delegate = pageDelegate
        if (delegate != null) {
            val isPrevDirection = when (delegate.state) {
                PageDelegate.State.DRAGGING -> delegate.isDraggingBackward()
                PageDelegate.State.ANIMATING -> delegate.direction == PageDelegate.Direction.PREV
                else -> false
            }
            val target = if (isPrevDirection) prevPage else nextPage
            target?.let { recordPage(it) }
            delegate.onDraw(canvas, current.canvasRecorder, target?.canvasRecorder ?: current.canvasRecorder)
        } else {
            current.canvasRecorder.draw(canvas)
        }
    }

    private fun selectLineAt(x: Float, y: Float) {
        val page = currentPage ?: return
        val line = page.lines.firstOrNullIndexed { index, line ->
            val bounds = lineBounds(index, line.text)
            x >= 0f && x <= width.toFloat() && y >= bounds.top && y <= bounds.bottom
        } ?: return

        val range = SelectionRange(
            chapterIndex = page.chapterIndex,
            startPos = line.startCharOffset,
            endPos = line.endCharOffset,
            selectedText = line.text,
        )
        renderContext.selectedRange = range
        isTextSelectionGesture = true
        page.invalidate()
        invalidate()
        onTextSelected?.invoke(range)
    }

    private fun lineBounds(index: Int, text: String): RectF {
        val page = currentPage ?: return RectF()
        val line = page.lines.getOrNull(index) ?: return RectF()
        val startX = page.marginHorizontal + line.startXOffset
        val right = (startX + textPaint.measureText(line.text)).coerceAtMost(width.toFloat() - TEXT_END_PADDING)
        return RectF(startX - SELECTION_HORIZONTAL_PADDING, line.top, right + SELECTION_HORIZONTAL_PADDING, line.bottom)
    }

    private inline fun <T> List<T>.firstOrNullIndexed(predicate: (Int, T) -> Boolean): T? {
        for (index in indices) {
            val item = this[index]
            if (predicate(index, item)) return item
        }
        return null
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and RGB_MASK) or (alpha shl ALPHA_SHIFT)
    }

    private companion object {
        private const val TEXT_END_PADDING = 20f
        private const val HEADER_TEXT_RATIO = 0.75f
        private const val FOOTER_TEXT_RATIO = 0.75f
        private const val SELECTION_ALPHA = 0x33
        private const val TTS_HIGHLIGHT_ALPHA = 0x24
        private const val SELECTION_HORIZONTAL_PADDING = 6f
        private const val RGB_MASK = 0x00FFFFFF
        private const val ALPHA_SHIFT = 24
    }
}
