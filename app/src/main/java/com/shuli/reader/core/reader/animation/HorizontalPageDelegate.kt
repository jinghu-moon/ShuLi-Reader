package com.shuli.reader.core.reader.animation

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.canvasrecorder.CanvasRecorder

/**
 * 水平平移翻页委托
 *
 * 使用统一的 pageOffset 变量驱动 DRAGGING 和 ANIMATING 渲染，
 * 避免拖拽→动画过渡时的视觉跳跃，并确保取消动画使用正确的目标页。
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

    /** 统一位移量，DRAGGING 和 ANIMATING 共用，负值向左（NEXT），正值向右（PREV） */
    private var pageOffset: Float = 0f

    /** 翻页边缘渐变阴影（30px 宽，从半透明黑到透明） */
    private val shadowDrawableR = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0x66111111, 0x00000000),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }

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
                    // direction 始终表示目标页方向（用于 ReaderCanvasView 选取正确 target 页）
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
                    // direction 已在 ACTION_MOVE 中根据 pageOffset 设置，保持不变
                    // 这样无论 commit 还是 cancel，target 页不会跳变
                    startAnimation()
                }
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder) {
        screenWidth = canvas.width.toFloat()
        // 更新渐变阴影尺寸（30px 宽 × 全高）
        shadowDrawableR.setBounds(0, 0, 30, canvas.height)

        when (state) {
            PageDelegate.State.IDLE -> {
                current.draw(canvas)
            }
            PageDelegate.State.SETTLING -> {
                target.draw(canvas)
            }
            PageDelegate.State.DRAGGING, PageDelegate.State.ANIMATING -> {
                // DRAGGING 和 ANIMATING 共用 pageOffset 驱动渲染
                canvas.save()
                canvas.translate(pageOffset, 0f)
                current.draw(canvas)
                canvas.restore()

                val targetOffset = if (pageOffset < 0) pageOffset + screenWidth else pageOffset - screenWidth
                canvas.save()
                canvas.translate(targetOffset, 0f)
                target.draw(canvas)
                canvas.restore()

                // 渐变阴影条：紧贴两页交界处
                val shadowX = if (pageOffset < 0) pageOffset + screenWidth else pageOffset
                canvas.save()
                canvas.translate(shadowX, 0f)
                shadowDrawableR.draw(canvas)
                canvas.restore()
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

        // Like Legado's fillPage: if committed animation was interrupted, still commit page
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
