package com.shuli.reader.core.reader.animation

import android.graphics.Canvas
import android.graphics.PointF
import android.view.MotionEvent
import com.shuli.reader.core.canvasrecorder.CanvasRecorder

/**
 * 翻页动画委托接口
 */
interface PageDelegate {

    /**
     * 翻页状态
     */
    enum class State {
        IDLE,      // 空闲
        DRAGGING,  // 拖拽中
        ANIMATING, // 动画中
    }

    /**
     * 翻页方向
     */
    enum class Direction {
        NEXT, // 下一页
        PREV, // 上一页
        NONE, // 无方向
    }

    /**
     * 翻页回调
     */
    interface Callback {
        /**
         * 翻页完成
         */
        fun onPageChanged(direction: Direction)

        /**
         * 请求重绘
         */
        fun invalidate()
    }

    /**
     * 当前状态
     */
    val state: State

    /**
     * 当前方向
     */
    val direction: Direction

    /**
     * 触摸事件处理
     * @return true 表示已处理
     */
    fun onTouch(event: MotionEvent): Boolean

    /**
     * 绘制翻页动画。
     * @param current 当前页 recorder（必须已 record 过）
     * @param target 目标页 recorder（next 或 prev，按 direction 决定）
     */
    fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder)

    /**
     * 开始下一页动画
     */
    fun startNext()

    /**
     * 开始上一页动画
     */
    fun startPrev()

    /**
     * 中断动画
     */
    fun abort()

    /**
     * 设置回调
     */
    fun setCallback(callback: Callback)

    /**
     * 拖拽是否向后（向右/向上，用于判断是上一页还是下一页）
     * 默认返回 false
     */
    fun isDraggingBackward(): Boolean = false
}