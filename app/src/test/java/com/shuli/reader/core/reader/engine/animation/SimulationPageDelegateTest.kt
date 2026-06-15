package com.shuli.reader.core.reader.engine.animation

import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import com.shuli.reader.core.recorder.CanvasRecorder
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SimulationPageDelegateTest {

    private lateinit var callback: PageDelegate.Callback
    private lateinit var canvas: Canvas
    private lateinit var currentRecorder: CanvasRecorder
    private lateinit var targetRecorder: CanvasRecorder

    @Before
    fun setup() {
        callback = mockk(relaxed = true)
        canvas = mockk(relaxed = true)
        currentRecorder = mockk(relaxed = true)
        targetRecorder = mockk(relaxed = true)

        every { canvas.width } returns 1080
        every { canvas.height } returns 1920
    }

    private fun createMotionEvent(action: Int, x: Float, y: Float = 960f): MotionEvent {
        return mockk {
            every { getAction() } returns action
            every { getX() } returns x
            every { getY() } returns y
        }
    }

    @Test
    fun initialState_isIdle() {
        val delegate = SimulationPageDelegate()
        assertEquals(PageDelegate.State.IDLE, delegate.state)
        assertEquals(PageDelegate.Direction.NONE, delegate.direction)
    }

    @Test
    fun actionDown_entersDraggingState() {
        val delegate = SimulationPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 500f))
        assertEquals(PageDelegate.State.DRAGGING, delegate.state)
    }

    @Test
    fun drag_updatesPositionAndRequestsInvalidation() {
        val delegate = SimulationPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, 300f))

        assertEquals(PageDelegate.State.DRAGGING, delegate.state)
        verify { callback.invalidate() }
    }

    @Test
    fun leftSwipeOverThreshold_triggersNextPage() {
        val delegate = SimulationPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 800f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, 200f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, 200f))

        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
        assertEquals(PageDelegate.Direction.NEXT, delegate.direction)
    }

    @Test
    fun rightSwipeOverThreshold_triggersPreviousPage() {
        val delegate = SimulationPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 200f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, 800f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, 800f))

        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
        assertEquals(PageDelegate.Direction.PREV, delegate.direction)
    }

    @Test
    fun smallSwipe_triggersRebound() {
        val delegate = SimulationPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, 550f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, 550f))

        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
    }

    @Test
    fun startNext_startsNextPageAnimation() {
        val delegate = SimulationPageDelegate()
        delegate.setCallback(callback)

        delegate.startNext()

        assertEquals(PageDelegate.Direction.NEXT, delegate.direction)
        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
    }

    @Test
    fun startPrev_startsPreviousPageAnimation() {
        val delegate = SimulationPageDelegate()
        delegate.setCallback(callback)

        delegate.startPrev()

        assertEquals(PageDelegate.Direction.PREV, delegate.direction)
        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
    }

    @Test
    fun abort_resetsState() {
        val delegate = SimulationPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 800f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, 200f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, 200f))

        delegate.abort()

        assertEquals(PageDelegate.State.IDLE, delegate.state)
        assertEquals(PageDelegate.Direction.NONE, delegate.direction)
    }

    @Test
    fun onDrawInIdleState_drawsCurrentPage() {
        val delegate = SimulationPageDelegate()
        delegate.setCallback(callback)

        delegate.onDraw(canvas, currentRecorder, targetRecorder)

        verify { currentRecorder.draw(canvas) }
    }

    @Test
    fun setCallback_receivesCallback() {
        val delegate = SimulationPageDelegate()
        delegate.setCallback(callback)

        delegate.startNext()

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ReaderMotionTokens.LONG_MS + 150L))
        verify(atLeast = 1) { callback.onPageChanged(any()) }
    }
}
