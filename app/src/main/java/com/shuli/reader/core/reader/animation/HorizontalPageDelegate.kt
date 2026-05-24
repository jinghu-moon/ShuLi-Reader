package com.shuli.reader.core.reader.animation

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.canvasrecorder.CanvasRecorder

/**
 * 水平平移翻页委托
 */
class HorizontalPageDelegate : PageDelegate {

    override var state: PageDelegate.State = PageDelegate.State.IDLE
        private set

    override var direction: PageDelegate.Direction = PageDelegate.Direction.NONE
        private set

    private var callback: PageDelegate.Callback? = null
    private var startX: Float = 0f
    private var currentX: Float = 0f
    private var screenWidth: Float = 1080f
    private var isCancel: Boolean = false

    private val shadowPaint = Paint().apply {
        color = 0x40000000
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var animationProgress: Float = 0f

    override fun setCallback(callback: PageDelegate.Callback) {
        this.callback = callback
    }

    override fun onTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                abort()
                startX = event.x
                currentX = event.x
                state = PageDelegate.State.DRAGGING
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == PageDelegate.State.DRAGGING) {
                    currentX = event.x
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
                        direction = if (distance < 0) {
                            PageDelegate.Direction.NEXT
                        } else {
                            PageDelegate.Direction.PREV
                        }
                    } else {
                        isCancel = true
                        direction = if (distance < 0) {
                            PageDelegate.Direction.PREV
                        } else {
                            PageDelegate.Direction.NEXT
                        }
                    }
                    startAnimation()
                }
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder) {
        screenWidth = canvas.width.toFloat()

        when (state) {
            PageDelegate.State.IDLE -> {
                current.draw(canvas)
            }
            PageDelegate.State.DRAGGING -> {
                val offset = currentX - startX
                canvas.save()
                canvas.translate(offset, 0f)
                current.draw(canvas)
                canvas.restore()

                val targetOffset = if (offset < 0) offset + screenWidth else offset - screenWidth
                canvas.save()
                canvas.translate(targetOffset, 0f)
                target.draw(canvas)
                canvas.restore()

                val shadowX = if (offset < 0) offset + screenWidth else offset
                val shadowRect = RectF(shadowX - 20, 0f, shadowX, canvas.height.toFloat())
                canvas.drawRect(shadowRect, shadowPaint)
            }
            PageDelegate.State.ANIMATING -> {
                val offset = if (direction == PageDelegate.Direction.NEXT) {
                    -screenWidth * animationProgress
                } else {
                    screenWidth * animationProgress
                }
                canvas.save()
                canvas.translate(offset, 0f)
                current.draw(canvas)
                canvas.restore()

                val nextOffset = if (direction == PageDelegate.Direction.NEXT) {
                    offset + screenWidth
                } else {
                    offset - screenWidth
                }
                canvas.save()
                canvas.translate(nextOffset, 0f)
                target.draw(canvas)
                canvas.restore()
            }
        }
    }

    override fun startNext() {
        isCancel = false
        direction = PageDelegate.Direction.NEXT
        startAnimation()
    }

    override fun startPrev() {
        isCancel = false
        direction = PageDelegate.Direction.PREV
        startAnimation()
    }

    override fun abort() {
        animator?.cancel()
        animator = null
        state = PageDelegate.State.IDLE
        direction = PageDelegate.Direction.NONE
        animationProgress = 0f
    }

    override fun isDraggingBackward(): Boolean {
        return currentX > startX
    }

    private fun startAnimation() {
        state = PageDelegate.State.ANIMATING
        val shouldNotify = !isCancel

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ReaderMotionTokens.MEDIUM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                animationProgress = anim.animatedValue as Float
                callback?.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    state = PageDelegate.State.IDLE
                    animationProgress = 0f
                    if (shouldNotify) {
                        callback?.onPageChanged(direction)
                    }
                }
            })
            start()
        }
    }
}
