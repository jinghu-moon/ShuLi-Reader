package com.shuli.reader.core.reader.cache

/**
 * 应用级章节缓存仓库：跨 ReaderViewModel 生命周期复用分页结果。
 *
 * - 每本书独立的 [CacheManager]，最多缓存 [MAX_CACHED_BOOKS] 本书
 * - 超过 [TTL_MS] 未访问的缓存自动淘汰
 * - 响应系统内存压力释放最老的缓存
 */
object BookCacheStore {

    private const val MAX_CACHED_BOOKS = 3
    private const val TTL_MS = 5 * 60 * 1000L // 5 分钟

    private data class Entry(
        val cache: CacheManager,
        val lastAccess: Long,
    )

    private val books = LinkedHashMap<String, Entry>(MAX_CACHED_BOOKS, 0.75f, true)

    /**
     * 获取指定书籍的缓存管理器，不存在则创建。
     */
    @Synchronized
    fun getBookCache(bookId: String): CacheManager {
        evictExpired()
        return books.getOrPut(bookId) {
            Entry(CacheManager.forMemoryClass(memoryClassMb()), System.currentTimeMillis())
        }.also {
            books[bookId] = it.copy(lastAccess = System.currentTimeMillis())
        }.cache
    }

    /**
     * 释放指定书籍的缓存（退出阅读页时调用）。
     */
    @Synchronized
    fun releaseBook(bookId: String) {
        books.remove(bookId)?.cache?.clear()
    }

    /**
     * 响应系统内存压力。
     */
    @Synchronized
    fun onTrimMemory(level: Int) {
        when {
            level >= 80 -> {
                // 完全释放
                books.values.forEach { it.cache.clear() }
                books.clear()
            }
            level >= 60 -> {
                // 释放超过一半容量的最老条目
                val now = System.currentTimeMillis()
                val evictable = books.entries
                    .filter { now - it.value.lastAccess > TTL_MS / 2 }
                    .sortedBy { it.value.lastAccess }
                val count = evictable.size / 2 + 1
                evictable.take(count).forEach { (id, _) ->
                    books.remove(id)?.cache?.clear()
                }
            }
        }
    }

    private fun evictExpired() {
        val now = System.currentTimeMillis()
        val expired = books.entries.filter { now - it.value.lastAccess > TTL_MS }
        expired.forEach { (id, _) ->
            books.remove(id)?.cache?.clear()
        }
        // 超出数量限制时淘汰最老的
        while (books.size > MAX_CACHED_BOOKS) {
            val eldest = books.entries.iterator().next()
            books.remove(eldest.key)?.cache?.clear()
        }
    }

    private fun memoryClassMb(): Int {
        return (Runtime.getRuntime().maxMemory() / 1024 / 1024).toInt().coerceIn(128, 1024)
    }
}
