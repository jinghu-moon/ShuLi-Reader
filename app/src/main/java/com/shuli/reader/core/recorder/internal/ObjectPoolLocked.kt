/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.recorder.internal

class ObjectPoolLocked<T>(private val delegate: ObjectPool<T>) : ObjectPool<T> by delegate {

    @Synchronized
    override fun obtain(): T {
        return delegate.obtain()
    }

    @Synchronized
    override fun recycle(target: T) {
        return delegate.recycle(target)
    }

}
