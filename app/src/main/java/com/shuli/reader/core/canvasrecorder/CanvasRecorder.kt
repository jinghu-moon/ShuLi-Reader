/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.canvasrecorder

import android.graphics.Canvas

interface CanvasRecorder {

    val width: Int

    val height: Int

    fun beginRecording(width: Int, height: Int): Canvas

    fun endRecording()

    fun draw(canvas: Canvas)

    fun invalidate()

    fun recycle()

    fun isDirty(): Boolean

    fun isLocked(): Boolean

    fun needRecord(): Boolean

    /** recycle() 后为 true，recordIfNeeded 据此短路避免在已回收的 delegate 上操作。 */
    fun isRecycled(): Boolean = false

}
