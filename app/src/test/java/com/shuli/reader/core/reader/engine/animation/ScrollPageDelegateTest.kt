package com.shuli.reader.core.reader.engine.animation

import android.view.MotionEvent
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 连续流 [ScrollPageDelegate] 单元测试。
 *
 * 验证自由偏移累加 + 按实际页高度回收页面（[ScrollPageDelegate.Pager]）的核心逻辑。
 */
@RunWith(RobolectricTestRunner::class)
class ScrollPageDelegateTest {

    private val callback = mockk<PageDelegate.Callback>(relaxed = true)

    /** 可配置页高度的假回收器，记录前进/后退次数。 */
    private class FakePager(
        var segmentHeight: Float = 1000f,
        var viewport: Float = 1920f,
        var maxForward: Int = Int.MAX_VALUE,
        var maxBackward: Int = Int.MAX_VALUE,
    ) : ScrollPageDelegate.Pager {
        var forwardCount = 0
        var backwardCount = 0
        override fun currentSegmentHeight(): Float = segmentHeight
        override fun viewportHeight(): Float = viewport
        override fun advanceForward(): Boolean {
            if (forwardCount >= maxForward) return false
            forwardCount++
            return true
        }
        override fun advanceBackward(): Boolean {
            if (backwardCount >= maxBackward) return false
            backwardCount++
            return true
        }
    }

    private var time = 1000L

    private fun event(action: Int, y: Float): MotionEvent {
        time += 16
        return MotionEvent.obtain(1000L, time, action, 500f, y, 0)
    }

    private fun newDelegate(pager: ScrollPageDelegate.Pager? = null): ScrollPageDelegate {
        return ScrollPageDelegate().apply {
            setCallback(callback)
            this.pager = pager
        }
    }

    @Test
    fun initialState_isIdle() {
        val delegate = ScrollPageDelegate()
        assertEquals(PageDelegate.State.IDLE, delegate.state)
        assertEquals(0f, delegate.getScrollPosition(), 0.01f)
    }

    @Test
    fun actionDown_entersDraggingState() {
        val delegate = newDelegate()
        delegate.onTouch(event(MotionEvent.ACTION_DOWN, 500f))
        assertEquals(PageDelegate.State.DRAGGING, delegate.state)
    }

    @Test
    fun dragUp_accumulatesNegativeOffset_andSetsNextDirection() {
        val delegate = newDelegate(FakePager(segmentHeight = 1000f))
        delegate.onTouch(event(MotionEvent.ACTION_DOWN, 500f))
        delegate.onTouch(event(MotionEvent.ACTION_MOVE, 400f)) // 手指上移 100 → 正文上移
        assertEquals(-100f, delegate.getScrollPosition(), 0.5f)
        assertEquals(PageDelegate.Direction.NEXT, delegate.direction)
    }

    @Test
    fun dragDown_atBookStart_clampsToZero() {
        // 无上一页（maxBackward=0）：向下拖拽不应产生正偏移
        val delegate = newDelegate(FakePager(segmentHeight = 1000f, maxBackward = 0))
        delegate.onTouch(event(MotionEvent.ACTION_DOWN, 500f))
        delegate.onTouch(event(MotionEvent.ACTION_MOVE, 620f)) // 手指下移 120
        assertEquals(0f, delegate.getScrollPosition(), 0.5f)
    }

    @Test
    fun scrollPastSegmentHeight_recyclesForwardOnce() {
        val pager = FakePager(segmentHeight = 300f)
        val delegate = newDelegate(pager)
        delegate.onTouch(event(MotionEvent.ACTION_DOWN, 500f))
        // 上移 350 > 一页(300) → 回收一页，残余偏移 = -50
        delegate.onTouch(event(MotionEvent.ACTION_MOVE, 150f))
        assertEquals(1, pager.forwardCount)
        assertEquals(-50f, delegate.getScrollPosition(), 0.5f)
    }

    @Test
    fun scrollPastMultipleSegments_recyclesMultipleTimes() {
        val pager = FakePager(segmentHeight = 100f)
        val delegate = newDelegate(pager)
        delegate.onTouch(event(MotionEvent.ACTION_DOWN, 500f))
        // 上移 250 → 跨越 2 页(100+100)，残余 -50
        delegate.onTouch(event(MotionEvent.ACTION_MOVE, 250f))
        assertEquals(2, pager.forwardCount)
        assertEquals(-50f, delegate.getScrollPosition(), 0.5f)
    }

    @Test
    fun scrollForwardAtBookEnd_clampsWithoutRecycle() {
        // 末页且内容短于视口：不允许上移留白
        val pager = FakePager(segmentHeight = 300f, viewport = 1920f, maxForward = 0)
        val delegate = newDelegate(pager)
        delegate.onTouch(event(MotionEvent.ACTION_DOWN, 500f))
        delegate.onTouch(event(MotionEvent.ACTION_MOVE, 100f)) // 上移 400 > 300
        assertEquals(0, pager.forwardCount)
        // 内容(300) < 视口(1920) → 钳制为 0
        assertEquals(0f, delegate.getScrollPosition(), 0.5f)
    }

    @Test
    fun abort_resetsState() {
        val delegate = newDelegate(FakePager())
        delegate.onTouch(event(MotionEvent.ACTION_DOWN, 500f))
        delegate.onTouch(event(MotionEvent.ACTION_MOVE, 400f))
        delegate.abort()
        assertEquals(PageDelegate.State.IDLE, delegate.state)
        assertEquals(PageDelegate.Direction.NONE, delegate.direction)
    }

    @Test
    fun setAndResetScrollPosition() {
        val delegate = newDelegate()
        delegate.setScrollPosition(-500f, active = true)
        assertEquals(-500f, delegate.getScrollPosition(), 0.01f)
        assertTrue(delegate.state == PageDelegate.State.DRAGGING)
        delegate.resetScrollPosition()
        assertEquals(0f, delegate.getScrollPosition(), 0.01f)
        assertEquals(PageDelegate.State.IDLE, delegate.state)
    }
}
