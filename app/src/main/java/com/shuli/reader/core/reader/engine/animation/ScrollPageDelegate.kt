package com.shuli.reader.core.reader.engine.animation

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.recorder.CanvasRecorder
import kotlin.math.abs

/**
 * 连续滚动委托。
 *
 * 该模式没有“拖动不足阈值则回弹”的翻页语义。用户松手后保留当前正文偏移，
 * 只有累计滚过当前正文流片段高度时才提交内部页引用，用分页模型模拟连续正文流。
 */
class ScrollPageDelegate(
    private val durationMs: Long = ReaderMotionTokens.MEDIUM_MS,
) : BodyScrollPageDelegate {

    override var state: PageDelegate.State = PageDelegate.State.IDLE
        private set

    override var direction: PageDelegate.Direction = PageDelegate.Direction.NONE
        private set

    private var callback: PageDelegate.Callback? = null
    private var startY: Float = 0f
    private var currentY: Float = 0f
    private var dragStartOffset: Float = 0f
    private var scrollOffset: Float = 0f
    private var nextScrollExtent: Float = 1920f
    private var prevScrollExtent: Float = 1920f

    private var animator: ValueAnimator? = null
    private var isAborting = false

    override fun setCallback(callback: PageDelegate.Callback) {
        this.callback = callback
    }

    override fun onTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                cancelAnimator()
                startY = event.y
                currentY = event.y
                dragStartOffset = scrollOffset
                direction = directionForOffset(scrollOffset)
                state = PageDelegate.State.DRAGGING
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == PageDelegate.State.DRAGGING) {
                    currentY = event.y
                    setContinuousOffset(dragStartOffset + currentY - startY)
                    callback?.invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state == PageDelegate.State.DRAGGING) {
                    currentY = event.y
                    setContinuousOffset(dragStartOffset + currentY - startY)
                    settleCompletedPage()
                    callback?.invalidate()
                }
                return true
            }
        }
        return false
    }

    private fun setContinuousOffset(offset: Float) {
        scrollOffset = offset.coerceIn(-nextScrollExtent, prevScrollExtent)
        direction = directionForOffset(scrollOffset)
    }

    private fun settleCompletedPage() {
        val completedDirection = when {
            scrollOffset <= -nextScrollExtent -> PageDelegate.Direction.NEXT
            scrollOffset >= prevScrollExtent -> PageDelegate.Direction.PREV
            else -> PageDelegate.Direction.NONE
        }

        if (completedDirection == PageDelegate.Direction.NONE) {
            state = PageDelegate.State.IDLE
            return
        }

        direction = completedDirection
        state = PageDelegate.State.SETTLING
        callback?.onPageChanged(completedDirection)
    }

    private fun directionForOffset(offset: Float): PageDelegate.Direction {
        return when {
            offset > 0f -> PageDelegate.Direction.PREV
            offset < 0f -> PageDelegate.Direction.NEXT
            else -> PageDelegate.Direction.NONE
        }
    }

    override fun confirmPageSettled() {
        if (state == PageDelegate.State.SETTLING) {
            scrollOffset = 0f
            dragStartOffset = 0f
            state = PageDelegate.State.IDLE
            direction = PageDelegate.Direction.NONE
        }
    }

    override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder) {
        setViewportHeight(canvas.height.toFloat())

        if (scrollOffset == 0f && state == PageDelegate.State.IDLE) {
            current.draw(canvas)
            return
        }

        canvas.save()
        canvas.translate(0f, scrollOffset)
        current.draw(canvas)
        canvas.restore()

        val targetOffset = if (isDraggingBackward()) {
            scrollOffset - prevScrollExtent
        } else {
            scrollOffset + nextScrollExtent
        }
        canvas.save()
        canvas.translate(0f, targetOffset)
        target.draw(canvas)
        canvas.restore()
    }

    override fun startNext() {
        startScrollAnimation(PageDelegate.Direction.NEXT)
    }

    override fun startPrev() {
        startScrollAnimation(PageDelegate.Direction.PREV)
    }

    private fun startScrollAnimation(targetDirection: PageDelegate.Direction) {
        direction = targetDirection
        state = PageDelegate.State.ANIMATING
        val startOffset = scrollOffset
        val targetOffset = when (targetDirection) {
            PageDelegate.Direction.NEXT -> -nextScrollExtent
            PageDelegate.Direction.PREV -> prevScrollExtent
            PageDelegate.Direction.NONE -> 0f
        }
        val targetExtent = when (targetDirection) {
            PageDelegate.Direction.PREV -> prevScrollExtent
            else -> nextScrollExtent
        }
        val fraction = (abs(targetOffset - startOffset) / targetExtent.coerceAtLeast(1f)).coerceIn(0f, 1f)

        cancelAnimator()
        animator = ValueAnimator.ofFloat(startOffset, targetOffset).apply {
            duration = (durationMs * fraction).toLong().coerceAtLeast(50L)
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                scrollOffset = anim.animatedValue as Float
                direction = targetDirection
                callback?.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isAborting) return
                    state = PageDelegate.State.SETTLING
                    callback?.onPageChanged(targetDirection)
                }
            })
            start()
        }
    }

    override fun abort() {
        isAborting = true
        animator?.cancel()
        isAborting = false
        animator = null
        state = PageDelegate.State.IDLE
        direction = PageDelegate.Direction.NONE
        scrollOffset = 0f
        dragStartOffset = 0f
    }

    override fun isDraggingBackward(): Boolean {
        return if (scrollOffset == 0f) {
            direction == PageDelegate.Direction.PREV
        } else {
            scrollOffset > 0f
        }
    }

    override fun getScrollPosition(): Float = scrollOffset

    override fun getViewportHeight(): Float = currentScrollExtent()

    override fun setViewportHeight(height: Float) {
        setScrollExtents(forwardExtent = height, backwardExtent = height)
    }

    fun setScrollExtents(forwardExtent: Float, backwardExtent: Float) {
        val newNextExtent = forwardExtent.coerceAtLeast(1f)
        val newPrevExtent = backwardExtent.coerceAtLeast(1f)
        if (newNextExtent == nextScrollExtent && newPrevExtent == prevScrollExtent) return

        scrollOffset = rescaleOffset(scrollOffset, newNextExtent, newPrevExtent)
        dragStartOffset = rescaleOffset(dragStartOffset, newNextExtent, newPrevExtent)
        nextScrollExtent = newNextExtent
        prevScrollExtent = newPrevExtent
    }

    fun setScrollPosition(position: Float, active: Boolean = false) {
        cancelAnimator()
        scrollOffset = position.coerceIn(-nextScrollExtent, prevScrollExtent)
        dragStartOffset = scrollOffset
        direction = directionForOffset(scrollOffset)
        state = if (active && scrollOffset != 0f) {
            PageDelegate.State.DRAGGING
        } else {
            PageDelegate.State.IDLE
        }
    }

    fun resetScrollPosition() {
        cancelAnimator()
        animator = null
        scrollOffset = 0f
        dragStartOffset = 0f
        direction = PageDelegate.Direction.NONE
        state = PageDelegate.State.IDLE
    }

    private fun cancelAnimator() {
        isAborting = true
        animator?.cancel()
        isAborting = false
        animator = null
    }

    private fun currentScrollExtent(): Float {
        return when {
            scrollOffset > 0f -> prevScrollExtent
            scrollOffset < 0f -> nextScrollExtent
            direction == PageDelegate.Direction.PREV -> prevScrollExtent
            else -> nextScrollExtent
        }
    }

    private fun rescaleOffset(offset: Float, newNextExtent: Float, newPrevExtent: Float): Float {
        return when {
            offset > 0f -> {
                val ratio = offset / prevScrollExtent.coerceAtLeast(1f)
                (ratio * newPrevExtent).coerceAtMost(newPrevExtent)
            }
            offset < 0f -> {
                val ratio = offset / nextScrollExtent.coerceAtLeast(1f)
                (ratio * newNextExtent).coerceAtLeast(-newNextExtent)
            }
            else -> 0f
        }
    }
}
