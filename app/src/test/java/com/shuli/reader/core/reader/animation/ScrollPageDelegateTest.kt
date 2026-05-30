package com.shuli.reader.core.reader.animation

import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import com.shuli.reader.core.canvasrecorder.CanvasRecorder
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

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
    fun releaseAfterSlowSwipe_entersIdleState() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, y = 510f))

        assertEquals(PageDelegate.State.IDLE, delegate.state)
    }

    @Test
    fun fastSwipe_startsInertiaScroll() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, y = 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, y = 300f))

        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
    }

    @Test
    fun startNext_scrollsOneScreenDown() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.startNext()

        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
    }

    @Test
    fun startPrev_scrollsOneScreenUp() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.startPrev()

        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
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
    fun scrollingDownBeyondOneScreen_triggersChapterChange() {
        val delegate = ScrollPageDelegate()
        delegate.setCallback(callback)

        delegate.setScrollPosition(-2000f)
        delegate.startNext()

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ReaderMotionTokens.MEDIUM_MS + 100L))

        verify(atLeast = 1) { callback.onPageChanged(any()) }
    }
}
