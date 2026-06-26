package com.shuli.reader.core.reader.engine.animation

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.recorder.CanvasRecorder
import kotlin.math.abs

/**
 * 连续滚动委托（真正的连续正文流，对标 legado [ContentTextView]）。
 *
 * 与"双页交换"模型不同：这里只维护一个自由累加的 [scrollOffset]，绘制交给
 * [ReaderCanvasView] 用页面序列按实际高度堆叠 N 页填满视口。委托本身只负责：
 *  1. 把触摸/惯性滑动转换成连续偏移量；
 *  2. 当偏移量越过当前页"实际内容高度"边界时，通过 [Pager] 回收页面（前进/后退一页），
 *     并把残余偏移量平移到新的当前页坐标系，从而实现首尾相连、无段落空白的滚动。
 *
 * 章节之间的间隔严格由排版（[ScrollBodyFlowLayout] 的章末 padding）决定，
 * 不再受"整屏 settle 距离"绑架。
 */
class ScrollPageDelegate(
    private val durationMs: Long = ReaderMotionTokens.MEDIUM_MS,
) : BodyScrollPageDelegate {

    /**
     * 页面回收器。由持有页面序列的视图（[ReaderCanvasView]）实现。
     *
     * 坐标约定：[scrollOffset] == 0 表示"当前页顶部"对齐视口顶部；
     * 负值表示向下滚动（正文向上移动），正值表示向上滚动（正文向下移动）。
     */
    interface Pager {
        /** 当前页的实际内容流高度（含章末间距），用于判定回收边界。 */
        fun currentSegmentHeight(): Float

        /** 视口（正文盒）高度，用于书末越界钳制。 */
        fun viewportHeight(): Float

        /** 把"当前页"前进一页（当前页 ← 下一页）。无下一页返回 false。 */
        fun advanceForward(): Boolean

        /** 把"当前页"后退一页（当前页 ← 上一页）。无上一页返回 false。 */
        fun advanceBackward(): Boolean
    }

    var pager: Pager? = null

    override var state: PageDelegate.State = PageDelegate.State.IDLE
        private set

    override var direction: PageDelegate.Direction = PageDelegate.Direction.NONE
        private set

    private var callback: PageDelegate.Callback? = null

    /** 当前页坐标系下的连续滚动偏移量。维持在 (-currentSegmentHeight, 0] 区间（回收后）。 */
    private var scrollOffset: Float = 0f

    /** 视口高度（用于书首/书末越界钳制）。 */
    private var viewportHeightPx: Float = 1920f

    private var lastTouchY: Float = 0f
    private var velocityTracker: VelocityTracker? = null

    private var flingAnimator: ValueAnimator? = null
    private var lastFlingValue: Float = 0f
    private var isAborting = false

    override fun setCallback(callback: PageDelegate.Callback) {
        this.callback = callback
    }

    // ── 触摸 ──────────────────────────────────────────────────

    override fun onTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                cancelFling()
                lastTouchY = event.y
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                state = PageDelegate.State.DRAGGING
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state != PageDelegate.State.DRAGGING) return true
                velocityTracker?.addMovement(event)
                val dy = event.y - lastTouchY
                lastTouchY = event.y
                applyScroll(dy)
                callback?.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state != PageDelegate.State.DRAGGING) return true
                velocityTracker?.addMovement(event)
                val velocityY = velocityTracker?.let {
                    it.computeCurrentVelocity(1000)
                    it.yVelocity
                } ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null
                if (abs(velocityY) > MIN_FLING_VELOCITY) {
                    startFling(velocityY)
                } else {
                    state = PageDelegate.State.IDLE
                    direction = PageDelegate.Direction.NONE
                }
                return true
            }
        }
        return false
    }

    /**
     * 应用一段滚动位移并按实际页高度回收页面。
     *
     * 对标 legado [ContentTextView.scroll]：先累加偏移，再在越过当前页边界时
     * 调用 [Pager] 切换当前页并把偏移量平移到新页坐标系。
     */
    private fun applyScroll(dy: Float) {
        val pager = pager ?: run {
            scrollOffset += dy
            return
        }
        scrollOffset += dy

        var guard = 0
        // 向下滚动（正文上移）：偏移量越过当前页底边，回收到下一页
        while (scrollOffset <= -pager.currentSegmentHeight() && guard++ < RECYCLE_GUARD) {
            val consumed = pager.currentSegmentHeight()
            if (pager.advanceForward()) {
                scrollOffset += consumed
            } else {
                // 书末：钳制在"内容底部对齐视口底部"或顶部，不允许继续上移留白
                val maxUp = -(pager.currentSegmentHeight() - pager.viewportHeight()).coerceAtLeast(0f)
                scrollOffset = scrollOffset.coerceAtLeast(maxUp)
                cancelFling()
                break
            }
        }
        // 向上滚动（正文下移）：偏移量越过当前页顶边，回收到上一页
        while (scrollOffset > 0f && guard++ < RECYCLE_GUARD) {
            if (pager.advanceBackward()) {
                // 平移到新当前页坐标系：新页顶部应位于视口上方一个新页高度处
                scrollOffset -= pager.currentSegmentHeight()
            } else {
                // 书首：钳制顶部对齐
                scrollOffset = 0f
                cancelFling()
                break
            }
        }
        direction = directionForOffset(scrollOffset)
    }

    private fun directionForOffset(offset: Float): PageDelegate.Direction = when {
        offset < 0f -> PageDelegate.Direction.NEXT
        offset > 0f -> PageDelegate.Direction.PREV
        else -> PageDelegate.Direction.NONE
    }

    // ── 惯性滑动 ──────────────────────────────────────────────

    private fun startFling(velocityY: Float) {
        cancelFling()
        state = PageDelegate.State.ANIMATING
        val distance = (velocityY * FLING_DISTANCE_SCALE)
            .coerceIn(-MAX_FLING_DISTANCE, MAX_FLING_DISTANCE)
        val duration = (abs(distance) / MAX_FLING_DISTANCE * durationMs * FLING_DURATION_FACTOR)
            .toLong().coerceIn(80L, 1200L)
        lastFlingValue = 0f
        flingAnimator = ValueAnimator.ofFloat(0f, distance).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                val delta = value - lastFlingValue
                lastFlingValue = value
                applyScroll(delta)
                callback?.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isAborting) return
                    state = PageDelegate.State.IDLE
                    direction = PageDelegate.Direction.NONE
                }
            })
            start()
        }
    }

    private fun cancelFling() {
        isAborting = true
        flingAnimator?.cancel()
        isAborting = false
        flingAnimator = null
    }

    // ── 绘制（连续流由 ReaderCanvasView 负责，这里只兜底单页） ──

    override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder) {
        // 连续滚动的真实绘制走 ReaderCanvasView.drawContinuousFlow；
        // 此方法仅在异常兜底路径被调用，简单绘制当前页。
        current.draw(canvas)
    }

    // ── 程序化翻页（手势热区/边缘翻页触发） ──────────────────────

    override fun startNext() {
        cancelFling()
        val target = pager?.currentSegmentHeight() ?: viewportHeightPx
        animateBy(-target)
    }

    override fun startPrev() {
        cancelFling()
        // 后退一整页：先回收到上一页再滚到其顶部
        val pager = pager
        if (pager != null && scrollOffset == 0f) {
            if (!pager.advanceBackward()) return
            scrollOffset = -pager.currentSegmentHeight()
        }
        animateBy(-scrollOffset)
    }

    private fun animateBy(distance: Float) {
        cancelFling()
        state = PageDelegate.State.ANIMATING
        lastFlingValue = 0f
        flingAnimator = ValueAnimator.ofFloat(0f, distance).apply {
            duration = durationMs.coerceAtLeast(80L)
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                val delta = value - lastFlingValue
                lastFlingValue = value
                applyScroll(delta)
                callback?.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isAborting) return
                    state = PageDelegate.State.IDLE
                    direction = PageDelegate.Direction.NONE
                }
            })
            start()
        }
    }

    override fun abort() {
        cancelFling()
        velocityTracker?.recycle()
        velocityTracker = null
        state = PageDelegate.State.IDLE
        direction = PageDelegate.Direction.NONE
    }

    /** 连续流模型无 SETTLING 状态，页面回收即时完成，无需收尾。 */
    override fun confirmPageSettled() { /* no-op */ }

    override fun isDraggingBackward(): Boolean = scrollOffset > 0f

    // ── BodyScrollPageDelegate ────────────────────────────────

    override fun getScrollPosition(): Float = scrollOffset

    override fun getViewportHeight(): Float = viewportHeightPx

    override fun setViewportHeight(height: Float) {
        viewportHeightPx = height.coerceAtLeast(1f)
    }

    /**
     * 兼容旧调用点（[ReaderCanvasView.updateBodyScrollExtent]）。
     * 连续流模型不再区分前后 extent，仅用于更新视口高度。
     */
    fun setScrollExtents(forwardExtent: Float, backwardExtent: Float) {
        viewportHeightPx = maxOf(forwardExtent, backwardExtent).coerceAtLeast(1f)
    }

    /** 编辑避让：直接设置滚动偏移（不经过回收）。 */
    fun setScrollPosition(position: Float, active: Boolean = false) {
        cancelFling()
        scrollOffset = position
        direction = directionForOffset(scrollOffset)
        state = if (active && scrollOffset != 0f) PageDelegate.State.DRAGGING else PageDelegate.State.IDLE
    }

    fun resetScrollPosition() {
        cancelFling()
        scrollOffset = 0f
        direction = PageDelegate.Direction.NONE
        state = PageDelegate.State.IDLE
    }

    companion object {
        private const val MIN_FLING_VELOCITY = 80f
        private const val FLING_DISTANCE_SCALE = 0.35f
        private const val MAX_FLING_DISTANCE = 12000f
        private const val FLING_DURATION_FACTOR = 1.4f
        private const val RECYCLE_GUARD = 4096
    }
}
