package com.shuli.reader.core.reader.engine.input

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.shuli.reader.core.reader.engine.animation.PageDelegate
import com.shuli.reader.core.reader.engine.selection.CanvasTextSelection
import com.shuli.reader.feature.reader.settings.GestureAction
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.feature.reader.settings.TouchZone
import kotlin.math.abs

/**
 * 触摸手势处理：tap / long-press / 边缘拖拽 / 边缘点击翻页 / 选区把手拖动。
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

        /** 是否处于编辑模式（编辑模式下点击正文触发内联编辑而非翻页） */
        fun isEditMode(): Boolean = false

        /** 当前翻页动画是否为上下滚动模式 */
        fun isScrollPageMode(): Boolean = false

        /** 触摸点是否位于正文盒子内 */
        fun isInBodyBox(x: Float, y: Float): Boolean = true

        /** 编辑模式下点击正文（非中心区域），触发内联编辑 */
        fun onInlineEditTap(x: Float, y: Float) {}

        /** 获取文本选区对象 */
        fun getTextSelection(): CanvasTextSelection? = null

        /** 选区把手拖动开始 */
        fun onSelectionHandleDragStart(anchorId: CanvasTextSelection.AnchorId) {}

        /** 选区把手拖动中 */
        fun onSelectionHandleDragMove(x: Float, y: Float) {}

        /** 选区把手拖动结束 */
        fun onSelectionHandleDragEnd() {}

        /** 选区被清除 */
        fun onSelectionCleared() {}
    }

    /** 手势配置快捷访问 */
    var gestureConfig: GestureConfig = GestureConfig()

    var callbacks: Callbacks? = null

    /** 是否正在文本选区手势中（拦截后续事件） */
    var isTextSelectionGesture: Boolean = false
        private set

    /** 是否正在拖动选区把手 */
    var isHandleDragGesture: Boolean = false
        private set

    /** 是否正在由 PageDelegate 接管本次拖动 */
    var isPageDelegateGesture: Boolean = false
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
                if (isPageDelegateGesture || touchMoved || isHandleDragGesture) return
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

                // 编辑模式：点击正文区域触发内联编辑
                if (cb.isEditMode()) {
                    cb.onInlineEditTap(x, y)
                    return true
                }

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

        // 检查是否点击了选区把手（优先级最高）
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val textSelection = cb.getTextSelection()
                if (!cb.isEditMode() && textSelection != null && textSelection.selectedRange != null) {
                    // 检查是否点击了把手
                    val hitHandle = textSelection.hitTestHandle(event.x, event.y)
                    if (hitHandle != null) {
                        // 开始拖动把手
                        isHandleDragGesture = true
                        textSelection.startHandleDrag(hitHandle)
                        cb.onSelectionHandleDragStart(hitHandle)
                        return true
                    }

                    // 检查是否点击了选区内部
                    if (textSelection.isPointInSelection(event.x, event.y)) {
                        // 点击选区内部，不做任何操作（等待后续手势）
                        return false
                    }

                    // 点击选区外部，清除选区
                    textSelection.clearSelection()
                    cb.onSelectionCleared()
                    return false
                }
            }
        }

        // 处理把手拖动手势
        if (isHandleDragGesture) {
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    cb.onSelectionHandleDragMove(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isHandleDragGesture = false
                    cb.getTextSelection()?.endHandleDrag()
                    cb.onSelectionHandleDragEnd()
                    return true
                }
            }
            return true
        }

        // 处理文本选区手势（长按选词后拦截后续事件）
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
                isPageDelegateGesture = false
                // 所有按下事件都先走 GestureDetector，让长按有机会触发
                // 即使在边缘区域，也不立即发送给翻页 delegate
                gestureDetector.onTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isPageDelegateGesture) {
                    delegate?.onTouch(event)
                    return true
                }

                if (!touchMoved) {
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    val distSq = dx * dx + dy * dy
                    if (distSq > slopSquare) {
                        touchMoved = true
                    }
                }

                if (touchMoved) {
                    // 手指移动超过阈值：交给翻页 delegate 处理拖拽翻页
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    val isContinuousScrollMode = cb.isScrollPageMode()
                    val isEdgeStart = touchDownX <= w * edgeWidthPercent || touchDownX >= w * (1f - edgeWidthPercent)
                    val isScrollDrag = isContinuousScrollMode &&
                        cb.isInBodyBox(touchDownX, touchDownY) &&
                        abs(dy) > abs(dx)
                    val shouldStartPageDelegate = if (isContinuousScrollMode) isScrollDrag else isEdgeStart
                    if (shouldStartPageDelegate && delegate != null) {
                        beginPageDelegateGesture(delegate, event)
                        delegate.onTouch(event)
                        return true
                    }
                }
                gestureDetector.onTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasMoved = touchMoved
                touchMoved = false

                if (isPageDelegateGesture) {
                    isPageDelegateGesture = false
                    delegate?.onTouch(event)
                    return true
                }

                // 如果是边缘拖拽翻页（已交给 delegate），把 UP 也交给 delegate 完成翻页
                val isEdgeStart = touchDownX <= w * edgeWidthPercent || touchDownX >= w * (1f - edgeWidthPercent)
                if (wasMoved && !cb.isScrollPageMode() && isEdgeStart && delegate != null) {
                    delegate.onTouch(event)
                    return true
                }

                // 所有非拖拽事件交给 GestureDetector 处理
                // GestureDetector 自动区分：
                //   长按 → onLongPress → 文本选择
                //   短按 → onSingleTapUp → 区域手势（边缘区域默认翻页）
                gestureDetector.onTouchEvent(event)
                return true
            }
        }
        return false
    }

    private fun beginPageDelegateGesture(delegate: PageDelegate, event: MotionEvent) {
        if (isPageDelegateGesture) return
        isPageDelegateGesture = true
        cancelGestureDetector(event)
        val downEvent = MotionEvent.obtain(
            event.downTime,
            event.eventTime,
            MotionEvent.ACTION_DOWN,
            touchDownX,
            touchDownY,
            event.metaState,
        )
        delegate.onTouch(downEvent)
        downEvent.recycle()
    }

    private fun cancelGestureDetector(event: MotionEvent) {
        val cancelEvent = MotionEvent.obtain(
            event.downTime,
            event.eventTime,
            MotionEvent.ACTION_CANCEL,
            event.x,
            event.y,
            event.metaState,
        )
        gestureDetector.onTouchEvent(cancelEvent)
        cancelEvent.recycle()
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
