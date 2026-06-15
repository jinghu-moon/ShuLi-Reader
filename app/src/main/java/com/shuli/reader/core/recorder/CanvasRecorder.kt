/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.recorder

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

    /** recycle() 后为 true；locked recorder 可在下一次 beginRecording() 时复活。 */
    fun isRecycled(): Boolean = false

}
