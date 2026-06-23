package com.shuli.reader.core.reader.engine.input

import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.shuli.reader.core.reader.engine.animation.PageDelegate
import com.shuli.reader.core.recorder.CanvasRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class CanvasTouchHandlerScrollModeTest {

    @Test
    fun verticalDragInScrollMode_isForwardedToPageDelegate() {
        val delegate = RecordingPageDelegate()
        val handler = createHandler(delegate, isScrollMode = true)

        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 500f, 500f, 0L))
        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, 500f, 700f, 16L))
        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_UP, 500f, 700f, 32L))

        assertEquals(
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP),
            delegate.actions,
        )
        assertFalse(handler.isPageDelegateGesture)
    }

    @Test
    fun verticalDragOutsideScrollMode_isNotForwardedFromBodyArea() {
        val delegate = RecordingPageDelegate()
        val handler = createHandler(delegate, isScrollMode = false)

        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 500f, 500f, 0L))
        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, 500f, 700f, 16L))
        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_UP, 500f, 700f, 32L))

        assertEquals(emptyList<Int>(), delegate.actions)
    }

    @Test
    fun verticalDragInScrollMode_outsideBodyBox_isNotForwarded() {
        val delegate = RecordingPageDelegate()
        val handler = createHandler(delegate, isScrollMode = true, isInBodyBox = false)

        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 500f, 100f, 0L))
        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, 500f, 320f, 16L))
        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_UP, 500f, 320f, 32L))

        assertEquals(emptyList<Int>(), delegate.actions)
    }

    @Test
    fun edgeDragInScrollMode_outsideBodyBox_isNotForwarded() {
        val delegate = RecordingPageDelegate()
        val handler = createHandler(delegate, isScrollMode = true, isInBodyBox = false)

        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 20f, 100f, 0L))
        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, 20f, 320f, 16L))
        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_UP, 20f, 320f, 32L))

        assertEquals(emptyList<Int>(), delegate.actions)
    }

    @Test
    fun edgeDragOutsideScrollMode_isForwarded() {
        val delegate = RecordingPageDelegate()
        val handler = createHandler(delegate, isScrollMode = false)

        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 20f, 500f, 0L))
        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, 20f, 720f, 16L))
        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_UP, 20f, 720f, 32L))

        assertEquals(
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP),
            delegate.actions,
        )
    }

    @Test
    fun verticalDragInScrollMode_cancelsPendingLongPress() {
        val delegate = RecordingPageDelegate()
        var longPressCount = 0
        val handler = createHandler(
            delegate = delegate,
            isScrollMode = true,
            onLongPress = { longPressCount++ },
        )

        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN, 500f, 500f, 0L))
        handler.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, 500f, 720f, 16L))

        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong() + 100L),
        )

        assertEquals(0, longPressCount)
    }

    private fun createHandler(
        delegate: PageDelegate,
        isScrollMode: Boolean,
        isInBodyBox: Boolean = true,
        onLongPress: () -> Unit = {},
    ): CanvasTouchHandler {
        return CanvasTouchHandler(RuntimeEnvironment.getApplication()).apply {
            callbacks = object : CanvasTouchHandler.Callbacks {
                override fun getWidth(): Float = 1080f
                override fun getHeight(): Float = 1920f
                override fun getPageDelegate(): PageDelegate = delegate
                override fun isEdgeTurnPageEnabled(): Boolean = true
                override fun getEdgeWidthPercent(): Float = 0.08f
                override fun isScrollPageMode(): Boolean = isScrollMode
                override fun isInBodyBox(x: Float, y: Float): Boolean = isInBodyBox
                override fun onPageChanged(direction: PageDelegate.Direction) = Unit
                override fun onCenterClicked() = Unit
                override fun onLongPress(x: Float, y: Float) = onLongPress()
            }
        }
    }

    private fun motionEvent(
        action: Int,
        x: Float,
        y: Float,
        eventTime: Long,
    ): MotionEvent {
        return MotionEvent.obtain(0L, eventTime, action, x, y, 0)
    }

    private class RecordingPageDelegate : PageDelegate {
        val actions = mutableListOf<Int>()

        override var state: PageDelegate.State = PageDelegate.State.IDLE
            private set

        override var direction: PageDelegate.Direction = PageDelegate.Direction.NONE
            private set

        override fun onTouch(event: MotionEvent): Boolean {
            actions += event.action
            state = when (event.action) {
                MotionEvent.ACTION_DOWN -> PageDelegate.State.DRAGGING
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> PageDelegate.State.IDLE
                else -> state
            }
            return true
        }

        override fun onDraw(canvas: Canvas, current: CanvasRecorder, target: CanvasRecorder) = Unit

        override fun startNext() {
            direction = PageDelegate.Direction.NEXT
        }

        override fun startPrev() {
            direction = PageDelegate.Direction.PREV
        }

        override fun abort() {
            state = PageDelegate.State.IDLE
            direction = PageDelegate.Direction.NONE
        }

        override fun confirmPageSettled() = Unit

        override fun setCallback(callback: PageDelegate.Callback) = Unit
    }
}
