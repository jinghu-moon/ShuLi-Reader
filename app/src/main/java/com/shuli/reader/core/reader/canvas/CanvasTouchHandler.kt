package com.shuli.reader.core.reader.canvas

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.shuli.reader.core.reader.animation.PageDelegate

/**
 * 触摸手势处理：tap / long-press / 边缘拖拽 / 边缘点击翻页。
 *
 * 从 ReaderCanvasView.onTouchEvent 拆出，独立测试手势识别逻辑。
 */
class CanvasTouchHandler(context: Context) {

    /** 触摸事件回调 */
    interface Callbacks {
        fun getWidth(): Float
        fun getHeight(): Float
        fun getPageDelegate(): PageDelegate?
        fun isEdgeTurnPageEnabled(): Boolean
        fun getEdgeWidthPercent(): Float
        fun onPageChanged(direction: PageDelegate.Direction)
        fun onCenterClicked()
        fun onLongPress(x: Float, y: Float)
    }

    var callbacks: Callbacks? = null

    /** 是否正在文本选区手势中（拦截后续事件） */
    var isTextSelectionGesture: Boolean = false
        private set

    private var touchDownX: Float = 0f
    private var touchDownY: Float = 0f
    private var touchMoved: Boolean = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val slopSquare = touchSlop * touchSlop

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onLongPress(event: MotionEvent) {
                val cb = callbacks ?: return
                cb.getPageDelegate()?.abort()
                cb.onLongPress(event.x, event.y)
            }

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                val cb = callbacks ?: return false
                val x = event.x
                val y = event.y
                val w = cb.getWidth()
                val h = cb.getHeight()
                val edgeWidthPercent = cb.getEdgeWidthPercent()

                val isCenter = x > w * edgeWidthPercent && x < w * (1f - edgeWidthPercent) &&
                    y > h / 3f && y < h * 2f / 3f
                if (isCenter) {
                    cb.onCenterClicked()
                    return true
                }
                return false
            }
        },
    )

    fun onTouchEvent(event: MotionEvent): Boolean {
        val cb = callbacks ?: return false

        if (isTextSelectionGesture) {
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                isTextSelectionGesture = false
            }
            return true
        }

        val w = cb.getWidth()
        val h = cb.getHeight()
        val edgeWidthPercent = cb.getEdgeWidthPercent()
        val delegate = cb.getPageDelegate()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchMoved = false

                val isEdge = event.x <= w * edgeWidthPercent || event.x >= w * (1f - edgeWidthPercent)
                if (isEdge && delegate != null) {
                    delegate.onTouch(event)
                    return true
                }
                gestureDetector.onTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!touchMoved) {
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    val distSq = dx * dx + dy * dy
                    if (distSq > slopSquare) {
                        touchMoved = true
                    }
                }

                if (touchMoved) {
                    val isEdgeStart = touchDownX <= w * edgeWidthPercent || touchDownX >= w * (1f - edgeWidthPercent)
                    if (isEdgeStart && delegate != null) {
                        delegate.onTouch(event)
                        return true
                    }
                }
                gestureDetector.onTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val isEdgeStart = touchDownX <= w * edgeWidthPercent || touchDownX >= w * (1f - edgeWidthPercent)
                val wasMoved = touchMoved
                touchMoved = false

                if (wasMoved && isEdgeStart && delegate != null) {
                    delegate.onTouch(event)
                    return true
                }

                gestureDetector.onTouchEvent(event)

                if (!wasMoved && isEdgeStart && cb.isEdgeTurnPageEnabled()) {
                    val isCenter = touchDownX > w * edgeWidthPercent && touchDownX < w * (1f - edgeWidthPercent) &&
                        touchDownY > h / 3f && touchDownY < h * 2f / 3f
                    if (!isCenter) {
                        if (touchDownX <= w * edgeWidthPercent) {
                            delegate?.startPrev() ?: cb.onPageChanged(PageDelegate.Direction.PREV)
                        } else {
                            delegate?.startNext() ?: cb.onPageChanged(PageDelegate.Direction.NEXT)
                        }
                        return true
                    }
                }
                return true
            }
        }
        return false
    }

    /** 标记开始文本选区手势（拦截后续触摸事件直到 UP/CANCEL） */
    fun beginTextSelection() {
        isTextSelectionGesture = true
    }
}
