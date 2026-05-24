/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.canvasrecorder

import android.graphics.Canvas
import android.graphics.Picture
import com.shuli.reader.core.canvasrecorder.internal.synchronizedPool
import com.shuli.reader.core.canvasrecorder.pools.PicturePool

class CanvasRecorderApi23Impl : BaseCanvasRecorder() {

    private var picture: Picture? = null

    override val width get() = picture?.width ?: -1
    override val height get() = picture?.height ?: -1

    private fun initPicture() {
        if (picture == null) {
            picture = picturePool.obtain()
        }
    }

    override fun beginRecording(width: Int, height: Int): Canvas {
        initPicture()
        return picture!!.beginRecording(width, height)
    }

    override fun endRecording() {
        picture!!.endRecording()
        super.endRecording()
    }

    override fun draw(canvas: Canvas) {
        if (picture == null) return
        canvas.drawPicture(picture!!)
    }

    override fun recycle() {
        super.recycle()
        if (picture == null) return
        picturePool.recycle(picture!!)
        picture = null
    }

    companion object {
        private val picturePool = PicturePool().synchronizedPool()
    }

}
