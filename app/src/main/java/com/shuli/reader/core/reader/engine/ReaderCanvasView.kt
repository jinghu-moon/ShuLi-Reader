package com.shuli.reader.core.reader.engine

import com.shuli.reader.core.reader.model.SlotResolution
import com.shuli.reader.core.reader.model.TitleStyleConfig
import com.shuli.reader.core.reader.engine.input.CanvasTouchHandler
import com.shuli.reader.core.reader.engine.selection.CanvasTextSelection
import com.shuli.reader.core.reader.engine.selection.SelectionVisualStyle
import com.shuli.reader.core.reader.engine.cache.PageBitmapCache
import com.shuli.reader.core.reader.engine.cache.PageKey
import com.shuli.reader.core.reader.engine.cache.PageRenderState
import com.shuli.reader.core.reader.engine.cache.PageRenderStateStore
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.reader.engine.animation.BodyScrollPageDelegate
import com.shuli.reader.core.reader.engine.animation.PageDelegate
import com.shuli.reader.core.reader.engine.animation.PageDelegateFactory
import com.shuli.reader.core.reader.engine.animation.ScrollPageDelegate
import com.shuli.reader.feature.reader.settings.GestureAction
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.BoxBounds
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
/** 连续滚动单帧最多堆叠的页面数，防御性上限（正常 2~4 页即填满视口）。 */
private const val SCROLL_CHAIN_GUARD = 64

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
        color = SelectionVisualStyle.HIGHLIGHT_COLOR
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val selectionHandlePaint = Paint().apply {
        color = SelectionVisualStyle.HANDLE_COLOR
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val selectionHandleStemPaint = Paint().apply {
        color = SelectionVisualStyle.HANDLE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = SelectionVisualStyle.HANDLE_STEM_WIDTH
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    /**
     * 更新选区和把手颜色（使用主题色）
     */
    fun updateSelectionColors(accentColor: Int) {
        selectionPaint.color = (accentColor and 0x00FFFFFF) or 0x33000000  // 20% 透明度
        selectionHandlePaint.color = accentColor
        selectionHandleStemPaint.color = accentColor
        // 放大镜边框也使用主题色
        selectionMagnifierBorderPaint.color = (accentColor and 0x00FFFFFF) or 0x40000000  // 25% 透明度
    }

    private val selectionMagnifierFillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val selectionMagnifierBorderPaint = Paint().apply {
        color = SelectionVisualStyle.MAGNIFIER_BORDER_COLOR
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val selectionMagnifierShadowPaint = Paint().apply {
        color = SelectionVisualStyle.MAGNIFIER_SHADOW_COLOR
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val selectionMagnifierRect = RectF()
    private val selectionMagnifierShadowRect = RectF()
    private val selectionMagnifierPath = Path()

    // 放大镜弹性缩放动画
    private var magnifierScale: Float = 0f
    private var magnifierScaleAnimator: android.animation.ValueAnimator? = null

    private val selectionMagnifierCrosshairPaint = Paint().apply {
        color = 0x80FFFFFF.toInt()
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val crossfadePaint = Paint().apply {
        isAntiAlias = true
    }

    // 查找匹配高亮 Paint
    private val findMatchPaint = Paint().apply {
        color = 0x40FFAB40.toInt()  // 浅橙色
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val currentFindMatchPaint = Paint().apply {
        color = 0x80FFAB40.toInt()  // 深橙色
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /** 查找匹配列表 */
    var findMatches: List<SelectionRange> = emptyList()

    /** 当前查找匹配 */
    var currentFindMatch: SelectionRange? = null

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

    // Phase 5: key-diff 驱动失效 — 存储上次 apply 的 key，用于精确判断
    private var lastAppliedContentKey: Any? = null
    private var lastAppliedShellKey: Any? = null
    private var lastAppliedOverlayKey: Any? = null
    private var lastAppliedLayoutKey: Any? = null
    private var lastAppliedCurrentPage: TextPage? = null

    // 章节文本
    private var chapterContent: CharSequence = ""
    /** chapterContent 来自哪一章。setPageInternal 用它检测跨章切换。 */
    private var chapterContentChapterIndex: Int = -1
    /** 当前及相邻章节正文。录制任意 page 时必须按 page.chapterIndex 取正文。 */
    private var chapterContentsByIndex: Map<Int, CharSequence> = emptyMap()

    // 排版变化 crossfade 动画
    private var oldPageBitmap: Bitmap? = null
    private var crossfadeAnimator: ValueAnimator? = null
    private var crossfadeAlpha: Float = 0f

    // 渲染参数
    private val renderContext = RenderContext()

    private val pageRenderer = ReaderPageRenderer(textPaint, headerPaint, footerPaint, progressPaint)
    private var currentTitleStyle: TitleStyleConfig = TitleStyleConfig()

    // ── 拆分委托 ──────────────────────────────────────────────

    /** Phase 2a: recorder 唯一 owner，TextPage 不再持有 recorder */
    internal val renderStateStore = PageRenderStateStore()

    /** 从 TextPage 生成 PageKey（用于 store 索引） */
    private fun TextPage.toKey() = PageKey(
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        startCharOffset = startCharOffset,
        endCharOffset = endCharOffset,
    )

    /** 返回当前活跃的页面 key 集合（current + next + prev） */
    private fun activePageKeys(): Set<PageKey> {
        val keys = mutableSetOf<PageKey>()
        currentPage?.let { keys.add(it.toKey()) }
        nextPage?.let { keys.add(it.toKey()) }
        prevPage?.let { keys.add(it.toKey()) }
        // 连续滚动时视口内堆叠的多页也需保活，否则其 render state 会被回收导致重录闪烁
        keys.addAll(scrollChainKeys)
        return keys
    }

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
        onPagesInvalidate = {
            // TTS/选区/笔记变化仅失效 overlay 层，正文不重录（§10 分层 recorder）
            currentPage?.let { renderStateStore.getPageState(it.toKey()).invalidateOverlay() }
            nextPage?.let { renderStateStore.getPageState(it.toKey()).invalidateOverlay() }
            prevPage?.let { renderStateStore.getPageState(it.toKey()).invalidateOverlay() }
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
            override fun getTextSelection() = this@ReaderCanvasView.textSelection
            override fun isScrollPageMode() = pageAnimType == PageDelegateFactory.PageAnimType.SCROLL
            override fun isInBodyBox(x: Float, y: Float): Boolean {
                val body = currentPage?.layout?.body ?: return true
                return x >= body.left && x <= body.right && y >= body.top && y <= body.bottom
            }
            override fun onAction(action: GestureAction, x: Float, y: Float) {
                this@ReaderCanvasView.onGestureAction?.invoke(action)
            }
            override fun isEditMode() = this@ReaderCanvasView.isEditMode
            override fun onInlineEditTap(x: Float, y: Float) {
                val page = currentPage ?: return
                val range = textSelection.selectWordAt(
                    x, y, page, chapterContent, width.toFloat(), textPaint,
                ) ?: return
                // 清除视觉选区（编辑模式不需要显示选区把手）
                textSelection.clearSelection()
                renderContext.selectedRange = null
                invalidate()
                // 计算词起始 X 和结束 X（供 Popover 精确定位）
                val bodyLeft = page.layout.body.left
                val line = page.lines.firstOrNull { l ->
                    range.startPos >= l.startCharOffset && range.startPos < l.endCharOffset
                }
                var wordStartX = x
                var wordEndX = x
                if (line != null && line.charWidths != null) {
                    val cw = line.charWidths!!
                    // 计算词起始 X
                    wordStartX = bodyLeft + line.startXOffset
                    val startIdx = (range.startPos - line.startCharOffset).coerceAtMost(cw.size)
                    for (i in 0 until startIdx) wordStartX += cw[i]
                    // 计算词结束 X
                    wordEndX = wordStartX
                    val endIdx = (range.endPos - range.startPos).coerceAtMost(cw.size - startIdx)
                    for (i in 0 until endIdx) wordEndX += cw[startIdx + i]
                }
                val baselineY = line?.baseline ?: y
                this@ReaderCanvasView.onInlineEditTap?.invoke(range, wordStartX, wordEndX, baselineY)
            }
            override fun onPageChanged(direction: PageDelegate.Direction) {
                this@ReaderCanvasView.onPageChanged?.invoke(direction)
            }
            override fun onCenterClicked() {
                this@ReaderCanvasView.onCenterClicked?.invoke()
            }
            override fun onSelectionCleared() {
                // 清除 Canvas 渲染层的选区状态并重绘
                renderContext.selectedRange = null
                val page = currentPage
                if (page != null) {
                    renderStateStore.getPageState(page.toKey()).invalidateContent()
                }
                invalidate()
                // 通知上层（ViewModel 清除 Compose 状态）
                this@ReaderCanvasView.onTextCleared?.invoke()
            }
            override fun onLongPress(x: Float, y: Float) {
                pageDelegate?.abort()
                val page = currentPage ?: return
                val range = textSelection.selectWordAt(x, y, page, chapterContent, width.toFloat(), textPaint)
                if (range != null) {
                    renderContext.selectedRange = range
                    beginTextSelection()
                    renderStateStore.getPageState(page.toKey()).invalidateContent()
                    invalidate()

                    // 计算选区最后一行的左右边界（小三角指向最后一行中间）
                    val handleInfos = textSelection.getHandleRects(page, width.toFloat())
                    val screenY = handleInfos?.lastOrNull()?.rect?.bottom ?: y
                    // 获取最后一行选区的左右 X 边界
                    val lastLineXRange = textSelection.getLastLineXRange(page, width.toFloat())
                    val startX = lastLineXRange?.first ?: (handleInfos?.firstOrNull()?.rect?.centerX() ?: x)
                    val endX = lastLineXRange?.second ?: (handleInfos?.lastOrNull()?.rect?.centerX() ?: x)

                    onTextSelected?.invoke(range, startX, endX, screenY)
                }
            }
            override fun onSelectionHandleDragStart(anchorId: CanvasTextSelection.AnchorId) {
                // 开始拖动把手，隐藏菜单
                onSelectionDragStart?.invoke()
                magnifierScaleAnimator?.cancel()
                magnifierScaleAnimator = android.animation.ValueAnimator.ofFloat(magnifierScale, 1.2f).apply {
                    duration = 200
                    interpolator = android.view.animation.OvershootInterpolator(1.5f)
                    addUpdateListener { anim ->
                        magnifierScale = anim.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
            override fun onSelectionHandleDragMove(x: Float, y: Float, isFastDrag: Boolean) {
                val page = currentPage ?: return
                // 将像素坐标转换为字符位置
                val charIndex = textSelection.pixelToChar(x, y, page, chapterContent, textPaint)
                if (charIndex != null) {
                    val result = textSelection.moveHandle(charIndex, chapterContent, page, isFastDrag)
                    if (result != null) {
                        renderContext.selectedRange = result.range
                        // 触发震动反馈
                        if (isHapticFeedbackEnabled) {
                            if (result.collided) {
                                if (android.os.Build.VERSION.SDK_INT >= 29) {
                                    performHapticFeedback(android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                                } else {
                                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                }
                            } else if (result.snapped || result.lineChanged) {
                                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }

                        // 选区高亮在 overlay 层，需要失效 overlay
                        renderStateStore.getPageState(page.toKey()).invalidateOverlay()
                        invalidate()
                    }
                }
            }
            override fun onSelectionHandleDragEnd() {
                // 结束拖动把手，显示菜单
                val range = textSelection.selectedRange
                val page = currentPage
                if (range != null && page != null) {
                    val handleInfos = textSelection.getHandleRects(page, width.toFloat())
                    val screenY = handleInfos?.lastOrNull()?.rect?.bottom ?: 0f
                    // 获取最后一行选区的左右 X 边界
                    val lastLineXRange = textSelection.getLastLineXRange(page, width.toFloat())
                    val startX = lastLineXRange?.first ?: (handleInfos?.firstOrNull()?.rect?.centerX() ?: 0f)
                    val endX = lastLineXRange?.second ?: (handleInfos?.lastOrNull()?.rect?.centerX() ?: 0f)
                    onSelectionDragEnd?.invoke(range, startX, endX, screenY)
                }
                
                magnifierScaleAnimator?.cancel()
                magnifierScaleAnimator = android.animation.ValueAnimator.ofFloat(magnifierScale, 0f).apply {
                    duration = 150
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        magnifierScale = anim.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        }
    }

    // ── 回调 ──────────────────────────────────────────────────

    // 翻页动画委托
    private var pageDelegate: PageDelegate? = null
    private var pageAnimType: PageDelegateFactory.PageAnimType = PageDelegateFactory.PageAnimType.HORIZONTAL

    // ── 连续滚动：页面序列 + 回收器 ────────────────────────────

    /**
     * 滚动模式页面序列提供者：给定锚点页与相对位移 delta（0=锚点，+1=下一页，-1=上一页），
     * 返回连续的页面（可跨章）。由 ReaderScreen 从 uiState 的相邻章节构建。
     */
    var scrollPageProvider: ((anchor: TextPage, delta: Int) -> TextPage?)? = null

    /** 上一帧连续流实际绘制的页面 key，纳入 activePageKeys 防止 render state 被回收。 */
    private var scrollChainKeys: Set<PageKey> = emptySet()

    /**
     * [ScrollPageDelegate] 的页面回收器实现。
     * 当连续偏移量越过当前页实际高度边界时，沿页面序列前进/后退一页，
     * 并通过 [onPageChanged] 通知上层同步章节/页码状态。
     */
    private val scrollPagerImpl = object : com.shuli.reader.core.reader.engine.animation.ScrollPageDelegate.Pager {
        override fun currentSegmentHeight(): Float {
            val cur = currentPage ?: return height.toFloat().coerceAtLeast(1f)
            return ScrollBodyFlowLayout.segmentFor(cur, currentTitleStyle).height
        }

        override fun viewportHeight(): Float {
            val cur = currentPage ?: return height.toFloat().coerceAtLeast(1f)
            return ScrollBodyFlowLayout.viewportFor(cur).height
        }

        override fun advanceForward(): Boolean {
            val cur = currentPage ?: return false
            val next = scrollPageProvider?.invoke(cur, 1) ?: return false
            if (contentForPage(next) == null) return false
            prevPage = cur
            currentPage = next
            nextPage = scrollPageProvider?.invoke(next, 1)
            onPageChanged?.invoke(PageDelegate.Direction.NEXT)
            return true
        }

        override fun advanceBackward(): Boolean {
            val cur = currentPage ?: return false
            val prev = scrollPageProvider?.invoke(cur, -1) ?: return false
            if (contentForPage(prev) == null) return false
            nextPage = cur
            currentPage = prev
            prevPage = scrollPageProvider?.invoke(prev, -1)
            onPageChanged?.invoke(PageDelegate.Direction.PREV)
            return true
        }
    }

    // 翻页回调
    var onPageChanged: ((PageDelegate.Direction) -> Unit)? = null

    // 边界检测回调
    var canTurnPrev: (() -> Boolean)? = null
    var canTurnNext: (() -> Boolean)? = null

    // 文本选区回调 (range, startX, endX, screenY)
    var onTextSelected: ((SelectionRange, Float, Float, Float) -> Unit)? = null

    // 选区清除回调
    var onTextCleared: (() -> Unit)? = null

    // 编辑模式标志（编辑模式下点击正文触发内联编辑）
    var isEditMode: Boolean = false

    // 编辑模式正文点击回调 (range, startX, endX, screenY)
    var onInlineEditTap: ((SelectionRange, Float, Float, Float) -> Unit)? = null

    // 编辑预览：当前正在编辑的文本（实时显示在 Canvas 上）
    var editPreviewText: String? = null
    // 编辑预览：对应的选区范围
    var editPreviewRange: SelectionRange? = null

    // 选区拖动开始回调（用于隐藏菜单）
    var onSelectionDragStart: (() -> Unit)? = null

    // 选区拖动结束回调（用于显示菜单）(range, startX, endX, screenY)
    var onSelectionDragEnd: ((SelectionRange, Float, Float, Float) -> Unit)? = null

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
            renderStateStore = renderStateStore,
            chapterContents = snapshotChapterContents(),
            renderContext = renderContext,
            backgroundPaint = backgroundPaint,
            textPaint = textPaint,
            selectionPaint = selectionPaint,
            headerPaint = headerPaint,
            footerPaint = footerPaint,
            progressPaint = progressPaint,
            postInvalidate = { postInvalidate() },
            generation = renderContext.generation,
        )
    }

    private fun snapshotChapterContents(): Map<Int, CharSequence> {
        if (chapterContentChapterIndex >= 0) {
            return chapterContentsByIndex + (chapterContentChapterIndex to chapterContent)
        }
        return chapterContentsByIndex
    }

    private fun contentForPage(page: TextPage): CharSequence? {
        return chapterContentsByIndex[page.chapterIndex]
            ?: chapterContent.takeIf { chapterContentChapterIndex == page.chapterIndex }
    }

    // ── RenderApplierTarget：scope-only invalidation ─────────────

    override fun invalidateAllPages() {
        currentPage?.let { renderStateStore.getPageState(it.toKey()).invalidateAll() }
        nextPage?.let { renderStateStore.getPageState(it.toKey()).invalidateAll() }
        prevPage?.let { renderStateStore.getPageState(it.toKey()).invalidateAll() }
    }

    override fun rebuildPageDelegate() {
        // pageDelegate 已由 applySnapshot 在进入 Applier 之前通过 setPageDelegate 设置；
        // 此方法仅作语义标记，使测试可验证 PAGE_DELEGATE scope 分发。
    }

    /**
     * Phase 5: key-diff 驱动精确失效。
     *
     * 替代大部分 scope-based invalidation：比较新旧 key，只有 key 变化才 invalidate 对应层。
     * 页面身份变化（currentPage 引用改变）时强制 invalidate 新页面的 content。
     */
    fun applyKeyDiff(contentKey: Any?, shellKey: Any?, overlayKey: Any?, layoutKey: Any? = null) {
        // 页面身份变化：新页面的 recorder 是空的，强制失效
        if (currentPage !== lastAppliedCurrentPage && currentPage != null) {
            renderStateStore.getPageState(currentPage!!.toKey()).invalidateContent()
        }

        // 排版参数变化（字号/行距/边距等）：页面尚未 reflow 时 contentKey 不变，
        // 但 Paint 已更新，必须强制 content + 行级 recorder 失效以用新参数重录。
        val layoutChanged = layoutKey != null && layoutKey != lastAppliedLayoutKey
        if (layoutChanged) {
            currentPage?.let {
                val key = it.toKey()
                renderStateStore.getPageState(key).invalidateContent()
                renderStateStore.invalidateLinesFor(key)
            }
        }

        // 对每个活跃页面执行 key-diff
        val pages = listOfNotNull(currentPage, nextPage, prevPage)
        for (page in pages) {
            renderStateStore.getPageState(page.toKey())
                .applyKeyDiff(contentKey, shellKey, overlayKey)
        }

        lastAppliedContentKey = contentKey
        lastAppliedShellKey = shellKey
        lastAppliedOverlayKey = overlayKey
        lastAppliedLayoutKey = layoutKey
        lastAppliedCurrentPage = currentPage
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

        // Phase 2a: 回收不再引用的旧页面的 render state
        if (changed) {
            // 先更新页面引用，再回收不活跃的 state
            // （recycleUnused 基于 activePageKeys 判断，所以需要先设新值）
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
            chapterContentsByIndex = chapterContentsByIndex + (incomingChapterIdx to content)
        }

        if (chapterSwitched && chapterContentChapterIndex != incomingChapterIdx) {
            // 新章 content 尚未就位（applySnapshot 还没跑），先把 recorder 标脏，
            // 阻止 draw 时使用残留的旧 Picture。
            if (com.shuli.reader.BuildConfig.DEBUG) {
                android.util.Log.d(
                    "ReaderCanvasView",
                    "chapterSwitch invalidate: prev=$previousChapterIdx incoming=$incomingChapterIdx contentChapter=$chapterContentChapterIndex",
                )
            }
            val newState = renderStateStore.getPageState(page.toKey())
            newState.invalidateAll()
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

        // Phase 2a: 页面引用更新后，回收不活跃的 render state
        if (changed) {
            renderStateStore.recycleUnused(activePageKeys())
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
            chapterContentsByIndex = chapterContents.toMap()
        }
        if (chapterContent.isNotEmpty()) {
            this.chapterContent = chapterContent
            this.chapterContentChapterIndex = snapshot.page.chapterIndex
            chapterContentsByIndex = chapterContentsByIndex + (snapshot.page.chapterIndex to chapterContent)
        }

        val layoutInput = snapshot.layout.input
        val v = snapshot.visual
        val sh = snapshot.shell
        pageAnimType = snapshot.page.pageAnimType

        // 1. 始终应用全部视觉参数（幂等操作，值未变时各 setter 内部跳过）
        // PR-4: 将 snapshot 的 generation 传递给 renderContext，用于后台任务过期校验
        renderContext.generation = snapshot.generation
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
        currentTitleStyle = v.titleStyle
        visualParams.setTitleStyle(v.titleStyle)
        visualParams.setEdgeTurnPageEnabled(sh.edgeTurnPage)
        visualParams.setEdgeWidthPercent(sh.edgeWidthPercent)
        this.leftZoneRatio = sh.leftZoneRatio
        this.gestureConfig = sh.gestureConfig
        if (this.colorTemperature != sh.colorTemperature) {
            this.colorTemperature = sh.colorTemperature
        }
        // 更新主题颜色（包括选区和把手颜色）
        setThemeColors(v.themeColors)
        if (pageDelegate != null) {
            setPageDelegate(pageDelegate)
        }

        // 2. 按 diff 范围精确失效：委托给 ReaderCanvasStateApplier
        com.shuli.reader.feature.reader.render.ReaderCanvasStateApplier()
            .apply(this, snapshot, diff)

        // Phase 5: key-diff 驱动精确失效（补充 scope-based invalidation）
        applyKeyDiff(
            contentKey = v.contentKey,
            shellKey = sh.shellKey,
            overlayKey = snapshot.overlay.overlayKey,
            layoutKey = snapshot.layout.layoutKey,
        )

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
        // 连续滚动委托注入页面回收器，使其能按实际页高度跨章回收页面
        (actualDelegate as? ScrollPageDelegate)?.pager = scrollPagerImpl
        actualDelegate?.setCallback(object : PageDelegate.Callback {
            override fun onPageChanged(direction: PageDelegate.Direction) {
                actualDelegate.abort()
                // Synchronously swap page references so the static draw path
                // immediately shows the correct page (avoids one-frame flicker).
                // The snapshot flow will validate on the next apply.
                when (direction) {
                    PageDelegate.Direction.NEXT -> {
                        prevPage = currentPage
                        currentPage = nextPage ?: currentPage
                        nextPage = null
                    }
                    PageDelegate.Direction.PREV -> {
                        nextPage = currentPage
                        currentPage = prevPage ?: currentPage
                        prevPage = null
                    }
                    PageDelegate.Direction.NONE -> {}
                }
                onPageChanged?.invoke(direction)
            }

            override fun invalidate() {
                this@ReaderCanvasView.invalidate()
            }
        })
    }

    /** 将滚动模式的页面内容垂直移动指定偏移量，用于选区编辑时避让键盘。 */
    fun scrollToY(deltaY: Float) {
        (pageDelegate as? ScrollPageDelegate)?.setScrollPosition(deltaY, active = deltaY != 0f)
        invalidate()
    }

    /** 重置编辑避让产生的滚动偏移。 */
    fun resetCanvasOffset() {
        (pageDelegate as? ScrollPageDelegate)?.resetScrollPosition()
        invalidate()
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
    internal fun setTitleStyle(style: TitleStyleConfig) {
        currentTitleStyle = style
        visualParams.setTitleStyle(style)
    }
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

    internal fun setThemeColors(colors: ThemeColors) {
        visualParams.setThemeColors(colors)
        // 更新选区和把手颜色为主题色
        updateSelectionColors(colors.accentColor)
    }

    internal fun setEdgeTurnPageEnabled(enabled: Boolean) = visualParams.setEdgeTurnPageEnabled(enabled)
    internal fun setEdgeWidthPercent(percent: Float) = visualParams.setEdgeWidthPercent(percent)
    internal fun setHeaderTextRatio(ratio: Float) = visualParams.setHeaderTextRatio(ratio)
    internal fun setFooterTextRatio(ratio: Float) = visualParams.setFooterTextRatio(ratio)

    // ── 页面旋转 / crossfade ──────────────────────────────────

    private fun startLayoutCrossfade() {
        val w = width
        val h = height
        val cur = currentPage
        if (w <= 0 || h <= 0 || cur == null) return

        crossfadeAnimator?.cancel()

        // 复用尺寸匹配的旧 Bitmap，避免每次 reflow 重新分配
        val bitmap = oldPageBitmap?.takeIf { it.width == w && it.height == h && !it.isRecycled }
            ?: run {
                oldPageBitmap?.recycle()
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            }

        val captureCanvas = Canvas(bitmap)
        captureCanvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        renderStateStore.getPageState(cur.toKey()).content.draw(captureCanvas)

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
            // 尺寸变化时失效所有活跃页面的 recorder
            currentPage?.let { renderStateStore.getPageState(it.toKey()).invalidateAll() }
            nextPage?.let { renderStateStore.getPageState(it.toKey()).invalidateAll() }
            prevPage?.let { renderStateStore.getPageState(it.toKey()).invalidateAll() }
            submitRenderTask()
        }
    }

    override fun onDetachedFromWindow() {
        crossfadeAnimator?.cancel()
        oldPageBitmap?.recycle()
        oldPageBitmap = null
        renderStateStore.clear()
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

        val currentState = renderStateStore.getPageState(current.toKey())

        recordPageIfNeeded(current, currentState, currentContent)

        val delegate = pageDelegate
        val bodyScrollDelegate = delegate as? BodyScrollPageDelegate
        updateBodyScrollExtent(current, bodyScrollDelegate)
        val isDelegateActive = delegate != null &&
            (delegate.state != PageDelegate.State.IDLE || (bodyScrollDelegate?.getScrollPosition() ?: 0f) != 0f)
        if (bodyScrollDelegate is ScrollPageDelegate) {
            drawContinuousFlow(canvas, current, currentState, bodyScrollDelegate)
        } else if (delegate != null && isDelegateActive) {
            val isPrevDirection = when (delegate.state) {
                PageDelegate.State.DRAGGING -> delegate.isDraggingBackward()
                PageDelegate.State.ANIMATING -> delegate.direction == PageDelegate.Direction.PREV
                PageDelegate.State.SETTLING -> delegate.direction == PageDelegate.Direction.PREV
                PageDelegate.State.IDLE -> delegate.isDraggingBackward()
            }
            val target = if (isPrevDirection) prevPage else nextPage
            val drawableTarget = target?.let { page ->
                contentForPage(page)?.let { content -> page to content }
            }
            if (target != null && drawableTarget == null) {
                val targetState = renderStateStore.getPageState(target.toKey())
                targetState.invalidateAll()
                currentState.shell.draw(canvas)
                currentState.content.draw(canvas)
                currentState.overlay.draw(canvas)
            } else {
                val targetState = drawableTarget?.let { (targetPage, targetContent) ->
                    val targetState = renderStateStore.getPageState(targetPage.toKey())
                    recordPageIfNeeded(targetPage, targetState, targetContent)
                    targetState
                }
                if (bodyScrollDelegate != null) {
                    drawScrollBodyAnimation(canvas, current, currentState, targetState, bodyScrollDelegate)
                } else {
                    targetState?.recordComposite(width, height)
                    currentState.recordComposite(width, height)
                    delegate.onDraw(canvas, currentState.composite, targetState?.composite ?: currentState.composite)
                }
            }
        } else {
            currentState.shell.draw(canvas)
            currentState.content.draw(canvas)
            currentState.overlay.draw(canvas)
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

        // 绘制选区高亮和把手（在最上层，不进 recorder，确保实时更新）
        val activeScrollOffset = (pageDelegate as? BodyScrollPageDelegate)
            ?.getScrollPosition()
            ?: 0f
        if (activeScrollOffset != 0f) {
            canvas.save()
            val body = if (pageDelegate is ScrollPageDelegate) {
                ScrollBodyFlowLayout.viewportFor(current)
            } else {
                current.layout.body
            }
            canvas.clipRect(body.left, body.top, body.right, body.bottom)
            canvas.translate(0f, activeScrollOffset)
        }
        drawSelectionOverlay(canvas)
        drawSelectionMagnifier(canvas, current, currentState)
        if (activeScrollOffset != 0f) {
            canvas.restore()
        }
    }

    private fun recordPageIfNeeded(
        page: TextPage,
        state: PageRenderState,
        content: CharSequence,
    ) {
        if (!state.content.needRecord() && !state.shell.needRecord() && !state.overlay.needRecord()) {
            return
        }
        pageBitmapCache.recordPage(
            page = page,
            renderState = state,
            width = width,
            height = height,
            content = content,
            contentChapterIndex = page.chapterIndex,
            renderContext = renderContext,
            backgroundPaint = backgroundPaint,
            textPaint = textPaint,
            selectionPaint = selectionPaint,
            renderStateStore = renderStateStore,
        )
    }

    /**
     * 连续滚动绘制（对标 legado [ContentTextView.drawPage]）。
     *
     * 以当前页为锚点，沿 [scrollPageProvider] 提供的页面序列按"实际内容高度"向下堆叠，
     * 直到填满视口；必要时向上回填顶部空隙。这样章节尾页内容再短，紧随其后的下一章也会
     * 立即出现在其下方，彻底消除"短尾页 + 大段空白"的缺陷。
     */
    private fun drawContinuousFlow(
        canvas: Canvas,
        current: TextPage,
        currentState: PageRenderState,
        delegate: ScrollPageDelegate,
    ) {
        // header/footer 始终取当前页骨架
        currentState.shell.draw(canvas)

        val viewport = ScrollBodyFlowLayout.viewportFor(current)
        if (viewport.width <= 0f || viewport.height <= 0f) {
            currentState.content.draw(canvas)
            currentState.overlay.draw(canvas)
            return
        }

        val provider = scrollPageProvider
        val scrollOffset = delegate.getScrollPosition()
        val activeKeys = mutableSetOf<PageKey>()

        canvas.save()
        canvas.clipRect(viewport.left, viewport.top, viewport.right, viewport.bottom)

        // 向下：从当前页起按实际高度堆叠，直到超出视口底部
        var page: TextPage? = current
        var offset = scrollOffset
        var guard = 0
        while (page != null && offset < viewport.height && guard++ < SCROLL_CHAIN_GUARD) {
            val seg = ScrollBodyFlowLayout.segmentFor(page, currentTitleStyle)
            val state = stateForChainPage(page)
            if (state != null) {
                drawScrollFlowSegment(canvas, viewport, seg, state, offset)
                activeKeys.add(page.toKey())
            }
            offset += seg.height
            page = provider?.invoke(page, 1)
        }

        // 向上：回填当前页顶部以上的空隙（scrollOffset > 0 时，例如书首回弹瞬态）
        var up: TextPage? = provider?.invoke(current, -1)
        var topOffset = scrollOffset
        guard = 0
        while (up != null && topOffset > 0f && guard++ < SCROLL_CHAIN_GUARD) {
            val seg = ScrollBodyFlowLayout.segmentFor(up, currentTitleStyle)
            topOffset -= seg.height
            val state = stateForChainPage(up)
            if (state != null) {
                drawScrollFlowSegment(canvas, viewport, seg, state, topOffset)
                activeKeys.add(up.toKey())
            }
            up = provider?.invoke(up, -1)
        }

        canvas.restore()
        scrollChainKeys = activeKeys
    }

    /** 取（必要时录制）连续流中某一页的 render state；内容缺失时返回 null。 */
    private fun stateForChainPage(page: TextPage): PageRenderState? {
        val content = contentForPage(page) ?: return null
        val state = renderStateStore.getPageState(page.toKey())
        recordPageIfNeeded(page, state, content)
        return state
    }

    private fun drawScrollBodyAnimation(
        canvas: Canvas,
        current: TextPage,
        currentState: PageRenderState,
        targetState: PageRenderState?,
        delegate: BodyScrollPageDelegate,
    ) {
        currentState.shell.draw(canvas)

        val body = current.layout.body
        if (body.width <= 0f || body.height <= 0f) {
            currentState.content.draw(canvas)
            currentState.overlay.draw(canvas)
            return
        }

        val scrollOffset = delegate.getScrollPosition()
        val viewportHeight = delegate.getViewportHeight()
        val targetOffset = if (delegate.isDraggingBackward()) {
            scrollOffset - viewportHeight
        } else {
            scrollOffset + viewportHeight
        }

        canvas.save()
        canvas.clipRect(body.left, body.top, body.right, body.bottom)
        drawBodyLayers(canvas, currentState, scrollOffset)
        targetState?.let { drawBodyLayers(canvas, it, targetOffset) }
        canvas.restore()
    }

    private fun drawScrollFlowSegment(
        canvas: Canvas,
        viewport: BoxBounds,
        segment: ScrollBodySegment,
        state: PageRenderState,
        offsetY: Float,
    ) {
        canvas.save()
        canvas.translate(viewport.left - segment.sourceLeft, viewport.top + offsetY - segment.sourceTop)
        state.content.draw(canvas)
        state.overlay.draw(canvas)
        canvas.restore()
    }

    private fun updateBodyScrollExtent(current: TextPage, delegate: BodyScrollPageDelegate?) {
        if (delegate == null) return
        if (delegate is ScrollPageDelegate) {
            // 连续流模型按页逐个计算高度（由 scrollPagerImpl 提供），这里只需告知视口高度用于书末钳制
            delegate.setViewportHeight(ScrollBodyFlowLayout.viewportFor(current).height)
        } else {
            delegate.setViewportHeight(current.layout.body.height)
        }
    }

    private fun drawBodyLayers(canvas: Canvas, state: PageRenderState, offsetY: Float) {
        canvas.save()
        canvas.translate(0f, offsetY)
        state.content.draw(canvas)
        state.overlay.draw(canvas)
        canvas.restore()
    }

    /**
     * 绘制选区把手和查找匹配高亮
     */
    private fun drawSelectionOverlay(canvas: Canvas) {
        val page = currentPage ?: return

        // 绘制查找匹配高亮
        if (findMatches.isNotEmpty() || currentFindMatch != null) {
            drawFindMatches(canvas, page)
        }

        // 绘制选区把手
        if (textSelection.selectedRange != null) {
            val handleInfos = textSelection.getHandleRects(page, width.toFloat())
            if (handleInfos != null) {
                for (info in handleInfos) {
                    drawHandle(canvas, info.rect, info.isStart)
                }
            }
        }

        // 绘制编辑预览
        drawEditPreview(canvas, page)
    }

    /**
     * 绘制编辑预览：覆盖原文并显示实时输入文本
     */
    private fun drawEditPreview(canvas: Canvas, page: TextPage) {
        val previewText = editPreviewText ?: return
        val range = editPreviewRange ?: return

        // 找到被编辑词所在的行
        val line = page.lines.firstOrNull { l ->
            range.startPos >= l.startCharOffset && range.startPos < l.endCharOffset
        } ?: return

        val bodyLeft = page.layout.body.left

        // 计算原文区域的起止 X 坐标
        val charWidths = line.charWidths
        var originalStartX = bodyLeft + line.startXOffset
        var originalEndX = originalStartX

        if (charWidths != null && charWidths.size == (line.endCharOffset - line.startCharOffset)) {
            for (i in 0 until (range.startPos - line.startCharOffset).coerceAtMost(charWidths.size)) {
                originalStartX += charWidths[i]
            }
            originalEndX = originalStartX
            val wordLen = (range.endPos - range.startPos).coerceAtMost(charWidths.size - (range.startPos - line.startCharOffset))
            for (i in 0 until wordLen) {
                originalEndX += charWidths[(range.startPos - line.startCharOffset) + i]
            }
        }

        // 用背景色覆盖原文
        val bgPaint = Paint().apply {
            color = backgroundPaint.color
            style = Paint.Style.FILL
        }
        val coverRect = android.graphics.RectF(
            originalStartX - 2f,
            line.top - 2f,
            originalEndX + 2f,
            line.bottom + 2f,
        )
        canvas.drawRect(coverRect, bgPaint)

        // 在原文位置绘制新文本
        val previewPaint = Paint(textPaint)
        canvas.drawText(previewText, originalStartX, line.baseline, previewPaint)
    }

    /**
     * 绘制查找匹配高亮
     */
    private fun drawFindMatches(canvas: Canvas, page: TextPage) {
        val bodyLeft = page.layout.body.left

        // 绘制其他匹配（浅色）
        for (match in findMatches) {
            if (match == currentFindMatch) continue
            page.lines.forEach { line ->
                if (intersects(match, line.startCharOffset, line.endCharOffset)) {
                    val rect = calculateMatchRect(line, match, bodyLeft)
                    canvas.drawRoundRect(rect, 3f, 3f, findMatchPaint)
                }
            }
        }

        // 绘制当前匹配（深色）
        currentFindMatch?.let { match ->
            page.lines.forEach { line ->
                if (intersects(match, line.startCharOffset, line.endCharOffset)) {
                    val rect = calculateMatchRect(line, match, bodyLeft)
                    canvas.drawRoundRect(rect, 3f, 3f, currentFindMatchPaint)
                }
            }
        }
    }

    /**
     * 计算匹配矩形
     */
    private fun calculateMatchRect(line: com.shuli.reader.core.reader.model.TextLine, match: SelectionRange, bodyLeft: Float): RectF {
        val lineStart = line.startCharOffset
        val lineEnd = line.endCharOffset
        val matchStart = maxOf(match.startPos, lineStart)
        val matchEnd = minOf(match.endPos, lineEnd)

        val charWidths = line.charWidths
        var startX = bodyLeft + line.startXOffset
        var endX = startX

        if (charWidths != null && charWidths.size == (lineEnd - lineStart)) {
            for (i in 0 until (matchStart - lineStart)) { startX += charWidths[i] }
            endX = startX
            for (i in (matchStart - lineStart) until (matchEnd - lineStart)) { endX += charWidths[i] }
        } else {
            startX = bodyLeft + line.startXOffset
            endX = startX + line.measuredWidth
        }

        return RectF(startX - 1f, line.top, endX + 1f, line.bottom)
    }

    /**
     * 判断选区是否与行相交
     */
    private fun intersects(range: SelectionRange, lineStart: Int, lineEnd: Int): Boolean {
        return range.startPos < lineEnd && range.endPos > lineStart
    }

    /**
     * 绘制单个把手
     */
    private fun drawHandle(
        canvas: Canvas,
        rect: RectF,
        isStart: Boolean,
    ) {
        val centerX = rect.centerX()
        val dotRadius = SelectionVisualStyle.HANDLE_DOT_RADIUS
        val stemStartY = if (isStart) rect.top + dotRadius else rect.top
        val stemEndY = if (isStart) rect.bottom else rect.bottom - dotRadius
        val dotCenterY = if (isStart) rect.top + dotRadius else rect.bottom - dotRadius

        canvas.drawLine(centerX, stemStartY, centerX, stemEndY, selectionHandleStemPaint)
        canvas.drawCircle(centerX, dotCenterY, dotRadius, selectionHandlePaint)
    }

    /**
     * 拖动把手时显示局部放大镜，避免手指遮挡当前把手和附近文本。
     */
    private fun drawSelectionMagnifier(
        canvas: Canvas,
        page: TextPage,
        pageState: PageRenderState,
    ) {
        if (!textSelection.isSelecting || magnifierScale <= 0.01f) return
        val activeAnchor = textSelection.activeAnchor ?: return
        val handleInfos = textSelection.getHandleRects(page, width.toFloat()) ?: return
        val focusHandle = handleInfos.firstOrNull { it.anchorId == activeAnchor } ?: return
        val focusRect = focusHandle.rect

        val density = page.density.takeIf { it > 0f } ?: resources.displayMetrics.density
        val lensWidth = SelectionVisualStyle.MAGNIFIER_WIDTH_DP * density
        val lensHeight = SelectionVisualStyle.MAGNIFIER_HEIGHT_DP * density
        val cornerRadius = SelectionVisualStyle.MAGNIFIER_CORNER_RADIUS_DP * density
        val edgePadding = SelectionVisualStyle.MAGNIFIER_EDGE_PADDING_DP * density
        val handleGap = SelectionVisualStyle.MAGNIFIER_HANDLE_GAP_DP * density
        val shadowOffset = SelectionVisualStyle.MAGNIFIER_SHADOW_OFFSET_DP * density

        val dotRadius = SelectionVisualStyle.HANDLE_DOT_RADIUS
        val focusX = focusRect.centerX()
        val focusY = if (focusHandle.isStart) {
            focusRect.top + dotRadius
        } else {
            focusRect.bottom - dotRadius
        }

        val maxLeft = (width.toFloat() - lensWidth - edgePadding).coerceAtLeast(edgePadding)
        val lensLeft = (focusX - lensWidth / 2f).coerceIn(edgePadding, maxLeft)
        val preferAbove = focusY - handleGap - lensHeight >= edgePadding
        val rawTop = if (preferAbove) {
            focusY - handleGap - lensHeight
        } else {
            focusY + handleGap
        }
        val maxTop = (height.toFloat() - lensHeight - edgePadding).coerceAtLeast(edgePadding)
        val lensTop = rawTop.coerceIn(edgePadding, maxTop)

        selectionMagnifierRect.set(lensLeft, lensTop, lensLeft + lensWidth, lensTop + lensHeight)
        selectionMagnifierShadowRect.set(selectionMagnifierRect)
        selectionMagnifierShadowRect.offset(0f, shadowOffset)
        selectionMagnifierFillPaint.color = backgroundPaint.color
        selectionMagnifierBorderPaint.strokeWidth = SelectionVisualStyle.MAGNIFIER_BORDER_WIDTH_DP * density

        canvas.drawRoundRect(
            selectionMagnifierShadowRect,
            cornerRadius,
            cornerRadius,
            selectionMagnifierShadowPaint,
        )
        canvas.drawRoundRect(
            selectionMagnifierRect,
            cornerRadius,
            cornerRadius,
            selectionMagnifierFillPaint,
        )

        selectionMagnifierPath.reset()
        selectionMagnifierPath.addRoundRect(
            selectionMagnifierRect,
            cornerRadius,
            cornerRadius,
            Path.Direction.CW,
        )

        canvas.save()
        // 放大镜出现动画：以放大镜中心为基准缩放
        val lensCenterX = selectionMagnifierRect.centerX()
        val lensCenterY = selectionMagnifierRect.centerY()
        canvas.scale(magnifierScale, magnifierScale, lensCenterX, lensCenterY)

        canvas.save()
        canvas.clipPath(selectionMagnifierPath)
        canvas.translate(lensCenterX, lensCenterY)
        canvas.scale(SelectionVisualStyle.MAGNIFIER_ZOOM, SelectionVisualStyle.MAGNIFIER_ZOOM)
        canvas.translate(-focusX, -focusY)
        pageState.shell.draw(canvas)
        pageState.content.draw(canvas)
        pageState.overlay.draw(canvas)
        for (info in handleInfos) {
            drawHandle(canvas, info.rect, info.isStart)
        }
        canvas.restore() // 恢复 clipPath 之前的状态，但保留 magnifierScale

        canvas.drawRoundRect(
            selectionMagnifierRect,
            cornerRadius,
            cornerRadius,
            selectionMagnifierBorderPaint,
        )
        
        // 绘制准星十字线 (0.5dp宽)
        selectionMagnifierCrosshairPaint.strokeWidth = 0.5f * density
        canvas.drawLine(
            lensCenterX, 
            selectionMagnifierRect.top, 
            lensCenterX, 
            selectionMagnifierRect.bottom, 
            selectionMagnifierCrosshairPaint
        )
        
        canvas.restore() // 恢复 magnifierScale 动画状态
    }

}
