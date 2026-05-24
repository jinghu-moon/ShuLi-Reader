package com.shuli.reader.core.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.shuli.reader.core.reader.model.TextPage

/**
 * 页面缓冲器，负责预渲染页面到位图
 */
class PageBuffer(
    private val renderer: ReaderPageRenderer,
) {
    // 缓存的位图
    private val bitmapCache = LinkedHashMap<Int, Bitmap>(3, 0.75f, true)

    /**
     * 获取页面的预渲染位图
     */
    fun getPageBitmap(
        page: TextPage,
        width: Int,
        height: Int,
        headerText: String,
        footerText: String,
        showProgress: Boolean,
    ): Bitmap {
        val cacheKey = page.pageIndex

        // 检查缓存
        bitmapCache[cacheKey]?.let { cachedBitmap ->
            if (!cachedBitmap.isRecycled && cachedBitmap.width == width && cachedBitmap.height == height) {
                return cachedBitmap
            }
        }

        // 创建新位图
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 渲染页面
        renderer.render(canvas, page, headerText, footerText, showProgress)

        // 缓存位图
        bitmapCache[cacheKey] = bitmap

        // 裁剪缓存大小
        trimToSize()

        return bitmap
    }

    /**
     * 清空缓存
     */
    fun clear() {
        bitmapCache.values.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        bitmapCache.clear()
    }

    /**
     * 清空指定页面的缓存
     */
    fun clearPage(pageIndex: Int) {
        bitmapCache.remove(pageIndex)?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    /**
     * 裁剪到最大大小
     */
    private fun trimToSize() {
        while (bitmapCache.size > 3) {
            val eldest = bitmapCache.entries.iterator().next()
            bitmapCache.remove(eldest.key)?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }
}