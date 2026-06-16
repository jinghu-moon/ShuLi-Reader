/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.recorder

import android.graphics.Canvas
import android.graphics.Picture
import java.util.concurrent.locks.ReentrantLock

/**
 * 线程安全装饰器：为 [CanvasRecorder] 加锁 + 终态保护。
 *
 * PR-3a: recycle() 后不再允许复活。beginRecording() 在 recycled 状态下返回 dummy Canvas，
 * 避免后台线程因 TOCTOU 竞态条件崩溃。
 */
class CanvasRecorderLocked(private val delegate: CanvasRecorder) :
    CanvasRecorder by delegate {

    var lock: ReentrantLock? = ReentrantLock()

    /** recycle() 后置位，不可逆。 */
    @Volatile
    private var recycled: Boolean = false

    /** dummy Picture，用于在 recycled 状态下返回无害的 Canvas */
    private val dummyPicture = Picture()

    private fun initLock() {
        if (lock == null) {
            synchronized(this) {
                if (lock == null) {
                    lock = ReentrantLock()
                }
            }
        }
    }

    /**
     * PR-3a: recycled 后返回 dummy Canvas，不抛异常。
     *
     * 后台线程可能在 recycle() 之前提交了渲染任务，任务执行时 recorder 已被 recycle。
     * 此时返回 dummy Canvas 让任务"录制"到空 Picture，避免崩溃。
     */
    override fun beginRecording(width: Int, height: Int): Canvas {
        if (recycled) {
            // 返回 dummy Canvas，后续 endRecording/draw 都是 no-op
            return dummyPicture.beginRecording(width, height)
        }
        initLock()
        lock!!.lock()
        // double-check：获取锁后可能已被 recycle
        if (recycled) {
            lock!!.unlock()
            return dummyPicture.beginRecording(width, height)
        }
        return delegate.beginRecording(width, height)
    }

    override fun endRecording() {
        if (recycled) {
            // recycled 路径：结束 dummy 录制（如果有），避免 dummyPicture 卡在 recording 状态
            try { dummyPicture.endRecording() } catch (_: IllegalStateException) {}
            return
        }
        val l = lock ?: return
        try {
            delegate.endRecording()
        } catch (_: IllegalStateException) {
            // 已经结束录制，忽略
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

    /**
     * 回收资源。调用后此实例不可再使用（终态）。
     */
    override fun recycle() {
        val l = lock ?: return
        l.lock()
        try {
            if (!recycled) {
                recycled = true
                try {
                    delegate.endRecording()
                } catch (_: IllegalStateException) {
                    // 已经结束录制，忽略
                }
                delegate.recycle()
            }
            // 统一结束 dummy 录制（如果有后台线程在 recycled 后调用了 beginRecording）
            try { dummyPicture.endRecording() } catch (_: IllegalStateException) {}
        } finally {
            if (l.isHeldByCurrentThread) l.unlock()
        }
    }

}
