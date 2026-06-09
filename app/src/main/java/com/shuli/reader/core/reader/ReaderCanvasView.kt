package com.shuli.reader.core.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.reader.animation.PageDelegate
import com.shuli.reader.core.reader.canvas.CanvasTextSelection
import com.shuli.reader.core.reader.canvas.CanvasTouchHandler
import com.shuli.reader.core.reader.canvas.PageBitmapCache
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme

/**
 * 阅读器 Canvas 视图
 *
 * 渲染架构：每页持有独立的 CanvasRecorder（RenderNode/Picture），
 * 选区/TTS 高亮变化仅 invalidate recorder 而非重绘 Bitmap。
 *
 * SRP 拆分后职责：字段声明、生命周期、onDraw 编排。
 * 视觉参数委托给 [CanvasVisualParamsManager]，
 * 触摸手势委托给 [CanvasTouchHandler]，
 * 文本选区委托给 [CanvasTextSelection]。
 */
class ReaderCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), RenderApplierTarget {
    private val fontManager = FontManager(context)
    private val defaultColors = ReaderTheme.PAPER.toReaderColorScheme().toCanvasThemeColors()

    // 画笔
    private val backgroundPaint = Paint().apply {
        color = defaultColors.backgroundColor
        style = Paint.Style.FILL
    }

    val textPaint = Paint().apply {
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

    // 章节文本
    private var chapterContent: CharSequence = ""

    // 排版变化 crossfade 动画
    private var oldPageBitmap: Bitmap? = null
    private var crossfadeAnimator: ValueAnimator? = null
    private var crossfadeAlpha: Float = 0f

    // 渲染参数
    private val renderContext = RenderContext()

    private val pageRenderer = ReaderPageRenderer(textPaint, headerPaint, footerPaint, progressPaint)

    // ── 拆分委托 ──────────────────────────────────────────────

    private val pageBitmapCache = PageBitmapCache(pageRenderer)

    private val textSelection = CanvasTextSelection()

    /** 视觉参数管理器（从 ReaderCanvasView 拆出，SRP） */
    private val visualParams = CanvasVisualParamsManager(
        textPaint = textPaint,
        headerPaint = headerPaint,
        footerPaint = footerPaint,
        backgroundPaint = backgroundPaint,
        progressPaint = progressPaint,
        selectionPaint = selectionPaint,
        ttsHighlightPaint = ttsHighlightPaint,
        renderContext = renderContext,
        pageRenderer = pageRenderer,
        fontManager = fontManager,
        onInvalidate = { invalidate() },
        onSubmitRenderTask = { submitRenderTask() },
        onPagesInvalidate = {
            // TTS/选区/笔记变化仅失效 overlay 层，正文不重录（§10 分层 recorder）
            currentPage?.invalidateOverlay()
            nextPage?.invalidateOverlay()
            prevPage?.invalidateOverlay()
        },
    )

    private val touchHandler: CanvasTouchHandler = CanvasTouchHandler(context).apply {
        callbacks = object : CanvasTouchHandler.Callbacks {
            override fun getWidth() = this@ReaderCanvasView.width.toFloat()
            override fun getHeight() = this@ReaderCanvasView.height.toFloat()
            override fun getPageDelegate() = pageDelegate
            override fun isEdgeTurnPageEnabled() = visualParams.isEdgeTurnPageEnabled()
            override fun getEdgeWidthPercent() = visualParams.getEdgeWidthPercent()
            override fun getLeftZoneRatio() = this@ReaderCanvasView.leftZoneRatio
            override fun onPageChanged(direction: PageDelegate.Direction) {
                this@ReaderCanvasView.onPageChanged?.invoke(direction)
            }
            override fun onCenterClicked() {
                this@ReaderCanvasView.onCenterClicked?.invoke()
            }
            override fun onLongPress(x: Float, y: Float) {
                pageDelegate?.abort()
                val page = currentPage ?: return
                val range = textSelection.selectLineAt(x, y, page, chapterContent, width.toFloat())
                if (range != null) {
                    renderContext.selectedRange = range
                    beginTextSelection()
                    page.invalidate()
                    invalidate()
                    onTextSelected?.invoke(range)
                }
            }
        }
    }

    // ── 回调 ──────────────────────────────────────────────────

    internal fun setBatteryLevel(level: Int) {
        if (renderContext.batteryLevel == level) return
        renderContext.batteryLevel = level
        currentPage?.invalidateShell()
        nextPage?.invalidateShell()
        prevPage?.invalidateShell()
        submitRenderTask()
        invalidate()
    }

    // 翻页动画委托
    private var pageDelegate: PageDelegate? = null

    // 翻页回调
    var onPageChanged: ((PageDelegate.Direction) -> Unit)? = null

    // 边界检测回调
    var canTurnPrev: (() -> Boolean)? = null
    var canTurnNext: (() -> Boolean)? = null

    // 文本选区回调
    var onTextSelected: ((SelectionRange) -> Unit)? = null

    // 中心区域点击回调
    var onCenterClicked: (() -> Unit)? = null

    // 触控热区比例（左侧区域宽度比例，右侧对称）
    var leftZoneRatio: Float = 0.33f

    // ── 页面录制委托 ──────────────────────────────────────────

    override fun submitRenderTask() {
        pageBitmapCache.submitRenderTask(
            width = width,
            height = height,
            currentPage = currentPage,
            nextPage = nextPage,
            prevPage = prevPage,
            content = chapterContent,
            renderContext = renderContext,
            backgroundPaint = backgroundPaint,
            textPaint = textPaint,
            ttsHighlightPaint = ttsHighlightPaint,
            selectionPaint = selectionPaint,
            postInvalidate = { postInvalidate() },
        )
    }

    // ── RenderApplierTarget：scope-only invalidation ─────────────

    override fun invalidateContentOnly() {
        currentPage?.invalidate()
        nextPage?.invalidate()
        prevPage?.invalidate()
    }

    override fun invalidateShellOnly() {
        currentPage?.invalidateShell()
        nextPage?.invalidateShell()
        prevPage?.invalidateShell()
    }

    override fun invalidateOverlayOnly() {
        currentPage?.invalidateOverlay()
        nextPage?.invalidateOverlay()
        prevPage?.invalidateOverlay()
    }

    override fun invalidateAllPages() {
        currentPage?.invalidateAll()
        nextPage?.invalidateAll()
        prevPage?.invalidateAll()
    }

    override fun rebuildPageDelegate() {
        // pageDelegate 已由 applySnapshot 在进入 Applier 之前通过 setPageDelegate 设置；
        // 此方法仅作语义标记，使测试可验证 PAGE_DELEGATE scope 分发。
    }

    // ── 页面设置 ──────────────────────────────────────────────

    override fun setPage(
        page: TextPage,
        next: TextPage?,
        prev: TextPage?,
        mode: PageRenderMode,
    ) {
        setPageInternal(page, next, prev, "", mode, isLayoutChange = false)
    }

    internal fun setPage(
        page: TextPage,
        next: TextPage? = null,
        prev: TextPage? = null,
        content: CharSequence = "",
        mode: PageRenderMode = PageRenderMode.SEQUENTIAL,
        isLayoutChange: Boolean = false,
    ) {
        setPageInternal(page, next, prev, content, mode, isLayoutChange)
    }

    private fun setPageInternal(
        page: TextPage,
        next: TextPage?,
        prev: TextPage?,
        content: CharSequence,
        mode: PageRenderMode,
        isLayoutChange: Boolean,
    ) {
        val changed = currentPage !== page || nextPage !== next || prevPage !== prev

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
        chapterContent = content
        when (mode) {
            PageRenderMode.SEQUENTIAL -> {
                nextPage = next
                prevPage = prev
            }
            PageRenderMode.JUMP, PageRenderMode.SCRUBBING -> {
                nextPage = null
                prevPage = null
                pageDelegate?.abort()
            }
        }

        pageDelegate?.confirmPageSettled()

        if (changed) {
            renderContext.selectedRange = null
            submitRenderTask()
            invalidate()
        }
    }

    // ── Snapshot 应用入口（Orchestrator 唯一调用） ──────────────

    override fun applySnapshot(
        snapshot: Any,
        diff: Any,
        pageDelegate: com.shuli.reader.core.reader.animation.PageDelegate?,
    ) {
        val renderSnapshot = snapshot as com.shuli.reader.feature.reader.render.ReaderRenderSnapshot
        val renderDiff = diff as com.shuli.reader.feature.reader.render.ReaderRenderDiff
        applySnapshotInternal(renderSnapshot, renderDiff, pageDelegate)
    }

    /**
     * 唯一渲染入口。先应用全部视觉参数（幂等），再按 diff 范围精确失效。
     *
     * 视觉参数从 [com.shuli.reader.feature.reader.render.ReaderRenderSnapshot] 的
     * `layout.input`、`visual`、`shell` 子快照读取，不依赖任何 `settings` 中间字段。
     *
     * @param snapshot 完整不可变快照
     * @param diff 失效范围集合
     * @param pageDelegate 翻页动画委托（可为 null）
     */
    fun applySnapshot(
        snapshot: com.shuli.reader.feature.reader.render.ReaderRenderSnapshot,
        diff: com.shuli.reader.feature.reader.render.ReaderRenderDiff,
        pageDelegate: com.shuli.reader.core.reader.animation.PageDelegate? = null,
    ) {
        applySnapshotInternal(snapshot, diff, pageDelegate)
    }

    private fun applySnapshotInternal(
        snapshot: com.shuli.reader.feature.reader.render.ReaderRenderSnapshot,
        diff: com.shuli.reader.feature.reader.render.ReaderRenderDiff,
        pageDelegate: com.shuli.reader.core.reader.animation.PageDelegate? = null,
    ) {
        val layoutInput = snapshot.layout.input
        val v = snapshot.visual
        val sh = snapshot.shell

        // 1. 始终应用全部视觉参数（幂等操作，值未变时各 setter 内部跳过）
        visualParams.updatePaintSnapshot(
            textSize = layoutInput.fontSizeSp * layoutInput.density,
            letterSpacing = layoutInput.letterSpacing,
            fakeBold = layoutInput.fontWeight == com.shuli.reader.core.data.ReaderFontWeight.BOLD,
            fontKey = layoutInput.fontKey,
            textAlign = v.textAlign,
            invalidateContent = false,
        )
        visualParams.setHeaderTextRatio(sh.headerFontSizeRatio)
        visualParams.setFooterTextRatio(sh.footerFontSizeRatio)
        visualParams.updateHeaderFooter(
            headerSlots = sh.headerSlots,
            footerSlots = sh.footerSlots,
            alpha = sh.headerFooterAlpha,
            showProgress = sh.showProgress,
            showHeaderLine = sh.showHeaderLine,
            showFooterLine = sh.showFooterLine,
        )
        visualParams.setTitleStyle(v.titleStyle)
        visualParams.setEdgeTurnPageEnabled(sh.edgeTurnPage)
        visualParams.setEdgeWidthPercent(sh.edgeWidthPercent)
        this.leftZoneRatio = sh.leftZoneRatio
        visualParams.setThemeColors(v.themeColors)
        if (pageDelegate != null) {
            setPageDelegate(pageDelegate)
        }

        // 2. 按 diff 范围精确失效：委托给 ReaderCanvasStateApplier
        com.shuli.reader.feature.reader.render.ReaderCanvasStateApplier()
            .apply(this, snapshot, diff)
        invalidate()
    }

    // ── 翻页动画委托 ──────────────────────────────────────────

    private var animationDisabledCache: Boolean? = null

    private fun isAnimationDisabled(): Boolean {
        return animationDisabledCache ?: run {
            val disabled = try {
                android.provider.Settings.Global.getFloat(
                    context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f,
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
        animationDisabledCache = null
    }

    internal fun setPageDelegate(delegate: PageDelegate?) {
        val actualDelegate = if (isAnimationDisabled() && delegate != null && delegate !is com.shuli.reader.core.reader.animation.NoAnimPageDelegate) {
            com.shuli.reader.core.reader.animation.NoAnimPageDelegate()
        } else {
            delegate
        }

        pageDelegate = actualDelegate
        actualDelegate?.setCallback(object : PageDelegate.Callback {
            override fun onPageChanged(direction: PageDelegate.Direction) {
                fillPage(direction)
                onPageChanged?.invoke(direction)
            }

            override fun invalidate() {
                this@ReaderCanvasView.invalidate()
            }
        })
    }

    // ── 视觉参数委托（CanvasVisualParamsManager） ────────────

    internal fun setHeaderText(text: String) = visualParams.setHeaderText(text)
    internal fun setFooterText(text: String) = visualParams.setFooterText(text)
    internal fun setHeaderSlots(slots: SlotResolution) = visualParams.setHeaderSlots(slots)
    internal fun setFooterSlots(slots: SlotResolution) = visualParams.setFooterSlots(slots)

    internal fun updateHeaderFooter(
        headerSlots: SlotResolution,
        footerSlots: SlotResolution,
        alpha: Float,
        showProgress: Boolean,
        showHeaderLine: Boolean = false,
        showFooterLine: Boolean = false,
    ) = visualParams.updateHeaderFooter(headerSlots, footerSlots, alpha, showProgress, showHeaderLine, showFooterLine)

    internal fun setShowProgress(show: Boolean) = visualParams.setShowProgress(show)
    internal fun setHeaderFooterAlpha(alpha: Float) = visualParams.setHeaderFooterAlpha(alpha)
    internal fun setTextSizePx(textSize: Float) = visualParams.setTextSizePx(textSize)
    internal fun setLetterSpacing(emSpacing: Float) = visualParams.setLetterSpacing(emSpacing)
    internal fun setFakeBoldText(fakeBold: Boolean) = visualParams.setFakeBoldText(fakeBold)
    internal fun setTextAlign(align: com.shuli.reader.core.data.ReaderTextAlign) = visualParams.setTextAlign(align)
    internal fun setTitleStyle(style: TitleStyleConfig) = visualParams.setTitleStyle(style)
    internal fun setFontFamily(fontKey: String) = visualParams.setFontFamily(fontKey)

    internal fun updatePaintSnapshot(
        textSize: Float? = null,
        letterSpacing: Float? = null,
        fakeBold: Boolean? = null,
        fontKey: String? = null,
        textAlign: com.shuli.reader.core.data.ReaderTextAlign? = null,
        invalidateContent: Boolean = false,
    ) = visualParams.updatePaintSnapshot(textSize, letterSpacing, fakeBold, fontKey, textAlign, invalidateContent)

    internal fun clearSelection() = visualParams.clearSelection()
    internal fun setTtsActiveRange(range: SelectionRange?) = visualParams.setTtsActiveRange(range)
    internal fun setNoteRanges(ranges: List<Pair<SelectionRange, String?>>) = visualParams.setNoteRanges(ranges)

    internal fun setTheme(
        backgroundColor: Int,
        textColor: Int,
        headerColor: Int,
        footerColor: Int,
        progressColor: Int,
    ) {
        visualParams.setTheme(backgroundColor, textColor, headerColor, footerColor, progressColor)
        pageBitmapCache.invalidateAllRecorders(currentPage, nextPage, prevPage)
    }

    internal fun setThemeColors(colors: ThemeColors) = visualParams.setThemeColors(colors)

    internal fun setEdgeTurnPageEnabled(enabled: Boolean) = visualParams.setEdgeTurnPageEnabled(enabled)
    internal fun setEdgeWidthPercent(percent: Float) = visualParams.setEdgeWidthPercent(percent)
    internal fun setHeaderTextRatio(ratio: Float) = visualParams.setHeaderTextRatio(ratio)
    internal fun setFooterTextRatio(ratio: Float) = visualParams.setFooterTextRatio(ratio)

    // ── 页面旋转 / crossfade ──────────────────────────────────

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

    private fun startLayoutCrossfade() {
        val w = width
        val h = height
        val cur = currentPage
        if (w <= 0 || h <= 0 || cur == null) return

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val captureCanvas = Canvas(bitmap)
        cur.canvasRecorder.draw(captureCanvas)

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

    // ── 触摸事件委托 ──────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchHandler.onTouchEvent(event) || super.onTouchEvent(event)
    }

    // ── 生命周期 / 绘制 ──────────────────────────────────────

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) {
            pageBitmapCache.invalidateAllRecorders(currentPage, nextPage, prevPage)
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

        if (current.canvasRecorder.needRecord() || current.shellRecorder.needRecord()) {
            pageBitmapCache.recordPage(
                current, width, height, chapterContent, renderContext,
                backgroundPaint, textPaint, ttsHighlightPaint, selectionPaint,
            )
        }

        val delegate = pageDelegate
        if (delegate != null && delegate.state != PageDelegate.State.IDLE) {
            val isPrevDirection = when (delegate.state) {
                PageDelegate.State.DRAGGING -> delegate.isDraggingBackward()
                PageDelegate.State.ANIMATING -> delegate.direction == PageDelegate.Direction.PREV
                PageDelegate.State.SETTLING -> delegate.direction == PageDelegate.Direction.PREV
                PageDelegate.State.IDLE -> false
            }
            val target = if (isPrevDirection) prevPage else nextPage
            target?.let {
                if (it.canvasRecorder.needRecord() || it.shellRecorder.needRecord()) {
                    pageBitmapCache.recordPage(
                        it, width, height, chapterContent, renderContext,
                        backgroundPaint, textPaint, ttsHighlightPaint, selectionPaint,
                    )
                }
                it.recordComposite(width, height)
            }
            current.recordComposite(width, height)
            delegate.onDraw(
                canvas,
                current.compositeRecorder,
                target?.compositeRecorder ?: current.compositeRecorder,
            )
        } else {
            current.shellRecorder.draw(canvas)
            current.canvasRecorder.draw(canvas)
        }

        oldPageBitmap?.let { bitmap ->
            if (crossfadeAlpha > 0f) {
                crossfadePaint.alpha = (crossfadeAlpha * 255).toInt()
                canvas.drawBitmap(bitmap, 0f, 0f, crossfadePaint)
            }
        }
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }

    private companion object {
        private const val SELECTION_ALPHA = 0x33
        private const val TTS_HIGHLIGHT_ALPHA = 0x24
    }
}
