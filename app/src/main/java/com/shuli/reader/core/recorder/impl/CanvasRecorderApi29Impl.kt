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
import com.shuli.reader.core.recorder.pools.PicturePool
import com.shuli.reader.core.recorder.pools.RenderNodePool

@RequiresApi(Build.VERSION_CODES.Q)
class CanvasRecorderApi29Impl : BaseCanvasRecorder() {

    private var renderNode: RenderNode? = null
    private var picture: Picture? = null

    override val width get() = renderNode?.width ?: -1
    override val height get() = renderNode?.height ?: -1

    private fun init() {
        if (renderNode == null) {
            renderNode = renderNodePool.obtain()
        }
        if (picture == null) {
            picture = picturePool.obtain()
        }
    }

    private fun flushRenderNode() {
        val rn = renderNode ?: return
        val pic = picture ?: return
        val rc = rn.beginRecording()
        rc.drawPicture(pic)
        rn.endRecording()
    }

    override fun beginRecording(width: Int, height: Int): Canvas {
        init()
        val rn = renderNode ?: throw IllegalStateException("renderNode is null after init")
        var pic = picture ?: throw IllegalStateException("picture is null after init")
        // 确保 Picture 不在录制状态，如果无法结束则丢弃并获取新的
        try {
            pic.endRecording()
        } catch (_: IllegalStateException) {
            // Picture 处于异常状态，丢弃并获取新的
            picturePool.recycle(pic)
            pic = picturePool.obtain()
            picture = pic
        }
        rn.setPosition(0, 0, width, height)
        return pic.beginRecording(width, height)
    }

    override fun endRecording() {
        picture?.endRecording()
        flushRenderNode()
        super.endRecording()
    }

    override fun draw(canvas: Canvas) {
        if (renderNode == null || picture == null) {
            return
        }
        if (canvas.isHardwareAccelerated) {
            if (!renderNode!!.hasDisplayList()) {
                flushRenderNode()
            }
            canvas.drawRenderNode(renderNode!!)
        } else {
            canvas.drawPicture(picture!!)
        }
    }

    override fun recycle() {
        super.recycle()
        // 确保 Picture 结束录制状态再回收
        try {
            picture?.endRecording()
        } catch (_: IllegalStateException) {
            // 已经结束录制，忽略
        }
        renderNode?.let { renderNodePool.recycle(it) }
        renderNode = null
        picture?.let { picturePool.recycle(it) }
        picture = null
    }

    companion object {
        private val picturePool = PicturePool().synchronizedPool()
        private val renderNodePool = RenderNodePool().synchronizedPool()
    }

}
