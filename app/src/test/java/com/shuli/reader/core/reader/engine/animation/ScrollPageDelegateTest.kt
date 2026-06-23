package com.shuli.reader.core.reader.engine.animation

import android.graphics.Canvas
import android.view.MotionEvent
import com.shuli.reader.core.recorder.CanvasRecorder
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScrollPageDelegateTest {

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

    private fun createMotionEvent(action: Int, x: Float = 500f, y: Float = 960f): MotionEvent {
        return mockk {
            every { getAction() } returns action
            every { getX() } returns x
            every { getY() } returns y
        }
    }

    @Test
    fun initialState_isIdle() {
        val delegate = ScrollPageDelegate()
        assertEquals(PageDelegate.State.IDLE, delegate.state)
        assertEquals(PageDelegate.Direction.NONE, delegate.direction)
    }

    @Test
    fun actionDown_entersDraggingState() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN))
        assertEquals(PageDelegate.State.DRAGGING, delegate.state)
    }

    @Test
    fun drag_updatesScrollPosition() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, y = 400f))

        assertEquals(-100f, delegate.getScrollPosition(), 1f)
        verify { callback.invalidate() }
    }

    @Test
    fun dragUp_setsNextDirection() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, y = 400f))

        assertEquals(PageDelegate.Direction.NEXT, delegate.direction)
        assertFalse(delegate.isDraggingBackward())
    }

    @Test
    fun dragDown_setsPrevDirection() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, y = 620f))

        assertEquals(PageDelegate.Direction.PREV, delegate.direction)
        assertTrue(delegate.isDraggingBackward())
    }

    @Test
    fun releaseAfterSlowSwipe_entersIdleState() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, y = 510f))

        assertEquals(PageDelegate.State.IDLE, delegate.state)
    }

    @Test
    fun releaseAfterPartialDrag_keepsOffsetWithoutPageChange() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, y = 300f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, y = 300f))

        assertEquals(PageDelegate.State.IDLE, delegate.state)
        assertEquals(-200f, delegate.getScrollPosition(), 1f)
        verify(exactly = 0) { callback.onPageChanged(any()) }
    }

    @Test
    fun startNext_scrollsOneScreenDown() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.startNext()

        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
        assertEquals(PageDelegate.Direction.NEXT, delegate.direction)
    }

    @Test
    fun startPrev_scrollsOneScreenUp() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.startPrev()

        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
        assertEquals(PageDelegate.Direction.PREV, delegate.direction)
    }

    @Test
    fun abort_stopsAnimation() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.startNext()
        delegate.abort()

        assertEquals(PageDelegate.State.IDLE, delegate.state)
        assertEquals(PageDelegate.Direction.NONE, delegate.direction)
    }

    @Test
    fun getScrollPosition_returnsCurrentPosition() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        assertEquals(0f, delegate.getScrollPosition())

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, y = 400f))

        assertEquals(-100f, delegate.getScrollPosition(), 1f)
    }

    @Test
    fun setScrollPosition_updatesPosition() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.setScrollPosition(-500f)

        assertEquals(-500f, delegate.getScrollPosition())
    }

    @Test
    fun onDraw_drawsCurrentPage() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.onDraw(canvas, currentRecorder, targetRecorder)

        verify { currentRecorder.draw(canvas) }
    }

    @Test
    fun onDraw_whenSmallDrag_drawsTargetPage() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, y = 400f))
        delegate.onDraw(canvas, currentRecorder, targetRecorder)

        verify { currentRecorder.draw(canvas) }
        verify { targetRecorder.draw(canvas) }
    }

    @Test
    fun releaseBeyondOneViewport_triggersPageChange() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)
        delegate.setViewportHeight(1000f)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, y = -600f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, y = -600f))

        assertEquals(PageDelegate.State.SETTLING, delegate.state)
        verify(exactly = 1) { callback.onPageChanged(PageDelegate.Direction.NEXT) }
    }

    @Test
    fun releaseBeyondConfiguredScrollExtent_triggersPageChange() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)
        delegate.setViewportHeight(240f)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, y = 230f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, y = 230f))

        assertEquals(PageDelegate.State.SETTLING, delegate.state)
        assertEquals(-240f, delegate.getScrollPosition(), 0.1f)
        verify(exactly = 1) { callback.onPageChanged(PageDelegate.Direction.NEXT) }
    }

    @Test
    fun releaseBeyondBackwardScrollExtent_triggersPrevPageChange() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)
        delegate.setScrollExtents(forwardExtent = 500f, backwardExtent = 180f)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, y = 720f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, y = 720f))

        assertEquals(PageDelegate.State.SETTLING, delegate.state)
        assertEquals(180f, delegate.getScrollPosition(), 0.1f)
        verify(exactly = 1) { callback.onPageChanged(PageDelegate.Direction.PREV) }
    }
}
