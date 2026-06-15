/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.recorder

import androidx.annotation.CallSuper

abstract class BaseCanvasRecorder : CanvasRecorder {

    @JvmField
    protected var isDirty = true

    override fun invalidate() {
        isDirty = true
    }

    @CallSuper
    override fun recycle() {
        isDirty = true
    }

    @CallSuper
    override fun endRecording() {
        isDirty = false
    }

    override fun isDirty(): Boolean {
        return isDirty
    }

    override fun isLocked(): Boolean {
        return false
    }

    override fun needRecord(): Boolean {
        return isDirty() && !isLocked()
    }

}
