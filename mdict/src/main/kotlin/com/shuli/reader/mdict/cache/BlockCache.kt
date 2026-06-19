package com.shuli.reader.mdict.cache

/**
 * 简单的 LRU 缓存。对应 docs/38 §8.2。
 *
 * 用于缓存解压后的 key/record block，避免同块连续查词时重复 IO + 解压。
 * 容量以「条目数」计（每条是一个已解压 block），默认很小（4）即可覆盖
 * 阅读时反复查邻近词的局部性，内存可控。
 *
 * 非线程安全：上层若并发查词需自行加锁，或每协程独立实例。
 */
class BlockCache<V>(private val capacity: Int = 4) {

    init {
        require(capacity >= 1) { "capacity must be >= 1" }
    }

    // accessOrder = true → 访问即移到末尾，removeEldestEntry 淘汰最久未用
    private val map = object : LinkedHashMap<Int, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, V>): Boolean =
            size > capacity
    }

    /** 命中返回缓存值；未命中调 [loader] 计算、存入并返回。 */
    fun getOrPut(key: Int, loader: (Int) -> V): V {
        map[key]?.let { return it }
        val value = loader(key)
        map[key] = value
        return value
    }

    fun clear() = map.clear()

    val size: Int get() = map.size
}
