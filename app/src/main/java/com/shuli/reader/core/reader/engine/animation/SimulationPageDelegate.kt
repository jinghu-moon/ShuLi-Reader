package com.shuli.reader.core.reader.engine.animation

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.shuli.reader.core.recorder.CanvasRecorder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * 仿真翻页委托。
 *
 * 算法参考 Legado SimulationPageDelegate：通过拖拽页角、双贝塞尔曲线、
 * 翻页背面镜像和多段阴影组合，绘制接近真实书页的卷曲效果。
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
    private var startY: Float = 0f
    private var touchX: Float = MIN_TOUCH
    private var touchY: Float = MIN_TOUCH
    private var lastTouchX: Float = MIN_TOUCH

    private var screenWidth: Float = 1080f
    private var screenHeight: Float = 1920f
    private var maxLength: Float = hypot(screenWidth.toDouble(), screenHeight.toDouble()).toFloat()

    private var cornerX: Int = screenWidth.toInt()
    private var cornerY: Int = screenHeight.toInt()
    private var isRtOrLb: Boolean = false
    private var isCancel: Boolean = false
    private var isAborting: Boolean = false

    private val path0 = Path()
    private val path1 = Path()

    private val bezierStart1 = PointF()
    private val bezierControl1 = PointF()
    private val bezierVertex1 = PointF()
    private var bezierEnd1 = PointF()

    private val bezierStart2 = PointF()
    private val bezierControl2 = PointF()
    private val bezierVertex2 = PointF()
    private var bezierEnd2 = PointF()

    private var middleX = 0f
    private var middleY = 0f
    private var degrees = 0f
    private var touchToCornerDistance = 0f

    private val matrix = Matrix()
    private val matrixArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f)

    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5F5F5.toInt()
        style = Paint.Style.FILL
    }

    private val folderShadowDrawableRL = GradientDrawable(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(0x00333333, 0xB0333333.toInt()),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }

    private val folderShadowDrawableLR = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0x00333333, 0xB0333333.toInt()),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }

    private val backShadowDrawableRL = GradientDrawable(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(0x11111111, 0x00EEEEEF),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }

    private val backShadowDrawableLR = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0x11111111, 0x00EEEEEF),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }

    private val frontShadowDrawableVLR = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0x80111111.toInt(), 0x00EEEEEF),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }

    private val frontShadowDrawableVRL = GradientDrawable(
        GradientDrawable.Orientation.RIGHT_LEFT,
        intArrayOf(0x80111111.toInt(), 0x00EEEEEF),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }

    private val frontShadowDrawableHTB = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0x80111111.toInt(), 0x00EEEEEF),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }

    private val frontShadowDrawableHBT = GradientDrawable(
        GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(0x80111111.toInt(), 0x00EEEEEF),
    ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }

    private var animator: ValueAnimator? = null

    override fun setCallback(callback: PageDelegate.Callback) {
        this.callback = callback
    }

    override fun onTouch(event: MotionEvent): Boolean {
        val action = event.action
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                abort()
                startX = event.x
                startY = event.y
                touchX = event.x.coerceTouch()
                touchY = event.y.coerceTouch()
                lastTouchX = touchX
                isCancel = false
                direction = PageDelegate.Direction.NONE
                calcCornerXY(startX, startY)
                state = PageDelegate.State.DRAGGING
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (state != PageDelegate.State.DRAGGING) return true

                lastTouchX = touchX
                touchX = event.x.coerceTouch()
                touchY = event.y.coerceTouch()

                val newDirection = when {
                    touchX > startX -> PageDelegate.Direction.PREV
                    touchX < startX -> PageDelegate.Direction.NEXT
                    else -> direction
                }
                if (newDirection != PageDelegate.Direction.NONE && newDirection != direction) {
                    setDirectionInternal(newDirection)
                }

                constrainTouchYForNaturalCurl()
                isCancel = when (direction) {
                    PageDelegate.Direction.NEXT -> touchX > lastTouchX
                    PageDelegate.Direction.PREV -> touchX < lastTouchX
                    PageDelegate.Direction.NONE -> false
                }
                callback?.invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state == PageDelegate.State.DRAGGING) {
                    if (direction == PageDelegate.Direction.NONE) {
                        val dx = touchX - startX
                        direction = if (dx < 0f) PageDelegate.Direction.NEXT else PageDelegate.Direction.PREV
                        setDirectionInternal(direction)
                    }
                    val movedEnough = abs(touchX - startX) > screenWidth / 3f
                isCancel = action == MotionEvent.ACTION_CANCEL || !movedEnough
                startAnimation()
            }
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder) {
        updateViewSize(canvas.width.toFloat(), canvas.height.toFloat())

        when (state) {
            PageDelegate.State.IDLE -> current.draw(canvas)
            PageDelegate.State.SETTLING -> target.draw(canvas)
            PageDelegate.State.DRAGGING, PageDelegate.State.ANIMATING -> {
                if (direction == PageDelegate.Direction.NONE) {
                    current.draw(canvas)
                    return
                }
                calcPoints()
                when (direction) {
                    PageDelegate.Direction.NEXT -> drawCurl(canvas, front = current, back = target)
                    PageDelegate.Direction.PREV -> drawCurl(canvas, front = target, back = current)
                    PageDelegate.Direction.NONE -> current.draw(canvas)
                }
            }
        }
    }

    override fun startNext() {
        updateProgrammaticStart(PageDelegate.Direction.NEXT)
        startAnimation()
    }

    override fun startPrev() {
        updateProgrammaticStart(PageDelegate.Direction.PREV)
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
        val committedDirection = direction

        isAborting = true
        animator?.cancel()
        isAborting = false
        animator = null
        state = PageDelegate.State.IDLE
        direction = PageDelegate.Direction.NONE
        isCancel = false

        if (wasCommitted && committedDirection != PageDelegate.Direction.NONE) {
            callback?.onPageChanged(committedDirection)
        }
    }

    override fun isDraggingBackward(): Boolean {
        return direction == PageDelegate.Direction.PREV ||
            (direction == PageDelegate.Direction.NONE && touchX > startX)
    }

    private fun drawCurl(canvas: Canvas, front: CanvasRecorder, back: CanvasRecorder) {
        drawCurrentPageArea(canvas, front)
        drawNextPageAreaAndShadow(canvas, back)
        drawCurrentPageShadow(canvas)
        drawCurrentBackArea(canvas, front)
    }

    private fun drawCurrentPageArea(canvas: Canvas, recorder: CanvasRecorder) {
        path0.reset()
        path0.moveTo(bezierStart1.x, bezierStart1.y)
        path0.quadTo(bezierControl1.x, bezierControl1.y, bezierEnd1.x, bezierEnd1.y)
        path0.lineTo(touchX, touchY)
        path0.lineTo(bezierEnd2.x, bezierEnd2.y)
        path0.quadTo(bezierControl2.x, bezierControl2.y, bezierStart2.x, bezierStart2.y)
        path0.lineTo(cornerX.toFloat(), cornerY.toFloat())
        path0.close()

        canvas.save()
        canvas.clipOutPath(path0)
        recorder.draw(canvas)
        canvas.restore()
    }

    private fun drawNextPageAreaAndShadow(canvas: Canvas, recorder: CanvasRecorder) {
        path1.reset()
        path1.moveTo(bezierStart1.x, bezierStart1.y)
        path1.lineTo(bezierVertex1.x, bezierVertex1.y)
        path1.lineTo(bezierVertex2.x, bezierVertex2.y)
        path1.lineTo(bezierStart2.x, bezierStart2.y)
        path1.lineTo(cornerX.toFloat(), cornerY.toFloat())
        path1.close()

        degrees = Math.toDegrees(
            atan2(
                (bezierControl1.x - cornerX).toDouble(),
                (bezierControl2.y - cornerY).toDouble(),
            ),
        ).toFloat()

        val left: Int
        val right: Int
        val shadowDrawable: GradientDrawable
        if (isRtOrLb) {
            left = bezierStart1.x.toInt()
            right = (bezierStart1.x + touchToCornerDistance / 4f).toInt()
            shadowDrawable = backShadowDrawableLR
        } else {
            left = (bezierStart1.x - touchToCornerDistance / 4f).toInt()
            right = bezierStart1.x.toInt()
            shadowDrawable = backShadowDrawableRL
        }

        canvas.save()
        canvas.clipPath(path0)
        canvas.clipPath(path1)
        recorder.draw(canvas)
        canvas.rotate(degrees, bezierStart1.x, bezierStart1.y)
        shadowDrawable.setBounds(
            left,
            bezierStart1.y.toInt(),
            right,
            (bezierStart1.y + maxLength).toInt(),
        )
        shadowDrawable.draw(canvas)
        canvas.restore()
    }

    private fun drawCurrentBackArea(canvas: Canvas, recorder: CanvasRecorder) {
        val i = ((bezierStart1.x + bezierControl1.x) / 2f).toInt()
        val f1 = abs(i - bezierControl1.x)
        val i1 = ((bezierStart2.y + bezierControl2.y) / 2f).toInt()
        val f2 = abs(i1 - bezierControl2.y)
        val f3 = min(f1, f2)

        path1.reset()
        path1.moveTo(bezierVertex2.x, bezierVertex2.y)
        path1.lineTo(bezierVertex1.x, bezierVertex1.y)
        path1.lineTo(bezierEnd1.x, bezierEnd1.y)
        path1.lineTo(touchX, touchY)
        path1.lineTo(bezierEnd2.x, bezierEnd2.y)
        path1.close()

        val folderShadowDrawable: GradientDrawable
        val left: Int
        val right: Int
        if (isRtOrLb) {
            left = (bezierStart1.x - 1f).toInt()
            right = (bezierStart1.x + f3 + 1f).toInt()
            folderShadowDrawable = folderShadowDrawableLR
        } else {
            left = (bezierStart1.x - f3 - 1f).toInt()
            right = (bezierStart1.x + 1f).toInt()
            folderShadowDrawable = folderShadowDrawableRL
        }

        canvas.save()
        canvas.clipPath(path0)
        canvas.clipPath(path1)

        val dis = hypot(
            (cornerX - bezierControl1.x).toDouble(),
            (bezierControl2.y - cornerY).toDouble(),
        ).toFloat().coerceAtLeast(EPSILON)
        val f8 = (cornerX - bezierControl1.x) / dis
        val f9 = (bezierControl2.y - cornerY) / dis
        matrixArray[0] = 1f - 2f * f9 * f9
        matrixArray[1] = 2f * f8 * f9
        matrixArray[3] = matrixArray[1]
        matrixArray[4] = 1f - 2f * f8 * f8
        matrix.reset()
        matrix.setValues(matrixArray)
        matrix.preTranslate(-bezierControl1.x, -bezierControl1.y)
        matrix.postTranslate(bezierControl1.x, bezierControl1.y)

        canvas.drawRect(0f, 0f, screenWidth, screenHeight, backPaint)
        canvas.save()
        canvas.concat(matrix)
        recorder.draw(canvas)
        canvas.restore()

        canvas.rotate(degrees, bezierStart1.x, bezierStart1.y)
        folderShadowDrawable.setBounds(
            left,
            bezierStart1.y.toInt(),
            right,
            (bezierStart1.y + maxLength).toInt(),
        )
        folderShadowDrawable.draw(canvas)
        canvas.restore()
    }

    private fun drawCurrentPageShadow(canvas: Canvas) {
        val degree = if (isRtOrLb) {
            Math.PI / 4 - atan2(
                (bezierControl1.y - touchY).toDouble(),
                (touchX - bezierControl1.x).toDouble(),
            )
        } else {
            Math.PI / 4 - atan2(
                (touchY - bezierControl1.y).toDouble(),
                (touchX - bezierControl1.x).toDouble(),
            )
        }

        val d1 = (SHADOW_WIDTH * 1.414f * cos(degree)).toFloat()
        val d2 = (SHADOW_WIDTH * 1.414f * sin(degree)).toFloat()
        val x = touchX + d1
        val y = if (isRtOrLb) touchY + d2 else touchY - d2

        path1.reset()
        path1.moveTo(x, y)
        path1.lineTo(touchX, touchY)
        path1.lineTo(bezierControl1.x, bezierControl1.y)
        path1.lineTo(bezierStart1.x, bezierStart1.y)
        path1.close()

        canvas.save()
        canvas.clipOutPath(path0)
        canvas.clipPath(path1)

        var left: Int
        var right: Int
        var shadowDrawable: GradientDrawable
        if (isRtOrLb) {
            left = bezierControl1.x.toInt()
            right = (bezierControl1.x + SHADOW_WIDTH).toInt()
            shadowDrawable = frontShadowDrawableVLR
        } else {
            left = (bezierControl1.x - SHADOW_WIDTH).toInt()
            right = (bezierControl1.x + 1f).toInt()
            shadowDrawable = frontShadowDrawableVRL
        }
        var rotateDegrees = Math.toDegrees(
            atan2(
                (touchX - bezierControl1.x).toDouble(),
                (bezierControl1.y - touchY).toDouble(),
            ),
        ).toFloat()
        canvas.rotate(rotateDegrees, bezierControl1.x, bezierControl1.y)
        shadowDrawable.setBounds(
            left,
            (bezierControl1.y - maxLength).toInt(),
            right,
            bezierControl1.y.toInt(),
        )
        shadowDrawable.draw(canvas)
        canvas.restore()

        path1.reset()
        path1.moveTo(x, y)
        path1.lineTo(touchX, touchY)
        path1.lineTo(bezierControl2.x, bezierControl2.y)
        path1.lineTo(bezierStart2.x, bezierStart2.y)
        path1.close()

        canvas.save()
        canvas.clipOutPath(path0)
        canvas.clipPath(path1)

        if (isRtOrLb) {
            left = bezierControl2.y.toInt()
            right = (bezierControl2.y + SHADOW_WIDTH).toInt()
            shadowDrawable = frontShadowDrawableHTB
        } else {
            left = (bezierControl2.y - SHADOW_WIDTH).toInt()
            right = (bezierControl2.y + 1f).toInt()
            shadowDrawable = frontShadowDrawableHBT
        }
        rotateDegrees = Math.toDegrees(
            atan2(
                (bezierControl2.y - touchY).toDouble(),
                (bezierControl2.x - touchX).toDouble(),
            ),
        ).toFloat()
        canvas.rotate(rotateDegrees, bezierControl2.x, bezierControl2.y)
        val temp = if (bezierControl2.y < 0f) {
            bezierControl2.y - screenHeight
        } else {
            bezierControl2.y
        }
        val hmg = hypot(bezierControl2.x.toDouble(), temp.toDouble())
        if (hmg > maxLength) {
            shadowDrawable.setBounds(
                (bezierControl2.x - SHADOW_WIDTH - hmg).toInt(),
                left,
                (bezierControl2.x + maxLength - hmg).toInt(),
                right,
            )
        } else {
            shadowDrawable.setBounds(
                (bezierControl2.x - maxLength).toInt(),
                left,
                bezierControl2.x.toInt(),
                right,
            )
        }
        shadowDrawable.draw(canvas)
        canvas.restore()
    }

    private fun startAnimation() {
        if (direction == PageDelegate.Direction.NONE) {
            state = PageDelegate.State.IDLE
            return
        }

        state = PageDelegate.State.ANIMATING
        val shouldNotify = !isCancel

        val startTouchX = touchX
        val startTouchY = touchY
        var dx: Float
        val dy: Float

        if (isCancel) {
            dx = if (cornerX > 0 && direction == PageDelegate.Direction.NEXT) {
                screenWidth - touchX
            } else {
                -touchX
            }
            if (direction != PageDelegate.Direction.NEXT) {
                dx = -(screenWidth + touchX)
            }
            dy = if (cornerY > 0) screenHeight - touchY else -touchY
        } else {
            dx = if (cornerX > 0 && direction == PageDelegate.Direction.NEXT) {
                -(screenWidth + touchX)
            } else {
                screenWidth - touchX
            }
            dy = if (cornerY > 0) screenHeight - touchY else 1f - touchY
        }

        val distanceFraction = (abs(dx) / screenWidth.coerceAtLeast(1f)).coerceIn(0.15f, 1.25f)

        isAborting = true
        animator?.cancel()
        isAborting = false
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (durationMs * distanceFraction).toLong().coerceAtLeast(80L)
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                touchX = (startTouchX + dx * fraction).coerceTouch()
                touchY = (startTouchY + dy * fraction).coerceTouch()
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
                        isCancel = false
                    }
                }
            })
            start()
        }
    }

    private fun updateProgrammaticStart(newDirection: PageDelegate.Direction) {
        isCancel = false
        direction = newDirection
        state = PageDelegate.State.DRAGGING
        when (newDirection) {
            PageDelegate.Direction.NEXT -> {
                startX = screenWidth * 0.9f
                startY = screenHeight * 0.9f
                touchX = startX
                touchY = startY
                calcCornerXY(screenWidth, startY)
            }
            PageDelegate.Direction.PREV -> {
                startX = 0f
                startY = screenHeight
                touchX = MIN_TOUCH
                touchY = screenHeight
                calcCornerXY(startX, screenHeight)
            }
            PageDelegate.Direction.NONE -> Unit
        }
    }

    private fun setDirectionInternal(newDirection: PageDelegate.Direction) {
        direction = newDirection
        when (newDirection) {
            PageDelegate.Direction.PREV -> {
                if (startX > screenWidth / 2f) {
                    calcCornerXY(startX, screenHeight)
                } else {
                    calcCornerXY(screenWidth - startX, screenHeight)
                }
            }
            PageDelegate.Direction.NEXT -> {
                if (screenWidth / 2f > startX) {
                    calcCornerXY(screenWidth - startX, startY)
                } else {
                    calcCornerXY(startX, startY)
                }
            }
            PageDelegate.Direction.NONE -> Unit
        }
    }

    private fun constrainTouchYForNaturalCurl() {
        if ((startY > screenHeight / 3f && startY < screenHeight * 2f / 3f) ||
            direction == PageDelegate.Direction.PREV
        ) {
            touchY = screenHeight
        }

        if (startY > screenHeight / 3f &&
            startY < screenHeight / 2f &&
            direction == PageDelegate.Direction.NEXT
        ) {
            touchY = MIN_TOUCH
        }
    }

    private fun calcCornerXY(x: Float, y: Float) {
        cornerX = if (x <= screenWidth / 2f) 0 else screenWidth.toInt()
        cornerY = if (y <= screenHeight / 2f) 0 else screenHeight.toInt()
        isRtOrLb = (cornerX == 0 && cornerY == screenHeight.toInt()) ||
            (cornerY == 0 && cornerX == screenWidth.toInt())
    }

    private fun calcPoints() {
        middleX = (touchX + cornerX) / 2f
        middleY = (touchY + cornerY) / 2f

        val dxToMiddle = safeDenominator(cornerX - middleX)
        val dyToMiddle = cornerY - middleY

        bezierControl1.x = middleX - dyToMiddle * dyToMiddle / dxToMiddle
        bezierControl1.y = cornerY.toFloat()
        bezierControl2.x = cornerX.toFloat()
        bezierControl2.y = middleY - dxToMiddle * dxToMiddle / safeDenominator(dyToMiddle)

        bezierStart1.x = bezierControl1.x - (cornerX - bezierControl1.x) / 2f
        bezierStart1.y = cornerY.toFloat()

        if (touchX > 0f && touchX < screenWidth && (bezierStart1.x < 0f || bezierStart1.x > screenWidth)) {
            if (bezierStart1.x < 0f) {
                bezierStart1.x = screenWidth - bezierStart1.x
            }

            val f1 = abs(cornerX - touchX).coerceAtLeast(EPSILON)
            val f2 = screenWidth * f1 / bezierStart1.x.coerceAtLeast(EPSILON)
            touchX = abs(cornerX - f2).coerceTouch()

            val f3 = abs(cornerX - touchX) * abs(cornerY - touchY) / f1
            touchY = abs(cornerY - f3).coerceTouch()

            middleX = (touchX + cornerX) / 2f
            middleY = (touchY + cornerY) / 2f

            val fixedDxToMiddle = safeDenominator(cornerX - middleX)
            val fixedDyToMiddle = cornerY - middleY
            bezierControl1.x = middleX - fixedDyToMiddle * fixedDyToMiddle / fixedDxToMiddle
            bezierControl1.y = cornerY.toFloat()
            bezierControl2.x = cornerX.toFloat()
            bezierControl2.y = middleY -
                fixedDxToMiddle * fixedDxToMiddle / safeDenominator(fixedDyToMiddle)
            bezierStart1.x = bezierControl1.x - (cornerX - bezierControl1.x) / 2f
        }

        bezierStart2.x = cornerX.toFloat()
        bezierStart2.y = bezierControl2.y - (cornerY - bezierControl2.y) / 2f

        touchToCornerDistance = hypot(
            (touchX - cornerX).toDouble(),
            (touchY - cornerY).toDouble(),
        ).toFloat()

        bezierEnd1 = getCross(
            PointF(touchX, touchY),
            bezierControl1,
            bezierStart1,
            bezierStart2,
        )
        bezierEnd2 = getCross(
            PointF(touchX, touchY),
            bezierControl2,
            bezierStart1,
            bezierStart2,
        )

        bezierVertex1.x = (bezierStart1.x + 2f * bezierControl1.x + bezierEnd1.x) / 4f
        bezierVertex1.y = (2f * bezierControl1.y + bezierStart1.y + bezierEnd1.y) / 4f
        bezierVertex2.x = (bezierStart2.x + 2f * bezierControl2.x + bezierEnd2.x) / 4f
        bezierVertex2.y = (2f * bezierControl2.y + bezierStart2.y + bezierEnd2.y) / 4f
    }

    private fun getCross(p1: PointF, p2: PointF, p3: PointF, p4: PointF): PointF {
        val x1 = p1.x
        val y1 = p1.y
        val x2 = p2.x
        val y2 = p2.y
        val x3 = p3.x
        val y3 = p3.y
        val x4 = p4.x
        val y4 = p4.y

        val denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denominator) < EPSILON) return PointF(p1.x, p1.y)

        val first = x1 * y2 - y1 * x2
        val second = x3 * y4 - y3 * x4
        return PointF(
            (first * (x3 - x4) - (x1 - x2) * second) / denominator,
            (first * (y3 - y4) - (y1 - y2) * second) / denominator,
        )
    }

    private fun updateViewSize(width: Float, height: Float) {
        val fixedWidth = width.coerceAtLeast(1f)
        val fixedHeight = height.coerceAtLeast(1f)
        if (screenWidth == fixedWidth && screenHeight == fixedHeight) return
        screenWidth = fixedWidth
        screenHeight = fixedHeight
        maxLength = hypot(screenWidth.toDouble(), screenHeight.toDouble()).toFloat()
        if (cornerX > 0) cornerX = screenWidth.toInt()
        if (cornerY > 0) cornerY = screenHeight.toInt()
    }

    private fun safeDenominator(value: Float): Float {
        return when {
            value in 0f..EPSILON -> EPSILON
            value in -EPSILON..0f -> -EPSILON
            else -> value
        }
    }

    private fun Float.coerceTouch(): Float {
        return when {
            this == 0f -> MIN_TOUCH
            this.isNaN() -> MIN_TOUCH
            else -> this
        }
    }

    private companion object {
        const val MIN_TOUCH = 0.1f
        const val EPSILON = 0.1f
        const val SHADOW_WIDTH = 25f
    }
}
