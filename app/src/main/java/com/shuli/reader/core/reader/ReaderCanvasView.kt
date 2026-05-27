package com.shuli.reader.core.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import com.shuli.reader.R
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.reader.animation.PageDelegate
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.core.canvasrecorder.CanvasRecorder
import com.shuli.reader.core.canvasrecorder.recordIfNeeded
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme
import java.util.concurrent.Executors

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
    private val fontManager = FontManager(context)
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

    private val crossfadePaint = Paint().apply {
        isAntiAlias = true
    }

    // 页面引用
    private var currentPage: TextPage? = null
    private var nextPage: TextPage? = null
    private var prevPage: TextPage? = null

    // 排版变化 crossfade 动画
    private var oldPageBitmap: Bitmap? = null
    private var crossfadeAnimator: ValueAnimator? = null
    private var crossfadeAlpha: Float = 0f

    // 渲染参数（recordPage 闭包上下文）
    private val renderContext = RenderContext()

    private inner class RenderContext {
        var headerText: String = ""
        var footerText: String = ""
        var headerSlots: SlotResolution = SlotResolution()
        var footerSlots: SlotResolution = SlotResolution()
        var showProgress: Boolean = true
        var headerAlpha: Float = 0.4f
        var footerAlpha: Float = 0.4f
        var batteryLevel: Int = 100
        var ttsActiveRange: SelectionRange? = null
        var selectedRange: SelectionRange? = null
    }

    private val pageRenderer = ReaderPageRenderer(textPaint, headerPaint, footerPaint, progressPaint)

    fun setBatteryLevel(level: Int) {
        if (renderContext.batteryLevel == level) return
        renderContext.batteryLevel = level
        currentPage?.invalidate()
        invalidate()
    }

    // 翻页动画委托
    private var pageDelegate: PageDelegate? = null

    // 翻页回调
    var onPageChanged: ((PageDelegate.Direction) -> Unit)? = null

    // 翻页后立即获取新页眉页脚槽位（同步更新，避免页码延迟）
    var onPageChangedSlots: (() -> Pair<SlotResolution, SlotResolution>)? = null

    // 文本选区回调
    var onTextSelected: ((SelectionRange) -> Unit)? = null

    // 中心区域点击回调
    var onCenterClicked: (() -> Unit)? = null

    private var isTextSelectionGesture = false

    /** 录制页面的公共实现。返回 true 表示实际产生了录制。 */
    private fun doRecordPage(page: TextPage, w: Int, h: Int): Boolean {
        return page.canvasRecorder.recordIfNeeded(w, h) {
            pageRenderer.render(
                canvas = this,
                page = page,
                headerSlots = renderContext.headerSlots,
                footerSlots = renderContext.footerSlots,
                showProgress = renderContext.showProgress,
                headerAlpha = renderContext.headerAlpha,
                footerAlpha = renderContext.footerAlpha,
                batteryLevel = renderContext.batteryLevel,
                ttsActiveRange = renderContext.ttsActiveRange,
                selectedRange = renderContext.selectedRange,
                ttsHighlightPaint = ttsHighlightPaint,
                selectionPaint = selectionPaint,
                backgroundPaint = backgroundPaint,
            )
        }
    }

    /** 主线程同步录制单页（兜底用）。 */
    private fun recordPage(page: TextPage) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        doRecordPage(page, w, h)
    }

    /**
     * 后台线程录制页面。CanvasRecorderLocked 内部 ReentrantLock 保证线程安全。
     * 返回 true 表示实际产生了录制（即 recorder 之前是脏的）。
     */
    private fun recordPageOffMain(page: TextPage, w: Int, h: Int): Boolean {
        return doRecordPage(page, w, h)
    }

    /** 提交后台预渲染任务：录制 current/next/prev 三页，完成后触发重绘。 */
    private fun submitRenderTask() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        renderThread.execute {
            val cur = currentPage
            val nxt = nextPage
            val prv = prevPage
            var dirty = false
            cur?.let { if (recordPageOffMain(it, w, h)) dirty = true }
            nxt?.let { if (recordPageOffMain(it, w, h)) dirty = true }
            prv?.let { if (recordPageOffMain(it, w, h)) dirty = true }
            if (dirty) postInvalidate()
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

                // 仅处理中心区域点击（边缘点击在 onTouchEvent 中手动处理）
                val isCenter = x > w / 3f && x < w * 2f / 3f && y > h / 3f && y < h * 2f / 3f
                if (isCenter) {
                    onCenterClicked?.invoke()
                    return true
                }
                return false
            }
        },
    )

    /**
     * 设置页面内容。
     * CanvasRecorder 自带缓存，仅在 invalidate 后下次绘制时重录。
     * @param mode 渲染模式：SEQUENTIAL 预热邻页，JUMP/SCRUBBING 仅当前页
     * @param isLayoutChange 是否为排版参数变化导致的页面重建（触发 crossfade 过渡）
     */
    fun setPage(
        page: TextPage,
        next: TextPage? = null,
        prev: TextPage? = null,
        mode: PageRenderMode = PageRenderMode.SEQUENTIAL,
        isLayoutChange: Boolean = false,
    ) {
        val changed = currentPage !== page || nextPage !== next || prevPage !== prev

        // 排版变化 crossfade：必须在回收旧资源和更新引用之前捕获旧页面快照
        if (isLayoutChange && changed) {
            startLayoutCrossfade()
        }

        // M4: 回收不再引用的旧页面的 RenderNode/Picture 资源
        if (changed) {
            val oldCurrent = currentPage
            val oldNext = nextPage
            val oldPrev = prevPage
            val newPages = setOfNotNull<Any>(page, next, prev)
            if (oldCurrent != null && oldCurrent !== page && oldCurrent !in newPages) {
                oldCurrent.recycleRecorders()
            }
            if (oldNext != null && oldNext !== next && oldNext !in newPages) {
                oldNext.recycleRecorders()
            }
            if (oldPrev != null && oldPrev !== prev && oldPrev !in newPages) {
                oldPrev.recycleRecorders()
            }
        }

        currentPage = page
        when (mode) {
            PageRenderMode.SEQUENTIAL -> {
                nextPage = next
                prevPage = prev
            }
            PageRenderMode.JUMP, PageRenderMode.SCRUBBING -> {
                nextPage = null
                prevPage = null
                pageDelegate?.abort() // 取消进行中的动画
            }
        }

        // 页面引用已切换，结束 SETTLING 状态，恢复正常 IDLE 渲染
        pageDelegate?.confirmPageSettled()

        if (changed) {
            renderContext.selectedRange = null
        }

        submitRenderTask()
        invalidate()
    }

    /** 缓存动画禁用检测结果，避免每次 setPageDelegate 查询 ContentProvider */
    private var animationDisabledCache: Boolean? = null

    private fun isAnimationDisabled(): Boolean {
        return animationDisabledCache ?: run {
            val disabled = try {
                android.provider.Settings.Global.getFloat(
                    context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
                ) == 0f
            } catch (e: Exception) {
                false
            }
            animationDisabledCache = disabled
            disabled
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animationDisabledCache = null // 重新检测
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
                // 同步旋转页面引用（如 Legado 的 fillPage），
                // 确保后续手势立即操作新页面，不等 Compose recomposition
                fillPage(direction)
                // 触发 ViewModel 更新 pageIndex
                onPageChanged?.invoke(direction)
                // 再获取新页眉页脚槽位（此时 pageIndex 已更新）
                onPageChangedSlots?.invoke()?.let { (h, f) ->
                    if (renderContext.headerSlots != h || renderContext.footerSlots != f) {
                        renderContext.headerSlots = h
                        renderContext.footerSlots = f
                        invalidateAllRecorders()
                    }
                }
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
        currentPage?.invalidate()
        invalidate()
    }

    /**
     * 设置页脚文本
     */
    fun setFooterText(text: String) {
        if (renderContext.footerText == text) return
        renderContext.footerText = text
        currentPage?.invalidate()
        invalidate()
    }

    /**
     * 设置页眉多槽位内容
     */
    fun setHeaderSlots(slots: SlotResolution) {
        updateRenderProperty(renderContext.headerSlots != slots) {
            renderContext.headerSlots = slots
        }
    }

    /**
     * 设置页脚多槽位内容
     */
    fun setFooterSlots(slots: SlotResolution) {
        updateRenderProperty(renderContext.footerSlots != slots) {
            renderContext.footerSlots = slots
        }
    }

    /**
     * 批量更新页眉页脚相关参数（仅触发一次重绘）
     */
    fun updateHeaderFooter(
        headerSlots: SlotResolution,
        footerSlots: SlotResolution,
        alpha: Float,
        showProgress: Boolean,
    ) {
        val changed = renderContext.headerSlots != headerSlots
                || renderContext.footerSlots != footerSlots
                || renderContext.headerAlpha != alpha
                || renderContext.footerAlpha != alpha
                || renderContext.showProgress != showProgress
        if (!changed) return

        renderContext.headerSlots = headerSlots
        renderContext.footerSlots = footerSlots
        renderContext.headerAlpha = alpha
        renderContext.footerAlpha = alpha
        renderContext.showProgress = showProgress
        invalidateAllRecorders()
        submitRenderTask()
        invalidate()
    }

    /**
     * 设置是否显示进度条
     */
    fun setShowProgress(show: Boolean) {
        if (renderContext.showProgress == show) return
        renderContext.showProgress = show
    }

    /**
     * 设置页眉页脚透明度
     */
    fun setHeaderFooterAlpha(alpha: Float) {
        if (renderContext.headerAlpha == alpha && renderContext.footerAlpha == alpha) return
        renderContext.headerAlpha = alpha
        renderContext.footerAlpha = alpha
    }

    fun setTextSizePx(textSize: Float) {
        updateRenderProperty(textPaint.textSize != textSize) {
            textPaint.textSize = textSize
            headerPaint.textSize = textSize * HEADER_TEXT_RATIO
            footerPaint.textSize = textSize * FOOTER_TEXT_RATIO
        }
    }

    /**
     * 设置字距（em 单位，Paint.letterSpacing 接受 em）
     */
    fun setLetterSpacing(emSpacing: Float) {
        updateRenderProperty(textPaint.letterSpacing != emSpacing) {
            textPaint.letterSpacing = emSpacing
        }
    }

    /**
     * 设置字重（FakeBold 模式）
     */
    fun setFakeBoldText(fakeBold: Boolean) {
        updateRenderProperty(textPaint.isFakeBoldText != fakeBold) {
            textPaint.isFakeBoldText = fakeBold
        }
    }

    /**
     * 设置文本对齐方式
     */
    fun setTextAlign(align: com.shuli.reader.core.data.ReaderTextAlign) {
        pageRenderer.setTextAlign(align)
        invalidateAllRecorders()
        submitRenderTask()
        invalidate()
    }

    /**
     * 设置标题样式
     */
    fun setTitleStyle(style: com.shuli.reader.core.reader.TitleStyleConfig) {
        pageRenderer.setTitleStyle(style)
    }

    /** 缓存当前 fontKey，避免重复加载字体。空串表示尚未设置，首次调用必定执行 */
    private var currentFontKey: String = ""

    /**
     * 设置阅读字体
     * - "system" = 系统默认
     * - "harmony" = 鸿蒙黑体（内置）
     * - "custom:{id}" = 用户导入的字体
     */
    fun setFontFamily(fontKey: String) {
        if (fontKey == currentFontKey) return
        currentFontKey = fontKey
        val typeface = when {
            fontKey == FontManager.KEY_SYSTEM -> Typeface.DEFAULT
            fontKey == FontManager.KEY_HARMONY -> try {
                ResourcesCompat.getFont(context, R.font.harmonyos_sanssc_regular)
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
            FontManager.isCustomFont(fontKey) -> fontManager.loadTypeface(fontKey) ?: Typeface.DEFAULT
            else -> Typeface.DEFAULT
        }
        if (textPaint.typeface == typeface) return
        textPaint.typeface = typeface
        invalidateAllRecorders()
        submitRenderTask()
        invalidate()
    }

    fun clearSelection() {
        if (renderContext.selectedRange == null) return
        renderContext.selectedRange = null
        currentPage?.invalidate()
        invalidate()
    }

    fun setTtsActiveRange(range: SelectionRange?) {
        if (renderContext.ttsActiveRange == range) return
        renderContext.ttsActiveRange = range
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
        submitRenderTask()
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

    /** 边缘翻页开关 */
    private var edgeTurnPageEnabled = true

    /** 设置边缘翻页是否启用 */
    fun setEdgeTurnPageEnabled(enabled: Boolean) {
        edgeTurnPageEnabled = enabled
    }

    /**
     * 同步旋转页面引用（如 Legado 的 fillPage）。
     * 在动画完成/中断提交后调用，确保后续绘制和手势操作正确的页面。
     */
    private fun fillPage(direction: PageDelegate.Direction) {
        when (direction) {
            PageDelegate.Direction.NEXT -> {
                prevPage = currentPage
                currentPage = nextPage
                nextPage = null
            }
            PageDelegate.Direction.PREV -> {
                nextPage = currentPage
                currentPage = prevPage
                prevPage = null
            }
            PageDelegate.Direction.NONE -> {}
        }
    }

    /** 使所有页面 recorder 失效（字体/主题/尺寸等全局变化时使用） */
    private fun invalidateAllRecorders() {
        currentPage?.invalidateAll()
        nextPage?.invalidateAll()
        prevPage?.invalidateAll()
    }

    /**
     * 排版变化 crossfade：将当前页面快照为 Bitmap，启动 alpha 过渡动画。
     * 旧页面从 alpha 1→0 淡出，新页面从底层显示。
     */
    private fun startLayoutCrossfade() {
        val w = width
        val h = height
        val cur = currentPage
        if (w <= 0 || h <= 0 || cur == null) return

        // 捕获旧页面为 Bitmap
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val captureCanvas = Canvas(bitmap)
        cur.canvasRecorder.draw(captureCanvas)

        // 清理旧资源
        crossfadeAnimator?.cancel()
        oldPageBitmap?.recycle()

        oldPageBitmap = bitmap
        crossfadeAlpha = 1f

        crossfadeAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                crossfadeAlpha = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    oldPageBitmap?.recycle()
                    oldPageBitmap = null
                    crossfadeAlpha = 0f
                }
            })
            start()
        }
    }

    /**
     * 更新渲染属性的公共模板
     *
     * 消除 setTextSizePx / setLetterSpacing / setFakeBoldText 等 setter 中
     * "if (same) return → update → invalidateAllRecorders → submitRenderTask → invalidate" 样板代码。
     *
     * @param changed 值是否发生变化（调用方负责比较）
     * @param apply 实际更新属性的 lambda
     */
    private inline fun updateRenderProperty(changed: Boolean, apply: () -> Unit) {
        if (!changed) return
        apply()
        invalidateAllRecorders()
        submitRenderTask()
        invalidate()
    }

    // --- 触摸状态 ---
    private var touchDownX: Float = 0f
    private var touchDownY: Float = 0f
    private var touchMoved: Boolean = false  // 是否超过 slop 阈值
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val slopSquare = touchSlop * touchSlop

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isTextSelectionGesture) {
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                isTextSelectionGesture = false
            }
            return true
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val delegate = pageDelegate

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchMoved = false

                // 边缘区域：直接交给 delegate 开始拖拽
                val isEdge = event.x <= w / 3f || event.x >= w * 2f / 3f
                if (isEdge && delegate != null) {
                    delegate.onTouch(event)
                    return true
                }
                // 中心区域：让 gestureDetector 初始化（后续判断 tap/longPress）
                gestureDetector.onTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!touchMoved) {
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    val distSq = dx * dx + dy * dy
                    if (distSq > slopSquare) {
                        touchMoved = true
                    }
                }

                // 超过 slop 且从边缘开始：交给 delegate 处理拖拽
                if (touchMoved) {
                    val isEdgeStart = touchDownX <= w / 3f || touchDownX >= w * 2f / 3f
                    if (isEdgeStart && delegate != null) {
                        delegate.onTouch(event)
                        return true
                    }
                }
                // 未超过 slop 或从中心开始：交给 gestureDetector
                gestureDetector.onTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val isEdgeStart = touchDownX <= w / 3f || touchDownX >= w * 2f / 3f
                val wasMoved = touchMoved
                touchMoved = false

                if (wasMoved && isEdgeStart && delegate != null) {
                    // 边缘拖拽结束：delegate 处理翻页动画
                    delegate.onTouch(event)
                    return true
                }

                // 未移动或从中心开始：交给 gestureDetector 判断 tap
                gestureDetector.onTouchEvent(event)

                // 手动处理边缘点击翻页（gestureDetector 仅处理中心区域）
                if (!wasMoved && isEdgeStart && edgeTurnPageEnabled) {
                    val isCenter = touchDownX > w / 3f && touchDownX < w * 2f / 3f &&
                        touchDownY > h / 3f && touchDownY < h * 2f / 3f
                    if (!isCenter) {
                        if (touchDownX <= w / 3f) {
                            delegate?.startPrev() ?: onPageChanged?.invoke(PageDelegate.Direction.PREV)
                        } else {
                            delegate?.startNext() ?: onPageChanged?.invoke(PageDelegate.Direction.NEXT)
                        }
                        return true
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) {
            invalidateAllRecorders()
            submitRenderTask()
        }
    }

    override fun onDetachedFromWindow() {
        crossfadeAnimator?.cancel()
        oldPageBitmap?.recycle()
        oldPageBitmap = null
        currentPage?.recycleRecorders()
        nextPage?.recycleRecorders()
        prevPage?.recycleRecorders()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = currentPage ?: return

        // 兜底：若后台尚未录制完成（首帧），主线程同步录制
        if (current.canvasRecorder.needRecord()) {
            recordPage(current)
        }

        val delegate = pageDelegate
        if (delegate != null) {
            val isPrevDirection = when (delegate.state) {
                PageDelegate.State.DRAGGING -> delegate.isDraggingBackward()
                PageDelegate.State.ANIMATING -> delegate.direction == PageDelegate.Direction.PREV
                PageDelegate.State.SETTLING -> delegate.direction == PageDelegate.Direction.PREV
                PageDelegate.State.IDLE -> false
            }
            val target = if (isPrevDirection) prevPage else nextPage
            target?.let {
                if (it.canvasRecorder.needRecord()) recordPage(it)
            }
            delegate.onDraw(canvas, current.canvasRecorder, target?.canvasRecorder ?: current.canvasRecorder)
        } else {
            current.canvasRecorder.draw(canvas)
        }

        // 排版变化 crossfade：叠加旧页面快照（alpha 从 1→0）
        oldPageBitmap?.let { bitmap ->
            if (crossfadeAlpha > 0f) {
                crossfadePaint.alpha = (crossfadeAlpha * 255).toInt()
                canvas.drawBitmap(bitmap, 0f, 0f, crossfadePaint)
            }
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
        /** 后台预渲染线程，单线程，优先级略低于主线程 */
        private val renderThread by lazy {
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "ShuLi-PageRender").apply { priority = Thread.NORM_PRIORITY - 1 }
            }
        }

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
