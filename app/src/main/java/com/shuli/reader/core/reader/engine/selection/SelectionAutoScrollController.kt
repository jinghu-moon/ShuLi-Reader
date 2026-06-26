package com.shuli.reader.core.reader.engine.selection

import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import androidx.annotation.MainThread
import com.shuli.reader.core.reader.engine.animation.PageDelegate

/**
 * 选区自动滚动控制器。
 *
 * 当手指拖拽把手进入屏幕上下边缘 10% 区域时触发页面连续滚动。
 * 滚动期间需抑制放大镜及上下文菜单的弹出。
 */
@MainThread
class SelectionAutoScrollController(
    private val edgePercent: Float = 0.1f,
    private val scrollCallback: ScrollCallback
) {
    interface ScrollCallback {
        fun scrollBy(dy: Float)
        fun turnPage(direction: PageDelegate.Direction)
        fun onScrollStateChanged(isScrolling: Boolean)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isAutoScrolling = false
    private var scrollSpeed = 0f
    private var isScrollMode = false
    private var viewportHeight = 0f
    
    private val scrollRunnable = object : Runnable {
        override fun run() {
            if (!isAutoScrolling) return
            
            if (isScrollMode) {
                scrollCallback.scrollBy(scrollSpeed)
                handler.postDelayed(this, 16L) // ~60fps
            } else {
                // 翻页模式，触发翻页后暂停等待页面就绪
                val direction = if (scrollSpeed < 0) PageDelegate.Direction.PREV else PageDelegate.Direction.NEXT
                scrollCallback.turnPage(direction)
                // 延迟较长时间，避免快速翻页
                handler.postDelayed(this, 800L)
            }
        }
    }

    fun setViewport(height: Float, isScrollMode: Boolean) {
        this.viewportHeight = height
        this.isScrollMode = isScrollMode
    }

    /**
     * 更新触摸点位置，检测是否需要自动滚动
     */
    fun updateTouch(y: Float) {
        if (viewportHeight <= 0f) return
        
        val topEdge = viewportHeight * edgePercent
        val bottomEdge = viewportHeight * (1 - edgePercent)
        
        var shouldScroll = false
        var speed = 0f
        
        if (y < topEdge) {
            shouldScroll = true
            // 越靠近边缘速度越快
            val ratio = (topEdge - y).coerceIn(0f, topEdge) / topEdge
            speed = -calculateMaxSpeed() * ratio
        } else if (y > bottomEdge) {
            shouldScroll = true
            val ratio = (y - bottomEdge).coerceIn(0f, topEdge) / topEdge
            speed = calculateMaxSpeed() * ratio
        }
        
        if (shouldScroll) {
            scrollSpeed = speed
            if (!isAutoScrolling) {
                isAutoScrolling = true
                scrollCallback.onScrollStateChanged(true)
                handler.post(scrollRunnable)
            }
        } else {
            stopAutoScroll()
        }
    }

    private fun calculateMaxSpeed(): Float {
        // 每帧最大滚动像素
        return 30f
    }

    /**
     * 停止自动滚动
     */
    fun stopAutoScroll() {
        if (isAutoScrolling) {
            isAutoScrolling = false
            handler.removeCallbacks(scrollRunnable)
            scrollCallback.onScrollStateChanged(false)
        }
    }
}
