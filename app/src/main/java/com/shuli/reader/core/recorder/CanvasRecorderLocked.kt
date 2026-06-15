/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.recorder

import android.graphics.Canvas
import java.util.concurrent.locks.ReentrantLock

class CanvasRecorderLocked(private val delegate: CanvasRecorder) :
    CanvasRecorder by delegate {

    var lock: ReentrantLock? = ReentrantLock()

    /** recycle() 后置位；下一次 beginRecording() 会重新初始化底层资源并复活。 */
    @Volatile
    private var recycled: Boolean = false

    private fun initLock() {
        if (lock == null) {
            synchronized(this) {
                if (lock == null) {
                    lock = ReentrantLock()
                }
            }
        }
    }

    override fun beginRecording(width: Int, height: Int): Canvas {
        initLock()
        lock!!.lock()
        // 复用即复活：recycle() 后页面对象可能被缓存复用并重新录制。
        // delegate.beginRecording() 内部会 init() 重新取 Picture/RenderNode，
        // 故此处复位 recycled，使 begin/end/draw 在新生命周期内保持对称——
        // 否则 endRecording 会因 recycled 短路而跳过 picture.endRecording()，
        // 导致 Picture 停留在 recording 状态，下一帧再 beginRecording 即抛
        // "Picture already recording"。
        recycled = false
        return delegate.beginRecording(width, height)
    }

    override fun endRecording() {
        val l = lock ?: return
        try {
            if (!recycled) delegate.endRecording()
        } finally {
            if (l.isHeldByCurrentThread) l.unlock()
        }
    }

    override fun draw(canvas: Canvas) {
        if (recycled) return
        val l = lock ?: return
        l.lock()
        try {
            if (!recycled) delegate.draw(canvas)
        } finally {
            if (l.isHeldByCurrentThread) l.unlock()
        }
    }

    override fun isLocked(): Boolean {
        val l = lock ?: return false
        return l.isLocked
    }

    override fun isRecycled(): Boolean = recycled

    override fun recycle() {
        val l = lock ?: return
        l.lock()
        try {
            if (!recycled) {
                recycled = true
                delegate.recycle()
            }
        } finally {
            if (l.isHeldByCurrentThread) l.unlock()
        }
        // 不再置 lock = null：后续录制调用仍需锁来序列化，否则会和 render 线程竞速。
    }

}
