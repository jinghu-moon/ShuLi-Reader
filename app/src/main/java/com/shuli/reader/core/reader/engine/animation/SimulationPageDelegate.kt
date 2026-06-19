package com.shuli.reader.core.reader.engine.animation

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.RectF
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.recorder.CanvasRecorder

/**
 * 仿真翻页委托
 * 实现贝塞尔曲线控制的卷页效果
 */
class SimulationPageDelegate(
    private val durationMs: Long = ReaderMotionTokens.LONG_MS,
) : PageDelegate {

    override var state: PageDelegate.State = PageDelegate.State.IDLE
        private set

    override var direction: PageDelegate.Direction = PageDelegate.Direction.NONE
        private set

    private var callback: PageDelegate.Callback? = null
    private var startX: Float = 0f
    private var currentX: Float = 0f
    private var startY: Float = 0f
    private var currentY: Float = 0f
    private var screenWidth: Float = 1080f
    private var screenHeight: Float = 1920f
    private var isCancel: Boolean = false

    // 贝塞尔控制点
    private val touchPoint = PointF()
    private val bezierStart = PointF()
    private val bezierControl = PointF()
    private val bezierEnd = PointF()
    private val bezierVertex1 = PointF()
    private val bezierVertex2 = PointF()

    // 绘制画笔
    private val shadowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val backPaint = Paint().apply {
        color = 0xFFF5F5F5.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val path = Path()

    private var animator: ValueAnimator? = null
    private var animationProgress: Float = 0f
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
                startY = event.y
                currentY = event.y
                state = PageDelegate.State.DRAGGING
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == PageDelegate.State.DRAGGING) {
                    currentX = event.x
                    currentY = event.y
                    updateBezierPoints()
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

    private fun updateBezierPoints() {
        touchPoint.set(currentX, currentY)

        val offsetX = currentX - startX
        val offsetY = currentY - startY

        if (offsetX < 0) {
            bezierStart.set(screenWidth, startY)
            bezierEnd.set(screenWidth, startY + screenHeight * 0.5f)
        } else {
            bezierStart.set(0f, startY)
            bezierEnd.set(0f, startY + screenHeight * 0.5f)
        }

        bezierControl.set(
            currentX + offsetX * 0.5f,
            currentY + offsetY * 0.3f
        )

        bezierVertex1.set(
            (bezierStart.x + bezierControl.x) / 2,
            (bezierStart.y + bezierControl.y) / 2
        )
        bezierVertex2.set(
            (bezierControl.x + bezierEnd.x) / 2,
            (bezierControl.y + bezierEnd.y) / 2
        )
    }

    override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder) {
        screenWidth = canvas.width.toFloat()
        screenHeight = canvas.height.toFloat()

        when (state) {
            PageDelegate.State.IDLE -> {
                current.draw(canvas)
            }
            PageDelegate.State.SETTLING -> {
                target.draw(canvas)
            }
            PageDelegate.State.DRAGGING -> {
                drawSimulationPage(canvas, current, target, 1f)
            }
            PageDelegate.State.ANIMATING -> {
                drawSimulationPage(canvas, current, target, animationProgress)
            }
        }
    }

    private fun drawSimulationPage(
        canvas: Canvas,
        current: CanvasRecorder,
        target: CanvasRecorder,
        progress: Float
    ) {
        val offsetX = currentX - startX

        canvas.save()

        target.draw(canvas)

        path.reset()

        if (offsetX < 0) {
            val foldX = screenWidth + offsetX * progress

            path.moveTo(foldX, 0f)
            path.lineTo(screenWidth, 0f)
            path.lineTo(screenWidth, screenHeight)
            path.lineTo(foldX, screenHeight)
            path.close()

            canvas.save()
            canvas.clipPath(path)
            current.draw(canvas)
            canvas.restore()

            drawFoldShadow(canvas, foldX, progress)
            drawBackSide(canvas, foldX, progress)
        } else {
            val foldX = offsetX * progress

            path.moveTo(0f, 0f)
            path.lineTo(foldX, 0f)
            path.lineTo(foldX, screenHeight)
            path.lineTo(0f, screenHeight)
            path.close()

            canvas.save()
            canvas.clipPath(path)
            current.draw(canvas)
            canvas.restore()

            drawFoldShadow(canvas, foldX, progress)
            drawBackSide(canvas, foldX, progress)
        }

        canvas.restore()
    }

    // 预分配的最大尺寸折叠阴影渐变（左右各一），通过 LocalMatrix + Paint.alpha 变换
    private val shadowMatrix = android.graphics.Matrix()

    private val leftShadowGradient: LinearGradient = LinearGradient(
        0f, 0f, MAX_SHADOW_WIDTH, 0f,
        intArrayOf(0xFF000000.toInt(), android.graphics.Color.TRANSPARENT),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP,
    )

    private val rightShadowGradient: LinearGradient = LinearGradient(
        0f, 0f, MAX_SHADOW_WIDTH, 0f,
        intArrayOf(android.graphics.Color.TRANSPARENT, 0xFF000000.toInt()),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP,
    )

    private fun drawFoldShadow(canvas: Canvas, foldX: Float, progress: Float) {
        val shadowWidth = MAX_SHADOW_WIDTH * progress
        if (shadowWidth < 1f) return

        val scaleX = progress
        val alpha = (80 * progress).toInt().coerceIn(0, 255)
        shadowPaint.alpha = alpha

        if (foldX < screenWidth / 2) {
            shadowMatrix.setScale(scaleX, 1f)
            shadowMatrix.postTranslate(foldX, 0f)
            leftShadowGradient.setLocalMatrix(shadowMatrix)
            shadowPaint.shader = leftShadowGradient
            canvas.drawRect(foldX, 0f, foldX + shadowWidth, screenHeight, shadowPaint)
        } else {
            shadowMatrix.setScale(scaleX, 1f)
            shadowMatrix.postTranslate(foldX - shadowWidth, 0f)
            rightShadowGradient.setLocalMatrix(shadowMatrix)
            shadowPaint.shader = rightShadowGradient
            canvas.drawRect(foldX - shadowWidth, 0f, foldX, screenHeight, shadowPaint)
        }
        shadowPaint.shader = null
    }

    private fun drawBackSide(canvas: Canvas, foldX: Float, progress: Float) {
        val backWidth = 60f * progress

        path.reset()
        if (foldX < screenWidth / 2) {
            path.moveTo(foldX, 0f)
            path.lineTo(foldX + backWidth, 0f)
            path.lineTo(foldX + backWidth, screenHeight)
            path.lineTo(foldX, screenHeight)
        } else {
            path.moveTo(foldX - backWidth, 0f)
            path.lineTo(foldX, 0f)
            path.lineTo(foldX, screenHeight)
            path.lineTo(foldX - backWidth, screenHeight)
        }
        path.close()

        canvas.drawPath(path, backPaint)
    }

    override fun startNext() {
        isCancel = false
        direction = PageDelegate.Direction.NEXT
        currentX = screenWidth * 0.8f
        startX = screenWidth
        startY = screenHeight / 2
        currentY = screenHeight / 2
        startAnimation()
    }

    override fun startPrev() {
        isCancel = false
        direction = PageDelegate.Direction.PREV
        currentX = screenWidth * 0.2f
        startX = 0f
        startY = screenHeight / 2
        currentY = screenHeight / 2
        startAnimation()
    }

    override fun confirmPageSettled() {
        if (state == PageDelegate.State.SETTLING) {
            state = PageDelegate.State.IDLE
            direction = PageDelegate.Direction.NONE
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
        animationProgress = 0f

        // Like Legado's fillPage: if committed animation was interrupted, still commit page
        if (wasCommitted && prevDirection != PageDelegate.Direction.NONE) {
            callback?.onPageChanged(prevDirection)
        }
    }

    override fun isDraggingBackward(): Boolean {
        return currentX > startX
    }

    private fun startAnimation() {
        state = PageDelegate.State.ANIMATING
        val shouldNotify = !isCancel

        val startProgress = Math.abs(currentX - startX) / screenWidth

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val linearProgress = anim.animatedValue as Float

                animationProgress = if (direction == PageDelegate.Direction.NEXT) {
                    startProgress + (1f - startProgress) * linearProgress
                } else {
                    startProgress * (1f - linearProgress)
                }

                currentX = if (direction == PageDelegate.Direction.NEXT) {
                    startX + (screenWidth - startX) * animationProgress
                } else {
                    startX * (1f - animationProgress)
                }
                updateBezierPoints()
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
                        animationProgress = 0f
                    }
                }
            })
            start()
        }
    }

    private companion object {
        const val MAX_SHADOW_WIDTH = 30f
    }
}
