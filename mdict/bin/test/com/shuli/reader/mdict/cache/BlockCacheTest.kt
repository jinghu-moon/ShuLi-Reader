package com.shuli.reader.mdict.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockCacheTest {

    @Test
    fun `loader called once per key until evicted`() {
        val cache = BlockCache<String>(capacity = 2)
        var calls = 0
        val load: (Int) -> String = { k -> calls++; "v$k" }

        assertEquals("v1", cache.getOrPut(1, load))
        assertEquals("v1", cache.getOrPut(1, load)) // 命中，不再调 loader
        assertEquals(1, calls)

        assertEquals("v2", cache.getOrPut(2, load))
        assertEquals(2, calls)
    }

    @Test
    fun `evicts least recently used`() {
        val cache = BlockCache<String>(capacity = 2)
        val load: (Int) -> String = { k -> "v$k" }

        cache.getOrPut(1, load)
        cache.getOrPut(2, load)
        cache.getOrPut(1, load)          // 1 变为最近使用
        cache.getOrPut(3, load)          // 淘汰最久未用的 2
        assertEquals(2, cache.size)

        var reloaded = false
        cache.getOrPut(2) { reloaded = true; "v2" } // 2 已被淘汰，需重载
        assertEquals(true, reloaded)
    }

    @Test
    fun `clear empties cache`() {
        val cache = BlockCache<String>(capacity = 4)
        cache.getOrPut(1) { "v1" }
        cache.clear()
        assertEquals(0, cache.size)
    }
}
