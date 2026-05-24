package com.shuli.reader.core.reader

import android.content.Context
import android.graphics.Bitmap
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
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme

/**
 * 阅读器 Canvas 视图
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

    // 当前页面
    private var currentPage: TextPage? = null

    // 下一页（用于动画）
    private var nextPage: TextPage? = null

    // 上一页（用于 PREV 动画）
    private var prevPage: TextPage? = null

    // 页眉页脚配置
    private var headerText: String = ""
    private var footerText: String = ""
    private var showProgress: Boolean = true
    private var selectedRange: SelectionRange? = null
    private var ttsActiveRange: SelectionRange? = null
    private var batteryLevel: Int = 100

    private val pageRenderer = ReaderPageRenderer(textPaint, headerPaint, footerPaint, progressPaint)

    fun setBatteryLevel(level: Int) {
        if (batteryLevel == level) return
        batteryLevel = level
        if (isAnimating()) return
        updateCurrentBitmapHeaderFooter()
        invalidate()
    }

    // 翻页动画委托
    private var pageDelegate: PageDelegate? = null

    // 位图缓冲
    private var currentBitmap: Bitmap? = null
    private var nextBitmap: Bitmap? = null
    private var prevBitmap: Bitmap? = null

    // 翻页回调
    var onPageChanged: ((PageDelegate.Direction) -> Unit)? = null

    // 文本选区回调
    var onTextSelected: ((SelectionRange) -> Unit)? = null

    // 中心区域点击回调
    var onCenterClicked: (() -> Unit)? = null

    private var isTextSelectionGesture = false

    // 动画期间页面数据已更新，需要在动画结束后重新渲染 Bitmap
    private var pendingBitmapRefresh = false

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
                    // 点击左区域，触发上一页动画
                    pageDelegate?.startPrev() ?: onPageChanged?.invoke(PageDelegate.Direction.PREV)
                    return true
                } else if (x >= w * 2f / 3f) {
                    // 点击右区域，触发下一页动画
                    pageDelegate?.startNext() ?: onPageChanged?.invoke(PageDelegate.Direction.NEXT)
                    return true
                }
                return false
            }
        },
    )

    /**
     * 设置页面内容。
     * 动画进行中时仅更新数据引用，不重新渲染 Bitmap，避免抖动。
     * 动画结束后下次 setPage 调用会补渲染。
     */
    fun setPage(page: TextPage, next: TextPage? = null, prev: TextPage? = null) {
        val changed = currentPage !== page || nextPage !== next || prevPage !== prev
        currentPage = page
        nextPage = next
        prevPage = prev

        if (isAnimating()) {
            // 动画期间：仅标记待刷新，不重渲染 Bitmap
            if (changed) {
                selectedRange = null
                pendingBitmapRefresh = true
            }
            return
        }

        if (changed || pendingBitmapRefresh) {
            selectedRange = null
            pendingBitmapRefresh = false
            preRenderAllBitmaps()
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
        if (headerText == text) return
        headerText = text
        if (isAnimating()) return
        updateCurrentBitmapHeaderFooter()
        invalidate()
    }

    /**
     * 设置页脚文本
     */
    fun setFooterText(text: String) {
        if (footerText == text) return
        footerText = text
        if (isAnimating()) return
        updateCurrentBitmapHeaderFooter()
        invalidate()
    }

    /**
     * 设置是否显示进度条
     */
    fun setShowProgress(show: Boolean) {
        if (showProgress == show) return
        showProgress = show
        if (isAnimating()) return
        updateCurrentBitmapHeaderFooter()
        invalidate()
    }

    fun setTextSizePx(textSize: Float) {
        if (textPaint.textSize == textSize) return
        textPaint.textSize = textSize
        headerPaint.textSize = textSize * HEADER_TEXT_RATIO
        footerPaint.textSize = textSize * FOOTER_TEXT_RATIO
        if (isAnimating()) {
            pendingBitmapRefresh = true
            return
        }
        preRenderAllBitmaps()
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
        if (isAnimating()) {
            pendingBitmapRefresh = true
            return
        }
        preRenderAllBitmaps()
        invalidate()
    }

    fun clearSelection() {
        if (selectedRange == null) return
        selectedRange = null
        if (isAnimating()) return
        updateCurrentBitmapHeaderFooter()
        invalidate()
    }

    fun setTtsActiveRange(range: SelectionRange?) {
        if (ttsActiveRange == range) return
        ttsActiveRange = range
        if (isAnimating()) return
        updateCurrentBitmapHeaderFooter()
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
        if (isAnimating()) {
            pendingBitmapRefresh = true
            return
        }
        preRenderAllBitmaps()
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val gestureHandled = gestureDetector.onTouchEvent(event)
        if (isTextSelectionGesture) {
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                isTextSelectionGesture = false
            }
            return true
        }

        // 中心区域不触发翻页动画
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
            // 只有左右区域才传递给翻页委托
            if (!isInCenterZone) {
                return delegate.onTouch(event)
            }
            return gestureHandled
        }
        return super.onTouchEvent(event)
    }

    /**
     * 立即预渲染当前页、下一页、上一页的 Bitmap。
     * 在页面数据变化时调用，确保动画开始前所有 Bitmap 已就绪。
     */
    private fun preRenderAllBitmaps() {
        if (width <= 0 || height <= 0) return
        releaseBitmaps()
        currentPage?.let {
            currentBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                drawPageContent(Canvas(bmp), it)
            }
        }
        nextPage?.let {
            nextBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                drawPageContent(Canvas(bmp), it)
            }
        }
        prevPage?.let {
            prevBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                drawPageContent(Canvas(bmp), it)
            }
        }
    }

    /**
     * 仅重新渲染当前页的 Bitmap（页眉/页脚/电量/选区/高亮变化时使用）。
     * 不释放 next/prev Bitmap，避免翻页动画抖动。
     */
    private fun updateCurrentBitmapHeaderFooter() {
        val page = currentPage
        val bmp = currentBitmap
        if (page != null && bmp != null && !bmp.isRecycled && width > 0 && height > 0) {
            drawPageContent(Canvas(bmp), page)
        }
    }

    private fun releaseBitmaps() {
        currentBitmap?.takeIf { !it.isRecycled }?.recycle()
        nextBitmap?.takeIf { !it.isRecycled }?.recycle()
        prevBitmap?.takeIf { !it.isRecycled }?.recycle()
        currentBitmap = null
        nextBitmap = null
        prevBitmap = null
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) {
            releaseBitmaps()
            // 尺寸变化后预渲染（需 post 确保 layout 完成）
            post { preRenderAllBitmaps() }
        }
    }

    override fun onDetachedFromWindow() {
        pendingBitmapRefresh = false
        releaseBitmaps()
        super.onDetachedFromWindow()
    }

    private fun drawPageContent(canvas: Canvas, page: TextPage) {
        pageRenderer.render(
            canvas = canvas,
            page = page,
            headerText = headerText,
            footerText = footerText,
            showProgress = showProgress,
            batteryLevel = batteryLevel,
            ttsActiveRange = ttsActiveRange,
            selectedRange = selectedRange,
            ttsHighlightPaint = ttsHighlightPaint,
            selectionPaint = selectionPaint,
            backgroundPaint = backgroundPaint
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val delegate = pageDelegate

        if (delegate != null && currentPage != null) {
            // 使用动画委托绘制（Bitmap 已在 setPage/preRender 时预渲染）
            val current = currentBitmap ?: run {
                // 首帧兜底：View 尚未 layout 时 setPage 中预渲染被跳过
                preRenderAllBitmaps()
                currentBitmap ?: return
            }
            // 根据动画方向选择目标页 Bitmap：PREV 用上一页，NEXT 用下一页
            val isPrevDirection = when (delegate.state) {
                PageDelegate.State.DRAGGING -> {
                    // 拖拽状态根据偏移方向判断
                    delegate.isDraggingBackward()
                }
                PageDelegate.State.ANIMATING -> {
                    delegate.direction == PageDelegate.Direction.PREV
                }
                else -> false
            }
            val next = if (isPrevDirection) {
                prevBitmap ?: current
            } else {
                nextBitmap ?: current
            }
            delegate.onDraw(canvas, current, next)
        } else {
            // 直接绘制（无动画）
            val page = currentPage ?: return
            drawPageContent(canvas, page)
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
        selectedRange = range
        isTextSelectionGesture = true
        updateCurrentBitmapHeaderFooter()
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

    private fun SelectionRange.intersects(start: Int, end: Int): Boolean {
        return startPos < end && endPos > start
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
        private const val TEXT_START_X = 20f
        private const val TEXT_END_PADDING = 20f
        private const val TEXT_FIRST_BASELINE = 100f
        private const val LINE_HEIGHT_MULTIPLIER = 1.5f
        private const val HEADER_TEXT_RATIO = 0.75f
        private const val FOOTER_TEXT_RATIO = 0.75f
        private const val SELECTION_ALPHA = 0x33
        private const val TTS_HIGHLIGHT_ALPHA = 0x24
        private const val SELECTION_HORIZONTAL_PADDING = 6f
        private const val SELECTION_CORNER_RADIUS = 6f
        private const val RGB_MASK = 0x00FFFFFF
        private const val ALPHA_SHIFT = 24
    }
}
