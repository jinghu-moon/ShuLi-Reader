/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.canvasrecorder

import android.graphics.Canvas
import android.view.View
import androidx.core.graphics.withSave

inline fun CanvasRecorder.recordIfNeeded(
    width: Int,
    height: Int,
    block: Canvas.() -> Unit
): Boolean {
    if (!needRecord()) return false
    record(width, height, block)
    return true
}

fun CanvasRecorder.recordIfNeeded(view: View): Boolean {
    if (!needRecord()) return false
    record(view.width, view.height) {
        view.draw(this)
    }
    return true
}

inline fun CanvasRecorder.record(width: Int, height: Int, block: Canvas.() -> Unit) {
    val canvas = beginRecording(width, height)
    try {
        canvas.withSave {
            block()
        }
    } finally {
        endRecording()
    }
}

inline fun CanvasRecorder.recordIfNeededThenDraw(
    canvas: Canvas,
    width: Int,
    height: Int,
    block: Canvas.() -> Unit
) {
    recordIfNeeded(width, height, block)
    draw(canvas)
}
