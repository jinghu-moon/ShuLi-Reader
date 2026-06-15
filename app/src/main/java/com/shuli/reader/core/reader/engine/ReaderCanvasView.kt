package com.shuli.reader.core.reader.engine

import com.shuli.reader.core.reader.model.SlotResolution
import com.shuli.reader.core.reader.model.TitleStyleConfig
import com.shuli.reader.core.reader.engine.input.CanvasTouchHandler
import com.shuli.reader.core.reader.engine.selection.CanvasTextSelection
import com.shuli.reader.core.reader.engine.cache.PageBitmapCache
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.reader.engine.animation.PageDelegate
import com.shuli.reader.feature.reader.settings.GestureAction
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.feature.reader.render.colorTemperatureToRgb
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme

/**
 * 阅读器 Canvas 视图
 *
 * 渲染架构：每页持有独立的 CanvasRecorder（RenderNode/Picture），
 * 选区高亮变化仅 invalidate recorder 而非重绘 Bitmap。
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

    private val crossfadePaint = Paint().apply {
        isAntiAlias = true
    }

    // ── VIEW_INVALIDATE overlay state（§1.4.1，不进 recorder）──
    private var colorTemperature: Float = 6500f

    private val colorTempPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // 页面引用
    private var currentPage: TextPage? = null
    private var nextPage: TextPage? = null
    private var prevPage: TextPage? = null

    // 章节文本
    private var chapterContent: CharSequence = ""
    /** chapterContent 来自哪一章。setPageInternal 用它检测跨章切换。 */
    private var chapterContentChapterIndex: Int = -1
    /** 当前及相邻章节正文。录制任意 page 时必须按 page.chapterIndex 取正文。 */
    private val chapterContentsByIndex = mutableMapOf<Int, CharSequence>()

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
            override fun getGestureConfig() = this@ReaderCanvasView.gestureConfig
            override fun onAction(action: GestureAction, x: Float, y: Float) {
                this@ReaderCanvasView.onGestureAction?.invoke(action)
            }
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

    internal fun setColorTemperature(temperature: Float) {
        if (colorTemperature == temperature) return
        colorTemperature = temperature
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

    // 触控区域手势配置（v5.1）
    var gestureConfig: GestureConfig = GestureConfig()

    // 触控区域动作回调（v5.1）：将 GestureAction 上抛给上层映射为 ReaderIntent
    var onGestureAction: ((GestureAction) -> Unit)? = null

    // ── 页面录制委托 ──────────────────────────────────────────

    override fun submitRenderTask() {
        pageBitmapCache.submitRenderTask(
            width = width,
            height = height,
            currentPage = currentPage,
            nextPage = nextPage,
            prevPage = prevPage,
            chapterContents = snapshotChapterContents(),
            renderContext = renderContext,
            backgroundPaint = backgroundPaint,
            textPaint = textPaint,
            selectionPaint = selectionPaint,
            postInvalidate = { postInvalidate() },
        )
    }

    private fun snapshotChapterContents(): Map<Int, CharSequence> {
        val snapshot = chapterContentsByIndex.toMutableMap()
        if (chapterContentChapterIndex >= 0) {
            snapshot[chapterContentChapterIndex] = chapterContent
        }
        return snapshot
    }

    private fun contentForPage(page: TextPage): CharSequence? {
        return chapterContentsByIndex[page.chapterIndex]
            ?: chapterContent.takeIf { chapterContentChapterIndex == page.chapterIndex }
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

        // M4+: 跨章检测 —— 新 page 来自不同章节时，当前 chapterContent 是旧章正文。
        // 此时若让新 page 的 canvasRecorder 直接 draw，会画出 PicturePool 里残留的旧内容
        // （典型现象：翻到下一章首页瞬间闪现上一章第一页的内容）。强制 invalidate 让
        // recorder 进入「脏」状态，下一帧 applySnapshot 写入新章内容后才会重录。
        val previousChapterIdx = currentPage?.chapterIndex ?: -1
        val incomingChapterIdx = page.chapterIndex
        val chapterSwitched = previousChapterIdx != incomingChapterIdx

        currentPage = page
        // content 为空表示「沿用已就位的章节文本」：RenderApplierTarget.setPage 不携带 content，
        // 真实章节文本由 applySnapshot 经 snapshot.chapterContent 写入。若用空串覆盖，会导致首页在
        // 文本缺失时录制出空白正文（recordIfNeeded 录制后不再刷新），而分页 offset 已推进，
        // 造成「首页只剩标题、次页缺章节开头」。
        if (content.isNotEmpty()) {
            chapterContent = content
            chapterContentChapterIndex = incomingChapterIdx
            chapterContentsByIndex[incomingChapterIdx] = content
        }

        if (chapterSwitched && chapterContentChapterIndex != incomingChapterIdx) {
            // 新章 content 尚未就位（applySnapshot 还没跑），先把 recorder 标脏，
            // 阻止 draw 时使用 PicturePool 残留的旧 Picture。
            if (com.shuli.reader.BuildConfig.DEBUG) {
                android.util.Log.d(
                    "ReaderCanvasView",
                    "chapterSwitch invalidate: prev=$previousChapterIdx incoming=$incomingChapterIdx contentChapter=$chapterContentChapterIndex",
                )
            }
            page.canvasRecorder.invalidate()
            page.shellRecorder.invalidate()
            page.compositeRecorder.invalidate()
        }
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
        pageDelegate: com.shuli.reader.core.reader.engine.animation.PageDelegate?,
        chapterContent: CharSequence,
        chapterContents: Map<Int, CharSequence>,
    ) {
        val renderSnapshot = snapshot as com.shuli.reader.feature.reader.render.ReaderRenderSnapshot
        val renderDiff = diff as com.shuli.reader.feature.reader.render.ReaderRenderDiff
        applySnapshotInternal(renderSnapshot, renderDiff, pageDelegate, chapterContent, chapterContents)
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
        pageDelegate: com.shuli.reader.core.reader.engine.animation.PageDelegate? = null,
        chapterContent: CharSequence = "",
        chapterContents: Map<Int, CharSequence> = emptyMap(),
    ) {
        applySnapshotInternal(snapshot, diff, pageDelegate, chapterContent, chapterContents)
    }

    private fun applySnapshotInternal(
        snapshot: com.shuli.reader.feature.reader.render.ReaderRenderSnapshot,
        diff: com.shuli.reader.feature.reader.render.ReaderRenderDiff,
        pageDelegate: com.shuli.reader.core.reader.engine.animation.PageDelegate? = null,
        chapterContent: CharSequence = "",
        chapterContents: Map<Int, CharSequence> = emptyMap(),
    ) {
        // 章节正文经 applySnapshot 独立参数传入（不进 snapshot，避免 O(n) equals，见 docs/26 §7）。
        // 空串表示「沿用已就位文本」：纯视觉刷新（章节未变）无需重传正文，不得用空串覆盖。
        if (chapterContents.isNotEmpty()) {
            chapterContentsByIndex.clear()
            chapterContentsByIndex.putAll(chapterContents)
        }
        if (chapterContent.isNotEmpty()) {
            this.chapterContent = chapterContent
            this.chapterContentChapterIndex = snapshot.page.chapterIndex
            chapterContentsByIndex[snapshot.page.chapterIndex] = chapterContent
        }

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
        this.gestureConfig = sh.gestureConfig
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
        val actualDelegate = if (isAnimationDisabled() && delegate != null && delegate !is com.shuli.reader.core.reader.engine.animation.NoAnimPageDelegate) {
            com.shuli.reader.core.reader.engine.animation.NoAnimPageDelegate()
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
        val beforeCurrent = currentPage
        when (direction) {
            PageDelegate.Direction.NEXT -> {
                prevPage = currentPage
                // 跨章翻页时 nextPage 可能还是 null（新章尚未加载完），保留 currentPage
                // 避免出现 currentPage = null 导致的空白帧与下游 NPE。
                val chosen = nextPage ?: currentPage
                currentPage = chosen
                nextPage = null
                if (com.shuli.reader.BuildConfig.DEBUG) {
                    android.util.Log.d(
                        "ReaderCanvasView",
                        "fillPage(NEXT): prev=${prevPage?.let { "[ch=${it.chapterIndex},pi=${it.pageIndex}]" }} " +
                            "beforeCurrent=${beforeCurrent?.let { "[ch=${it.chapterIndex},pi=${it.pageIndex}]" }} " +
                            "newCurrent=${chosen?.let { "[ch=${it.chapterIndex},pi=${it.pageIndex}]" }}",
                    )
                }
            }
            PageDelegate.Direction.PREV -> {
                nextPage = currentPage
                // 跨章翻页时 prevPage 可能还是 null，保留 currentPage。
                val chosen = prevPage ?: currentPage
                currentPage = chosen
                prevPage = null
                if (com.shuli.reader.BuildConfig.DEBUG) {
                    android.util.Log.d(
                        "ReaderCanvasView",
                        "fillPage(PREV): next=${nextPage?.let { "[ch=${it.chapterIndex},pi=${it.pageIndex}]" }} " +
                            "beforeCurrent=${beforeCurrent?.let { "[ch=${it.chapterIndex},pi=${it.pageIndex}]" }} " +
                            "newCurrent=${chosen?.let { "[ch=${it.chapterIndex},pi=${it.pageIndex}]" }}",
                    )
                }
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
        val current = currentPage
        if (current == null) {
            // C: currentPage 为空时（跨章翻页间隙、首次加载），用主题背景色整屏填充，
            // 避免空白帧 / 残留旧 bitmap 闪烁。后续 snapshot 应用后会正常绘制。
            canvas.drawColor(backgroundPaint.color)
            return
        }

        val currentContent = contentForPage(current)

        // 跨章硬闸：没有 currentPage 所属章节正文时，不能录制或播放 recorder。
        // 相邻章正文已就位时允许跨章动画完整绘制；否则只画背景等待 snapshot 更新。
        if (currentContent == null) {
            canvas.drawColor(backgroundPaint.color)
            if (com.shuli.reader.BuildConfig.DEBUG) {
                android.util.Log.d(
                    "ReaderCanvasView",
                    "onDraw skip: missing content currentChapter=${current.chapterIndex} pi=${current.pageIndex}",
                )
            }
            return
        }

        if (current.canvasRecorder.needRecord() || current.shellRecorder.needRecord()) {
            pageBitmapCache.recordPage(
                page = current,
                width = width,
                height = height,
                content = currentContent,
                contentChapterIndex = current.chapterIndex,
                renderContext = renderContext,
                backgroundPaint = backgroundPaint,
                textPaint = textPaint,
                selectionPaint = selectionPaint,
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
            val drawableTarget = target?.let { page ->
                contentForPage(page)?.let { content -> page to content }
            }
            if (target != null && drawableTarget == null) {
                target.invalidateAll()
                current.shellRecorder.draw(canvas)
                current.canvasRecorder.draw(canvas)
            } else {
                drawableTarget?.let { (targetPage, targetContent) ->
                    if (targetPage.canvasRecorder.needRecord() || targetPage.shellRecorder.needRecord()) {
                        pageBitmapCache.recordPage(
                            page = targetPage,
                            width = width,
                            height = height,
                            content = targetContent,
                            contentChapterIndex = targetPage.chapterIndex,
                            renderContext = renderContext,
                            backgroundPaint = backgroundPaint,
                            textPaint = textPaint,
                            selectionPaint = selectionPaint,
                        )
                    }
                    targetPage.recordComposite(width, height)
                }
                current.recordComposite(width, height)
                delegate.onDraw(
                    canvas,
                    current.compositeRecorder,
                    drawableTarget?.first?.compositeRecorder ?: current.compositeRecorder,
                )
            }
        } else {
            current.shellRecorder.draw(canvas)
            current.canvasRecorder.draw(canvas)
        }

        // ── VIEW_INVALIDATE：色温滤镜（MULTIPLY，不进 recorder）──
        if (colorTemperature < 6500f) {
            val (r, g, b) = colorTemperatureToRgb(colorTemperature)
            colorTempPaint.color = android.graphics.Color.rgb(r, g, b)
            colorTempPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), colorTempPaint)
            colorTempPaint.xfermode = null
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
    }
}
