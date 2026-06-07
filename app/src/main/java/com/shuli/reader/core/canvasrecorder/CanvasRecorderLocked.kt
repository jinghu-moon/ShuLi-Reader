/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.canvasrecorder

import android.graphics.Canvas
import java.util.concurrent.locks.ReentrantLock

class CanvasRecorderLocked(private val delegate: CanvasRecorder) :
    CanvasRecorder by delegate {

    var lock: ReentrantLock? = ReentrantLock()

    /** recycle() 后置位，后续 beginRecording/recordIfNeeded 直接短路，避免在已回收的 delegate 上操作。 */
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
