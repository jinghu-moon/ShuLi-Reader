/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.canvasrecorder

import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import com.shuli.reader.core.canvasrecorder.internal.synchronizedPool
import com.shuli.reader.core.canvasrecorder.pools.PicturePool
import com.shuli.reader.core.canvasrecorder.pools.RenderNodePool

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
        val rc = renderNode!!.beginRecording()
        rc.drawPicture(picture!!)
        renderNode!!.endRecording()
    }

    override fun beginRecording(width: Int, height: Int): Canvas {
        init()
        renderNode!!.setPosition(0, 0, width, height)
        return picture!!.beginRecording(width, height)
    }

    override fun endRecording() {
        picture!!.endRecording()
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
        if (renderNode == null || picture == null) return
        renderNodePool.recycle(renderNode!!)
        renderNode = null
        picturePool.recycle(picture!!)
        picture = null
    }

    companion object {
        private val picturePool = PicturePool().synchronizedPool()
        private val renderNodePool = RenderNodePool().synchronizedPool()
    }

}
