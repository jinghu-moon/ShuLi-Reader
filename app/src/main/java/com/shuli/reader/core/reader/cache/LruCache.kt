package com.shuli.reader.core.reader.cache

/**
 * LRU 缓存实现（线程安全）
 */
class LruCache<K, V>(
    private val maxSize: Int,
) {
    private val cache = LinkedHashMap<K, V>(maxSize, 0.75f, true)

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
        cache[key] = value
        trimToSize()
    }

    /**
     * 移除缓存
     */
    @Synchronized
    fun remove(key: K): V? {
        return cache.remove(key)
    }

    /**
     * 清空缓存
     */
    @Synchronized
    fun clear() {
        cache.clear()
    }

    /**
     * 获取缓存大小
     */
    @Synchronized
    fun size(): Int {
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
        while (cache.size > maxSize) {
            val eldest = cache.entries.iterator().next()
            cache.remove(eldest.key)
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