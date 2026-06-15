/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.recorder.impl

import com.shuli.reader.core.recorder.BaseCanvasRecorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.shuli.reader.core.recorder.pools.CanvasPool

class CanvasRecorderBitmapImpl : BaseCanvasRecorder() {

    var bitmap: Bitmap? = null
    var canvas: Canvas? = null

    override val width get() = bitmap?.width ?: -1
    override val height get() = bitmap?.height ?: -1

    private fun init(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        if (bitmap!!.width != width || bitmap!!.height != height) {
            if (bitmap!!.isMutable && canReconfigure(width, height)) {
                bitmap!!.reconfigure(width, height, Bitmap.Config.ARGB_8888)
            } else {
                bitmap!!.recycle()
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }
        }
    }

    private fun canReconfigure(width: Int, height: Int): Boolean {
        return bitmap!!.allocationByteCount >= width * height * 4
    }

    override fun beginRecording(width: Int, height: Int): Canvas {
        init(width, height)
        bitmap?.eraseColor(Color.TRANSPARENT)
        canvas = canvasPool.obtain().apply { setBitmap(bitmap) }
        return canvas!!
    }

    override fun endRecording() {
        bitmap?.prepareToDraw()
        super.endRecording()
        canvas?.let { canvasPool.recycle(it) }
        canvas = null
    }

    override fun draw(canvas: Canvas) {
        val b = bitmap ?: return
        if (!b.isRecycled) {
            canvas.drawBitmap(b, 0f, 0f, null)
        }
    }

    override fun recycle() {
        super.recycle()
        val b = bitmap ?: return
        bitmap = null
        b.recycle()
    }

    companion object {
        private val canvasPool = CanvasPool(2)
    }

}
