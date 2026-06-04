package com.shuli.reader.core.reader.cache

import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextPage

/**
 * 缓存管理器，管理页面、章节和位图缓存
 */
class CacheManager(
    pageCacheSize: Int = 50,
    chapterCacheSize: Int = 10,
) {
    companion object {
        fun limitsForMemoryClass(memoryClassMb: Int): CacheLimits {
            return when {
                memoryClassMb < 256 -> CacheLimits(pageCacheSize = 2 * 1024 * 1024, chapterCacheSize = 5 * 1024 * 1024)
                memoryClassMb < 512 -> CacheLimits(pageCacheSize = 4 * 1024 * 1024, chapterCacheSize = 10 * 1024 * 1024)
                else -> CacheLimits(pageCacheSize = 8 * 1024 * 1024, chapterCacheSize = 20 * 1024 * 1024)
            }
        }

        fun forMemoryClass(memoryClassMb: Int): CacheManager {
            val limits = limitsForMemoryClass(memoryClassMb)
            return CacheManager(
                pageCacheSize = limits.pageCacheSize,
                chapterCacheSize = limits.chapterCacheSize,
            )
        }
    }

    /**
     * 页面缓存 key
     */
    data class PageCacheKey(
        val bookId: String,
        val chapterIndex: Int,
        val pageIndex: Int,
        val textSize: Float,
        val lineHeight: Float,
        val pageWidth: Int,
        val pageHeight: Int,
    )

    /**
     * 章节缓存 key
     */
    data class ChapterCacheKey(
        val bookId: String,
        val chapterIndex: Int,
        val textSize: Float,
        val lineHeight: Float,
        val pageWidth: Int,
        val pageHeight: Int,
        val letterSpacingPx: Float = 0f,
        val marginHorizontal: Float = 0f,
        val marginVertical: Float = 0f,
        val indent: Float = 2f,
        val showHeader: Boolean = true,
        val showFooter: Boolean = true,
        val chineseConvert: Int = 0,  // ChineseConvert ordinal
        val usePanguSpacing: Boolean = false,
        // 正文标题样式（影响首页 titleAreaHeight 进而改变首页字符数）
        val titleAlignOrdinal: Int = 1,  // TitleAlign ordinal（CENTER 默认 1）
        val titleSizeOffsetSp: Int = 4,
        val titleMarginTopDp: Float = 9f,
        val titleMarginBottomDp: Float = 60f,
    )

    // 页面缓存 (预估每页包含若干 TextLine 和 CanvasRecorder 句柄，固定按 10KB 估算)
    private val pageCache = LruCache<PageCacheKey, TextPage>(pageCacheSize) { _, _ ->
        10 * 1024 // 10KB
    }

    // 章节缓存 (基于字符串 content 长度估算字节：每个 Char 占 2 字节)
    private val chapterCache = LruCache<ChapterCacheKey, TextChapter>(chapterCacheSize) { _, chapter ->
        chapter.content.length * 2 + 1024 // 额外 1KB 用于框架对象
    }

    /**
     * 获取页面
     */
    fun getPage(key: PageCacheKey): TextPage? {
        return pageCache.get(key)
    }

    /**
     * 存入页面
     */
    fun putPage(key: PageCacheKey, page: TextPage) {
        pageCache.put(key, page)
    }

    /**
     * 获取章节
     */
    fun getChapter(key: ChapterCacheKey): TextChapter? {
        return chapterCache.get(key)
    }

    /**
     * 存入章节
     */
    fun putChapter(key: ChapterCacheKey, chapter: TextChapter) {
        chapterCache.put(key, chapter)
    }

    /**
     * 清空所有缓存
     */
    fun clear() {
        pageCache.clear()
        chapterCache.clear()
    }

    /**
     * 清空指定书籍的缓存
     */
    fun clearBook(bookId: String) {
        pageCache.clear()
        chapterCache.clear()
    }

    /**
     * 响应内存紧张
     */
    fun onTrimMemory(level: Int) {
        when {
            // TRIM_MEMORY_COMPLETE 或更高：完全释放
            level >= 80 -> clear()
            // TRIM_MEMORY_MODERATE：释放一半（直接清空，由后续访问按需重建）
            level >= 60 -> {
                pageCache.clear()
                chapterCache.clear()
            }
            // TRIM_MEMORY_BACKGROUND：保守释放页面缓存
            level >= 40 -> {
                pageCache.clear()
            }
        }
    }

    /**
     * 获取缓存统计信息
     */
    fun stats(): CacheManagerStats {
        return CacheManagerStats(
            pageCacheStats = pageCache.stats(),
            chapterCacheStats = chapterCache.stats(),
        )
    }
}

/**
 * 缓存管理器统计信息
 */
data class CacheManagerStats(
    val pageCacheStats: CacheStats,
    val chapterCacheStats: CacheStats,
)

data class CacheLimits(
    val pageCacheSize: Int,
    val chapterCacheSize: Int,
)
