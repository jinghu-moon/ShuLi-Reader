/*
 * Adapted from legado (https://github.com/gedoor/legado)
 * Copyright (C) gedoor, licensed under GPL-3.0.
 *
 * This file is part of ShuLi-Reader, also licensed under GPL-3.0.
 */
package com.shuli.reader.core.canvasrecorder.internal

import androidx.annotation.CallSuper
import androidx.core.util.Pools

abstract class BaseObjectPool<T : Any>(size: Int) : ObjectPool<T> {

    open val pool = Pools.SimplePool<T>(size)

    override fun obtain(): T {
        return pool.acquire() ?: create()
    }

    @CallSuper
    override fun recycle(target: T) {
        pool.release(target)
    }

}
