package com.shuli.reader.core.reader.engine.selection

import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.MainThread
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextPage

/**
 * 选区几何缓存 Key
 */
data class SelectionGeometryCacheKey(
    val chapterIndex: Int,
    val pageIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val rangeStart: Int,
    val rangeEnd: Int,
    val layoutVersion: Int,
    val styleVersion: Int
) {
    companion object {
        fun create(
            page: TextPage,
            range: SelectionRange,
            layoutVersion: Int,
            styleVersion: Int
        ): SelectionGeometryCacheKey {
            return SelectionGeometryCacheKey(
                chapterIndex = page.chapterIndex,
                pageIndex = page.pageIndex,
                startCharOffset = page.startCharOffset,
                endCharOffset = page.endCharOffset,
                rangeStart = range.startPos,
                rangeEnd = range.endPos,
                layoutVersion = layoutVersion,
                styleVersion = styleVersion
            )
        }
    }
}

/**
 * 缓存的几何数据
 */
class SelectionGeometry {
    val highlightRects = ArrayList<RectF>(50)
    val unifiedPath = Path()
    var isUnifiedPathValid = false
    var fallbackToRects = false
}

/**
 * 选区几何缓存，用于降低分配和 Path 运算成本。
 * 限制 10 个页面内的选区状态。
 */
@MainThread
class SelectionGeometryCache {
    private val cache = object : LinkedHashMap<SelectionGeometryCacheKey, SelectionGeometry>(10, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SelectionGeometryCacheKey, SelectionGeometry>): Boolean {
            return size > 10
        }
    }
    
    fun get(key: SelectionGeometryCacheKey): SelectionGeometry? {
        return cache[key]
    }
    
    fun put(key: SelectionGeometryCacheKey, geometry: SelectionGeometry) {
        cache[key] = geometry
    }
    
    fun clear() {
        cache.clear()
    }
    
    /** 页面回收时清理对应的缓存 */
    fun onPageRecycled(chapterIndex: Int, pageIndex: Int) {
        val keysToRemove = cache.keys.filter { it.chapterIndex == chapterIndex && it.pageIndex == pageIndex }
        for (k in keysToRemove) {
            cache.remove(k)
        }
    }

    /** 页面回收时清理对应的缓存。仅保留兼容旧调用；跨章节场景优先使用带 chapterIndex 的重载。 */
    fun onPageRecycled(pageIndex: Int) {
        val keysToRemove = cache.keys.filter { it.pageIndex == pageIndex }
        for (k in keysToRemove) {
            cache.remove(k)
        }
    }
}
