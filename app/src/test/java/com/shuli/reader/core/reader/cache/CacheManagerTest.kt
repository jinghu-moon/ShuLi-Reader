package com.shuli.reader.core.reader.cache

import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CacheManagerTest {

    @Test
    fun pageCache_storesAndRetrievesValues() {
        val cacheManager = CacheManager(pageCacheSize = 10)
        val key = CacheManager.PageCacheKey(
            bookId = "book1",
            chapterIndex = 0,
            pageIndex = 0,
            textSize = 18f,
            lineHeight = 1.5f,
            pageWidth = 1080,
            pageHeight = 1920,
        )

        val page = TextPage(
            startCharOffset = 0,
            endCharOffset = 100,
            chapterIndex = 0,
            pageIndex = 0,
            pageSize = PageSize(1080, 1920),
            marginHorizontal = 24f,
            lines = emptyList(),
            columns = emptyList(),
        )

        cacheManager.putPage(key, page)
        val cachedPage = cacheManager.getPage(key)

        assertNotNull("应能获取缓存的页面", cachedPage)
        assertEquals("页面内容应正确", 0, cachedPage?.startCharOffset)
    }

    @Test
    fun chapterCache_storesAndRetrievesValues() {
        val cacheManager = CacheManager(chapterCacheSize = 10)
        val key = CacheManager.ChapterCacheKey(
            bookId = "book1",
            chapterIndex = 0,
            textSize = 18f,
            lineHeight = 1.5f,
            pageWidth = 1080,
            pageHeight = 1920,
        )

        val chapter = TextChapter(
            chapterIndex = 0,
            title = "Test Chapter",
            content = "Test content",
            pages = emptyList(),
        )

        cacheManager.putChapter(key, chapter)
        val cachedChapter = cacheManager.getChapter(key)

        assertNotNull("应能获取缓存的章节", cachedChapter)
        assertEquals("章节标题应正确", "Test Chapter", cachedChapter?.title)
    }

    @Test
    fun cache_supportsLruEviction() {
        val cacheManager = CacheManager(pageCacheSize = 2)

        val key1 = CacheManager.PageCacheKey("book1", 0, 0, 18f, 1.5f, 1080, 1920)
        val key2 = CacheManager.PageCacheKey("book1", 0, 1, 18f, 1.5f, 1080, 1920)
        val key3 = CacheManager.PageCacheKey("book1", 0, 2, 18f, 1.5f, 1080, 1920)

        val page1 = TextPage(0, 100, 0, 0, PageSize(1080, 1920), 24f, emptyList(), emptyList())
        val page2 = TextPage(100, 200, 0, 1, PageSize(1080, 1920), 24f, emptyList(), emptyList())
        val page3 = TextPage(200, 300, 0, 2, PageSize(1080, 1920), 24f, emptyList(), emptyList())

        cacheManager.putPage(key1, page1)
        cacheManager.putPage(key2, page2)
        cacheManager.putPage(key3, page3)

        // 最早的页面应该被驱逐
        assertNull("最早页面应被驱逐", cacheManager.getPage(key1))
        assertNotNull("第二页应存在", cacheManager.getPage(key2))
        assertNotNull("第三页应存在", cacheManager.getPage(key3))
    }

    @Test
    fun cache_supportsClear() {
        val cacheManager = CacheManager()
        val key = CacheManager.PageCacheKey("book1", 0, 0, 18f, 1.5f, 1080, 1920)
        val page = TextPage(0, 100, 0, 0, PageSize(1080, 1920), 24f, emptyList(), emptyList())

        cacheManager.putPage(key, page)
        cacheManager.clear()

        assertNull("清空后应无法获取页面", cacheManager.getPage(key))
    }

    @Test
    fun cacheStats_areCorrect() {
        val cacheManager = CacheManager(pageCacheSize = 10, chapterCacheSize = 5)
        val stats = cacheManager.stats()

        assertEquals("页面缓存大小应为0", 0, stats.pageCacheStats.size)
        assertEquals("页面缓存最大大小应为10", 10, stats.pageCacheStats.maxSize)
        assertEquals("章节缓存大小应为0", 0, stats.chapterCacheStats.size)
        assertEquals("章节缓存最大大小应为5", 5, stats.chapterCacheStats.maxSize)
    }

    @Test
    fun cacheLimits_convergeByMemoryClass() {
        assertEquals(CacheLimits(pageCacheSize = 20, chapterCacheSize = 4), CacheManager.limitsForMemoryClass(128))
        assertEquals(CacheLimits(pageCacheSize = 40, chapterCacheSize = 8), CacheManager.limitsForMemoryClass(256))
        assertEquals(CacheLimits(pageCacheSize = 80, chapterCacheSize = 16), CacheManager.limitsForMemoryClass(512))
    }

    @Test
    fun forMemoryClass_usesMatchingLimits() {
        val cacheManager = CacheManager.forMemoryClass(128)
        val stats = cacheManager.stats()

        assertEquals(20, stats.pageCacheStats.maxSize)
        assertEquals(4, stats.chapterCacheStats.maxSize)
    }
}
