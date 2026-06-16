/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.recorder.impl

import com.shuli.reader.core.recorder.BaseCanvasRecorder

import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import com.shuli.reader.core.recorder.internal.synchronizedPool
import com.shuli.reader.core.recorder.pools.RenderNodePool

/**
 * API 29+ 实现：Picture + RenderNode。
 *
 * Picture 不再池化（PR-1）：AOSP Picture 有严格的状态机（beginRecording/endRecording 必须成对），
 * 池化会导致 recording 状态跨实例污染。每次 init 直接 new Picture()。
 *
 * RenderNode 仍保留池化：创建成本较高（涉及 native 分配），且 draw() 时 lazy flush（PR-2）
 * 确保 flush 只发生在 UI 线程。
 */
@RequiresApi(Build.VERSION_CODES.Q)
class CanvasRecorderApi29Impl : BaseCanvasRecorder() {

    private var renderNode: RenderNode? = null
    private var picture: Picture? = null

    /** draw() 时检查：RenderNode 需要在 UI 线程重新 flush */
    private var renderNodeDirty = true

    override val width get() = renderNode?.width ?: -1
    override val height get() = renderNode?.height ?: -1

    private fun init() {
        if (renderNode == null) {
            renderNode = renderNodePool.obtain()
        }
        if (picture == null) {
            // PR-1: 直接创建，不从池中获取
            picture = Picture()
        }
    }

    /**
     * 将 Picture 内容 flush 到 RenderNode 的 display list。
     *
     * PR-2: 此方法只应在 UI 线程调用（draw 时 lazy flush），
     * 后台线程不再调用 flushRenderNode()。
     */
    private fun flushRenderNode() {
        val rn = renderNode ?: return
        val pic = picture ?: return
        val rc = rn.beginRecording()
        rc.drawPicture(pic)
        rn.endRecording()
        renderNodeDirty = false
    }

    override fun beginRecording(width: Int, height: Int): Canvas {
        init()
        val rn = renderNode ?: throw IllegalStateException("renderNode is null after init")
        val pic = picture ?: throw IllegalStateException("picture is null after init")
        // PR-1: Picture 是新创建的，不需要检查旧状态
        rn.setPosition(0, 0, width, height)
        renderNodeDirty = true  // 标记 RenderNode 需要重新 flush
        return pic.beginRecording(width, height)
    }

    override fun endRecording() {
        picture?.endRecording()
        // PR-2: 不再在后台线程 flush RenderNode，只标记 dirty
        // flushRenderNode() 将在 UI 线程的 draw() 中执行
        super.endRecording()
    }

    override fun draw(canvas: Canvas) {
        val pic = picture ?: return
        if (canvas.isHardwareAccelerated) {
            val rn = renderNode ?: return
            // PR-2: UI 线程 lazy flush
            if (renderNodeDirty || !rn.hasDisplayList()) {
                flushRenderNode()
            }
            canvas.drawRenderNode(rn)
        } else {
            canvas.drawPicture(pic)
        }
    }

    override fun recycle() {
        super.recycle()
        // 确保 Picture 结束录制状态
        try {
            picture?.endRecording()
        } catch (_: IllegalStateException) {
            // 已经结束录制，忽略
        }
        renderNode?.let { renderNodePool.recycle(it) }
        renderNode = null
        // PR-1: Picture 不回池，直接丢弃等待 GC
        picture = null
        renderNodeDirty = true
    }

    companion object {
        // PR-1: 移除 picturePool
        private val renderNodePool = RenderNodePool().synchronizedPool()
    }

}
