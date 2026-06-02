package com.shuli.reader.core.reader.animation

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.canvasrecorder.CanvasRecorder

/**
 * 覆盖翻页委托
 *
 * 使用统一的 pageOffset 变量驱动 DRAGGING 和 ANIMATING 渲染，
 * 覆盖效果：当前页覆盖在目标页上方滑动。
 */
class CoverPageDelegate : PageDelegate {

    override var state: PageDelegate.State = PageDelegate.State.IDLE
        private set

    override var direction: PageDelegate.Direction = PageDelegate.Direction.NONE
        private set

    private var callback: PageDelegate.Callback? = null
    private var startX: Float = 0f
    private var currentX: Float = 0f
    private var screenWidth: Float = 1080f
    private var isCancel: Boolean = false

    /** 统一位移量，负值向左（NEXT），正值向右（PREV） */
    private var pageOffset: Float = 0f

    private val shadowPaint = Paint().apply {
        color = 0x40000000
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var isAborting = false

    override fun setCallback(callback: PageDelegate.Callback) {
        this.callback = callback
    }

    override fun onTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                abort()
                startX = event.x
                currentX = event.x
                pageOffset = 0f
                state = PageDelegate.State.DRAGGING
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == PageDelegate.State.DRAGGING) {
                    currentX = event.x
                    pageOffset = currentX - startX
                    direction = if (pageOffset > 0) PageDelegate.Direction.PREV else PageDelegate.Direction.NEXT
                    callback?.invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (state == PageDelegate.State.DRAGGING) {
                    val distance = currentX - startX
                    val threshold = screenWidth / 3

                    if (Math.abs(distance) > threshold) {
                        isCancel = false
                    } else {
                        isCancel = true
                    }
                    startAnimation()
                }
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder, drawTarget: Boolean) {
        screenWidth = canvas.width.toFloat()

        when (state) {
            PageDelegate.State.IDLE -> {
                current.draw(canvas)
            }
            PageDelegate.State.SETTLING -> {
                if (drawTarget) target.draw(canvas)
            }
            PageDelegate.State.DRAGGING, PageDelegate.State.ANIMATING -> {
                if (drawTarget) {
                    target.draw(canvas)
                }
                canvas.save()
                canvas.translate(pageOffset, 0f)
                current.draw(canvas)
                canvas.restore()
                if (drawTarget) {
                    val shadowRect = if (pageOffset < 0) {
                        RectF(pageOffset + screenWidth, 0f, screenWidth, canvas.height.toFloat())
                    } else {
                        RectF(0f, 0f, pageOffset, canvas.height.toFloat())
                    }
                    canvas.drawRect(shadowRect, shadowPaint)
                }
            }
        }
    }

    override fun startNext() {
        isCancel = false
        direction = PageDelegate.Direction.NEXT
        pageOffset = 0f
        startAnimation()
    }

    override fun startPrev() {
        isCancel = false
        direction = PageDelegate.Direction.PREV
        pageOffset = 0f
        startAnimation()
    }

    override fun confirmPageSettled() {
        if (state == PageDelegate.State.SETTLING) {
            state = PageDelegate.State.IDLE
            direction = PageDelegate.Direction.NONE
            pageOffset = 0f
        }
    }

    override fun abort() {
        val wasCommitted = state == PageDelegate.State.ANIMATING && !isCancel
        val prevDirection = direction

        isAborting = true
        animator?.cancel()
        isAborting = false
        animator = null
        state = PageDelegate.State.IDLE
        direction = PageDelegate.Direction.NONE
        pageOffset = 0f

        if (wasCommitted && prevDirection != PageDelegate.Direction.NONE) {
            callback?.onPageChanged(prevDirection)
        }
    }

    override fun isDraggingBackward(): Boolean {
        return pageOffset > 0
    }

    private fun startAnimation() {
        state = PageDelegate.State.ANIMATING
        val shouldNotify = !isCancel

        val currentOffset = pageOffset
        val targetOffset = if (isCancel) {
            0f
        } else {
            if (direction == PageDelegate.Direction.NEXT) -screenWidth else screenWidth
        }
        val fraction = (Math.abs(targetOffset - currentOffset) / screenWidth).coerceIn(0f, 1f)

        animator?.cancel()
        animator = ValueAnimator.ofFloat(currentOffset, targetOffset).apply {
            duration = (ReaderMotionTokens.MEDIUM_MS * fraction).toLong().coerceAtLeast(50L)
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                pageOffset = anim.animatedValue as Float
                callback?.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isAborting) return
                    if (shouldNotify) {
                        state = PageDelegate.State.SETTLING
                        callback?.onPageChanged(direction)
                    } else {
                        state = PageDelegate.State.IDLE
                        direction = PageDelegate.Direction.NONE
                        pageOffset = 0f
                    }
                }
            })
            start()
        }
    }
}
