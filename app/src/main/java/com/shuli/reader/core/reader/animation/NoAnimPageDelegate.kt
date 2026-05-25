package com.shuli.reader.core.reader.animation

import android.graphics.Canvas
import android.view.MotionEvent
import com.shuli.reader.core.canvasrecorder.CanvasRecorder

/**
 * 无动画翻页委托
 */
class NoAnimPageDelegate : PageDelegate {

    override var state: PageDelegate.State = PageDelegate.State.IDLE
        private set

    override var direction: PageDelegate.Direction = PageDelegate.Direction.NONE
        private set

    private var callback: PageDelegate.Callback? = null
    private var screenWidth: Float = 1080f

    override fun setCallback(callback: PageDelegate.Callback) {
        this.callback = callback
    }

    override fun onTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                state = PageDelegate.State.DRAGGING
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (state == PageDelegate.State.DRAGGING) {
                    direction = if (event.x < screenWidth / 2) {
                        PageDelegate.Direction.PREV
                    } else {
                        PageDelegate.Direction.NEXT
                    }
                    state = PageDelegate.State.IDLE
                    callback?.onPageChanged(direction)
                }
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder) {
        current.draw(canvas)
    }

    override fun startNext() {
        direction = PageDelegate.Direction.NEXT
        state = PageDelegate.State.IDLE
        callback?.onPageChanged(direction)
    }

    override fun startPrev() {
        direction = PageDelegate.Direction.PREV
        state = PageDelegate.State.IDLE
        callback?.onPageChanged(direction)
    }

    override fun confirmPageSettled() {
        if (state == PageDelegate.State.SETTLING) {
            state = PageDelegate.State.IDLE
            direction = PageDelegate.Direction.NONE
        }
    }

    override fun abort() {
        state = PageDelegate.State.IDLE
        direction = PageDelegate.Direction.NONE
    }
}