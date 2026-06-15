package com.shuli.reader.core.reader.canvas

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.shuli.reader.core.reader.animation.PageDelegate
import com.shuli.reader.feature.reader.settings.GestureAction
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.feature.reader.settings.TouchZone

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
        fun getLeftZoneRatio(): Float = 0.33f
        fun onPageChanged(direction: PageDelegate.Direction)
        fun onCenterClicked()
        fun onLongPress(x: Float, y: Float)

        /** action-based 扩展（v5.1），默认无操作以保持向后兼容 */
        fun onAction(action: GestureAction, x: Float = 0f, y: Float = 0f) {}

        /** 获取当前手势配置（v5.1），默认返回默认配置 */
        fun getGestureConfig(): GestureConfig = GestureConfig()
    }

    /** 手势配置快捷访问 */
    var gestureConfig: GestureConfig = GestureConfig()

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
                val leftRatio = cb.getLeftZoneRatio().coerceIn(0.2f, 0.5f)

                val isCenter = x > w * leftRatio && x < w * (1f - leftRatio) &&
                    y > h / 3f && y < h * 2f / 3f
                if (isCenter) {
                    cb.onCenterClicked()
                    return true
                }

                val zone = resolveTouchZone(x, y, w, h, leftRatio)
                val config = cb.getGestureConfig()
                val action = config.getAction(zone)
                if (action != GestureAction.NONE) {
                    cb.onAction(action, x, y)
                }
                return true
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

    private fun resolveTouchZone(
        x: Float, y: Float, w: Float, h: Float, leftRatio: Float,
    ): TouchZone {
        val col = when {
            x <= w * leftRatio -> 0
            x >= w * (1f - leftRatio) -> 2
            else -> 1
        }
        val row = when {
            y <= h / 3f -> 0
            y >= h * 2f / 3f -> 2
            else -> 1
        }
        return TouchZone.entries[row * 3 + col]
    }

    /** 标记开始文本选区手势（拦截后续触摸事件直到 UP/CANCEL） */
    fun beginTextSelection() {
        isTextSelectionGesture = true
    }
}
