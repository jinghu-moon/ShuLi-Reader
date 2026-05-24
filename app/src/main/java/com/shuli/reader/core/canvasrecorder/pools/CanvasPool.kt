/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.canvasrecorder.pools

import android.graphics.Canvas
import androidx.core.util.Pools

class CanvasPool(size: Int) {

    private val pool = Pools.SynchronizedPool<Canvas>(size)

    fun obtain(): Canvas {
        val canvas = pool.acquire() ?: Canvas()
        return canvas
    }

    fun recycle(canvas: Canvas) {
        canvas.setBitmap(null)
        canvas.restoreToCount(1)
        pool.release(canvas)
    }

}
