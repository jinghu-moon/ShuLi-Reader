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
class PageDelegateTest {

    private lateinit var callback: PageDelegate.Callback
    private lateinit var canvas: Canvas

    @Before
    fun setup() {
        callback = mockk(relaxed = true)
        canvas = mockk(relaxed = true)

        every { canvas.width } returns 1080
        every { canvas.height } returns 1920
    }

    private fun createMotionEvent(action: Int, x: Float, y: Float = 100f): MotionEvent {
        return mockk {
            every { getAction() } returns action
            every { getX() } returns x
            every { getY() } returns y
        }
    }

    @Test
    fun noAnimPageDelegate_completesPageChangeImmediately() {
        val delegate = NoAnimPageDelegate()
        delegate.setCallback(callback)

        // 初始状态
        assertEquals(PageDelegate.State.IDLE, delegate.state)
        assertEquals(PageDelegate.Direction.NONE, delegate.direction)

        // 触摸屏幕右侧 -> 下一页
        val downEvent = createMotionEvent(MotionEvent.ACTION_DOWN, 600f)
        delegate.onTouch(downEvent)

        val upEvent = createMotionEvent(MotionEvent.ACTION_UP, 600f)
        delegate.onTouch(upEvent)

        // 应立即调用回调
        verify { callback.onPageChanged(PageDelegate.Direction.NEXT) }
        assertEquals(PageDelegate.State.IDLE, delegate.state)
    }

    @Test
    fun noAnimPageDelegate_touchLeftSide_goesToPreviousPage() {
        val delegate = NoAnimPageDelegate()
        delegate.setCallback(callback)

        // 触摸屏幕左侧
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 200f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, 200f))

        verify { callback.onPageChanged(PageDelegate.Direction.PREV) }
    }

    @Test
    fun noAnimPageDelegateStartNext_triggersNextPageCallback() {
        val delegate = NoAnimPageDelegate()
        delegate.setCallback(callback)

        delegate.startNext()

        verify { callback.onPageChanged(PageDelegate.Direction.NEXT) }
        assertEquals(PageDelegate.Direction.NEXT, delegate.direction)
    }

    @Test
    fun noAnimPageDelegateStartPrev_triggersPreviousPageCallback() {
        val delegate = NoAnimPageDelegate()
        delegate.setCallback(callback)

        delegate.startPrev()

        verify { callback.onPageChanged(PageDelegate.Direction.PREV) }
        assertEquals(PageDelegate.Direction.PREV, delegate.direction)
    }

    @Test
    fun coverPageDelegate_drag_updatesState() {
        val delegate = CoverPageDelegate()
        delegate.setCallback(callback)

        // 按下
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 500f))
        assertEquals(PageDelegate.State.DRAGGING, delegate.state)

        // 移动
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, 300f))
        assertEquals(PageDelegate.State.DRAGGING, delegate.state)

        // 验证重绘请求
        verify { callback.invalidate() }
    }

    @Test
    fun coverPageDelegate_overThreshold_triggersPageChange() {
        val delegate = CoverPageDelegate()
        delegate.setCallback(callback)

        // 模拟向左滑动超过阈值
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 800f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, 200f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, 200f))

        // 应进入动画状态
        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
        assertEquals(PageDelegate.Direction.NEXT, delegate.direction)
    }

    @Test
    fun coverPageDelegate_underThreshold_rebounds() {
        val delegate = CoverPageDelegate()
        delegate.setCallback(callback)

        // 模拟小幅滑动
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 500f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, 450f))

        // 应进入动画状态但方向为 PREV（回弹）
        assertEquals(PageDelegate.State.ANIMATING, delegate.state)
    }

    @Test
    fun horizontalPageDelegate_supportsHorizontalSwipe() {
        val delegate = HorizontalPageDelegate()
        delegate.setCallback(callback)

        // 按下
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 500f))
        assertEquals(PageDelegate.State.DRAGGING, delegate.state)

        // 向左滑动
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_MOVE, 300f))
        verify { callback.invalidate() }
    }

    @Test
    fun pageDelegateAbort_resetsState() {
        val delegate = CoverPageDelegate()
        delegate.setCallback(callback)

        // 先触发动画
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_DOWN, 800f))
        delegate.onTouch(createMotionEvent(MotionEvent.ACTION_UP, 200f))

        // 中断
        delegate.abort()

        assertEquals(PageDelegate.State.IDLE, delegate.state)
        assertEquals(PageDelegate.Direction.NONE, delegate.direction)
    }

    @Test
    fun pageDelegate_supportsReplacingCallback() {
        val delegate = NoAnimPageDelegate()
        val firstCallback = mockk<PageDelegate.Callback>(relaxed = true)
        val secondCallback = mockk<PageDelegate.Callback>(relaxed = true)

        delegate.setCallback(firstCallback)
        delegate.startNext()
        verify { firstCallback.onPageChanged(any()) }

        delegate.setCallback(secondCallback)
        delegate.startNext()
        verify { secondCallback.onPageChanged(any()) }
    }
}
