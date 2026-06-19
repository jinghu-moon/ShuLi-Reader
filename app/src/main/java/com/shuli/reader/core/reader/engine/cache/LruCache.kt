package com.shuli.reader.core.reader.engine.cache

import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * LRU 缓存实现（使用 ReadWriteLock 优化读多写少场景的并发性能）
 */
class LruCache<K, V>(
    private val maxSize: Int,
    private val sizeOf: (key: K, value: V) -> Int = { _, _ -> 1 }
) {
    private val cache = LinkedHashMap<K, V>(0, 0.75f, true)
    private var currentSize = 0
    private val lock = ReentrantReadWriteLock()

    fun get(key: K): V? {
        lock.readLock().lock()
        try {
            return cache[key]
        } finally {
            lock.readLock().unlock()
        }
    }

    fun put(key: K, value: V) {
        lock.writeLock().lock()
        try {
            val previous = cache.put(key, value)
            if (previous != null) {
                currentSize -= sizeOf(key, previous)
            }
            currentSize += sizeOf(key, value)
            trimToSize()
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun remove(key: K): V? {
        lock.writeLock().lock()
        try {
            val previous = cache.remove(key)
            if (previous != null) {
                currentSize -= sizeOf(key, previous)
            }
            return previous
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun clear() {
        lock.writeLock().lock()
        try {
            cache.clear()
            currentSize = 0
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun size(): Int {
        lock.readLock().lock()
        try {
            return currentSize
        } finally {
            lock.readLock().unlock()
        }
    }

    fun count(): Int {
        lock.readLock().lock()
        try {
            return cache.size
        } finally {
            lock.readLock().unlock()
        }
    }

    fun containsKey(key: K): Boolean {
        lock.readLock().lock()
        try {
            return cache.containsKey(key)
        } finally {
            lock.readLock().unlock()
        }
    }

    private fun trimToSize() {
        while (currentSize > maxSize && cache.isNotEmpty()) {
            val eldest = cache.entries.iterator().next()
            val value = eldest.value
            cache.remove(eldest.key)
            currentSize -= sizeOf(eldest.key, value)
        }
    }

    fun stats(): CacheStats {
        lock.readLock().lock()
        try {
            return CacheStats(
                size = cache.size,
                maxSize = maxSize,
            )
        } finally {
            lock.readLock().unlock()
        }
    }

    fun removeIf(predicate: (K) -> Boolean) {
        lock.writeLock().lock()
        try {
            val iterator = cache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (predicate(entry.key)) {
                    currentSize -= sizeOf(entry.key, entry.value)
                    iterator.remove()
                }
            }
        } finally {
            lock.writeLock().unlock()
        }
    }
}

/**
 * 缓存统计信息
 */
data class CacheStats(
    val size: Int,
    val maxSize: Int,
) {
    val hitRate: Float
        get() = if (maxSize > 0) size.toFloat() / maxSize else 0f
}