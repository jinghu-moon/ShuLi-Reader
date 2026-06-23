package com.shuli.reader.core.reader.engine.animation

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.recorder.CanvasRecorder
import kotlin.math.abs

/**
 * 上下滑动翻页委托。
 *
 * 这是离散翻页动画：拖动不足阈值会回到当前页，点击/按键可触发上一页或下一页。
 */
class VerticalSlidePageDelegate(
    private val durationMs: Long = ReaderMotionTokens.MEDIUM_MS,
) : BodyScrollPageDelegate {

    override var state: PageDelegate.State = PageDelegate.State.IDLE
        private set

    override var direction: PageDelegate.Direction = PageDelegate.Direction.NONE
        private set

    private var callback: PageDelegate.Callback? = null
    private var startY: Float = 0f
    private var currentY: Float = 0f
    private var scrollOffset: Float = 0f
    private var viewportHeight: Float = 1920f
    private var isCancel: Boolean = false

    private var animator: ValueAnimator? = null
    private var isAborting = false

    override fun setCallback(callback: PageDelegate.Callback) {
        this.callback = callback
    }

    override fun onTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                abort()
                startY = event.y
                currentY = event.y
                scrollOffset = 0f
                isCancel = false
                state = PageDelegate.State.DRAGGING
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == PageDelegate.State.DRAGGING) {
                    currentY = event.y
                    scrollOffset = currentY - startY
                    direction = directionForOffset(scrollOffset)
                    callback?.invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (state == PageDelegate.State.DRAGGING) {
                    currentY = event.y
                    scrollOffset = currentY - startY
                    if (abs(scrollOffset) < MIN_DRAG_DISTANCE_PX) {
                        scrollOffset = 0f
                        direction = PageDelegate.Direction.NONE
                        state = PageDelegate.State.IDLE
                        callback?.invalidate()
                    } else {
                        direction = directionForOffset(scrollOffset)
                        isCancel = abs(scrollOffset) <= viewportHeight / 3f
                        startScrollAnimation()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (state == PageDelegate.State.DRAGGING || state == PageDelegate.State.ANIMATING) {
                    isCancel = true
                    startScrollAnimation()
                }
                return true
            }
        }
        return false
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
            state = PageDelegate.State.IDLE
            direction = PageDelegate.Direction.NONE
        }
    }

    override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder) {
        setViewportHeight(canvas.height.toFloat())

        when (state) {
            PageDelegate.State.IDLE -> {
                current.draw(canvas)
            }
            PageDelegate.State.SETTLING -> {
                target.draw(canvas)
            }
            PageDelegate.State.DRAGGING, PageDelegate.State.ANIMATING -> {
                canvas.save()
                canvas.translate(0f, scrollOffset)
                current.draw(canvas)
                canvas.restore()

                val targetOffset = if (isPrevDirection()) {
                    scrollOffset - viewportHeight
                } else {
                    scrollOffset + viewportHeight
                }
                canvas.save()
                canvas.translate(0f, targetOffset)
                target.draw(canvas)
                canvas.restore()
            }
        }
    }

    override fun startNext() {
        isCancel = false
        direction = PageDelegate.Direction.NEXT
        scrollOffset = 0f
        startScrollAnimation()
    }

    override fun startPrev() {
        isCancel = false
        direction = PageDelegate.Direction.PREV
        scrollOffset = 0f
        startScrollAnimation()
    }

    private fun startScrollAnimation() {
        state = PageDelegate.State.ANIMATING
        val shouldNotify = !isCancel && direction != PageDelegate.Direction.NONE
        val startOffset = scrollOffset
        val targetOffset = when {
            isCancel || direction == PageDelegate.Direction.NONE -> 0f
            direction == PageDelegate.Direction.NEXT -> -viewportHeight
            else -> viewportHeight
        }
        val fraction = (abs(targetOffset - startOffset) / viewportHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)

        cancelAnimator()
        animator = ValueAnimator.ofFloat(startOffset, targetOffset).apply {
            duration = (durationMs * fraction).toLong().coerceAtLeast(50L)
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                scrollOffset = anim.animatedValue as Float
                callback?.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isAborting) return
                    if (shouldNotify) {
                        state = PageDelegate.State.SETTLING
                        callback?.onPageChanged(direction)
                    } else {
                        scrollOffset = 0f
                        state = PageDelegate.State.IDLE
                        direction = PageDelegate.Direction.NONE
                    }
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
        isCancel = false
    }

    override fun isDraggingBackward(): Boolean {
        return isPrevDirection()
    }

    override fun getScrollPosition(): Float = scrollOffset

    override fun getViewportHeight(): Float = viewportHeight

    override fun setViewportHeight(height: Float) {
        val newHeight = height.coerceAtLeast(1f)
        if (newHeight == viewportHeight) return
        val oldHeight = viewportHeight.coerceAtLeast(1f)
        val offsetRatio = scrollOffset / oldHeight
        viewportHeight = newHeight
        if (state != PageDelegate.State.IDLE && scrollOffset != 0f) {
            scrollOffset = offsetRatio * viewportHeight
        }
    }

    fun setScrollPosition(position: Float, active: Boolean = false) {
        cancelAnimator()
        scrollOffset = position
        direction = directionForOffset(position)
        if (active && position != 0f) {
            state = PageDelegate.State.DRAGGING
            currentY = startY + position
        } else if (position == 0f) {
            state = PageDelegate.State.IDLE
            direction = PageDelegate.Direction.NONE
        }
    }

    fun resetScrollPosition() {
        cancelAnimator()
        animator = null
        scrollOffset = 0f
        direction = PageDelegate.Direction.NONE
        state = PageDelegate.State.IDLE
        isCancel = false
    }

    private fun isPrevDirection(): Boolean {
        return if (scrollOffset == 0f) {
            direction == PageDelegate.Direction.PREV
        } else {
            scrollOffset > 0f
        }
    }

    private fun cancelAnimator() {
        isAborting = true
        animator?.cancel()
        isAborting = false
        animator = null
    }

    companion object {
        private const val MIN_DRAG_DISTANCE_PX = 24f
    }
}

