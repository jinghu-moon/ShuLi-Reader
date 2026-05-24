/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.canvasrecorder

import android.os.Build

object CanvasRecorderFactory {

    private val atLeastApi24 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    private val atLeastApi29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val isSupport = atLeastApi24

    /** 渲染优化总开关，调试时可关闭以降级到 CanvasRecorderImpl（无缓存，等价每帧重画）。 */
    var optimizeRender: Boolean = true

    fun create(locked: Boolean = false): CanvasRecorder {
        val impl = when {
            !optimizeRender -> CanvasRecorderImpl()
            atLeastApi29 -> CanvasRecorderApi29Impl()
            atLeastApi24 -> CanvasRecorderApi23Impl()
            else -> CanvasRecorderImpl()
        }
        return if (locked) {
            CanvasRecorderLocked(impl)
        } else {
            impl
        }
    }

}
