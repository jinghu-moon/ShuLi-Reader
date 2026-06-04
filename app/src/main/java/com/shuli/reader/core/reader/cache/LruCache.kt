package com.shuli.reader.core.reader.cache

/**
 * LRU 缓存实现（线程安全）
 */
class LruCache<K, V>(
    private val maxSize: Int,
    private val sizeOf: (key: K, value: V) -> Int = { _, _ -> 1 }
) {
    private val cache = LinkedHashMap<K, V>(0, 0.75f, true)
    private var currentSize = 0

    /**
     * 获取缓存值
     */
    @Synchronized
    fun get(key: K): V? {
        return cache[key]
    }

    /**
     * 存入缓存
     */
    @Synchronized
    fun put(key: K, value: V) {
        val previous = cache.put(key, value)
        if (previous != null) {
            currentSize -= sizeOf(key, previous)
        }
        currentSize += sizeOf(key, value)
        trimToSize()
    }

    /**
     * 移除缓存
     */
    @Synchronized
    fun remove(key: K): V? {
        val previous = cache.remove(key)
        if (previous != null) {
            currentSize -= sizeOf(key, previous)
        }
        return previous
    }

    /**
     * 清空缓存
     */
    @Synchronized
    fun clear() {
        cache.clear()
        currentSize = 0
    }

    /**
     * 获取当前计算的缓存大小（可能是字节数也可能是条目数，取决于 sizeOf）
     */
    @Synchronized
    fun size(): Int {
        return currentSize
    }

    /**
     * 获取缓存条目数
     */
    @Synchronized
    fun count(): Int {
        return cache.size
    }

    /**
     * 检查是否包含 key
     */
    @Synchronized
    fun containsKey(key: K): Boolean {
        return cache.containsKey(key)
    }

    /**
     * 裁剪到最大大小
     */
    private fun trimToSize() {
        while (currentSize > maxSize && cache.isNotEmpty()) {
            val eldest = cache.entries.iterator().next()
            val value = eldest.value
            cache.remove(eldest.key)
            currentSize -= sizeOf(eldest.key, value)
        }
    }

    /**
     * 获取缓存统计信息
     */
    @Synchronized
    fun stats(): CacheStats {
        return CacheStats(
            size = cache.size,
            maxSize = maxSize,
        )
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