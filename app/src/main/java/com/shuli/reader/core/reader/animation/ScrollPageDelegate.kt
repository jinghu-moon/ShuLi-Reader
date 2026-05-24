package com.shuli.reader.core.reader.animation

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.canvasrecorder.CanvasRecorder

/**
 * 垂直滚动翻页委托
 * 支持连续滚动阅读模式
 */
class ScrollPageDelegate : PageDelegate {

    override var state: PageDelegate.State = PageDelegate.State.IDLE
        private set

    override var direction: PageDelegate.Direction = PageDelegate.Direction.NONE
        private set

    private var callback: PageDelegate.Callback? = null
    private var startY: Float = 0f
    private var currentY: Float = 0f
    private var scrollOffset: Float = 0f
    private var screenHeight: Float = 1920f

    private var animator: ValueAnimator? = null

    override fun setCallback(callback: PageDelegate.Callback) {
        this.callback = callback
    }

    override fun onTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                currentY = event.y
                state = PageDelegate.State.DRAGGING
                animator?.cancel()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == PageDelegate.State.DRAGGING) {
                    val deltaY = event.y - currentY
                    currentY = event.y
                    scrollOffset += deltaY
                    callback?.invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (state == PageDelegate.State.DRAGGING) {
                    val velocity = (event.y - startY) / 100f

                    if (Math.abs(velocity) > 1f) {
                        startInertiaScroll(velocity)
                    } else {
                        state = PageDelegate.State.IDLE
                    }
                }
                return true
            }
        }
        return false
    }

    private fun startInertiaScroll(velocity: Float) {
        state = PageDelegate.State.ANIMATING
        val startOffset = scrollOffset
        val targetOffset = scrollOffset + velocity * 500f

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ReaderMotionTokens.LONG_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                scrollOffset = startOffset + (targetOffset - startOffset) * progress
                callback?.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    state = PageDelegate.State.IDLE
                    checkChapterBoundary()
                }
            })
            start()
        }
    }

    private fun checkChapterBoundary() {
        if (scrollOffset < -screenHeight) {
            direction = PageDelegate.Direction.NEXT
            callback?.onPageChanged(direction)
            scrollOffset = 0f
        } else if (scrollOffset > screenHeight) {
            direction = PageDelegate.Direction.PREV
            callback?.onPageChanged(direction)
            scrollOffset = 0f
        }
    }

    override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder) {
        screenHeight = canvas.height.toFloat()

        canvas.save()
        canvas.translate(0f, scrollOffset)
        current.draw(canvas)
        canvas.restore()

        if (scrollOffset < -screenHeight * 0.8f) {
            canvas.save()
            canvas.translate(0f, scrollOffset + screenHeight)
            target.draw(canvas)
            canvas.restore()
        } else if (scrollOffset > screenHeight * 0.8f) {
            canvas.save()
            canvas.translate(0f, scrollOffset - screenHeight)
            target.draw(canvas)
            canvas.restore()
        }
    }

    override fun startNext() {
        startScrollAnimation(-screenHeight)
    }

    override fun startPrev() {
        startScrollAnimation(screenHeight)
    }

    private fun startScrollAnimation(targetDelta: Float) {
        state = PageDelegate.State.ANIMATING
        val startOffset = scrollOffset
        val targetOffset = scrollOffset + targetDelta

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ReaderMotionTokens.MEDIUM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float
                scrollOffset = startOffset + (targetOffset - startOffset) * progress
                callback?.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    state = PageDelegate.State.IDLE
                    checkChapterBoundary()
                }
            })
            start()
        }
    }

    override fun abort() {
        animator?.cancel()
        animator = null
        state = PageDelegate.State.IDLE
        direction = PageDelegate.Direction.NONE
    }

    override fun isDraggingBackward(): Boolean {
        return currentY < startY
    }

    fun getScrollPosition(): Float = scrollOffset

    fun setScrollPosition(position: Float) {
        scrollOffset = position
    }
}
