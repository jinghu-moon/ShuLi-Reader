package com.shuli.reader.core.dictionary.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * MDD 资源加载器
 *
 * 管理 MDX 词典的 MDD 资源文件（CSS、图片、音频）
 */
class MddResourceLoader(
    private val mddParser: Any?, // MdictParser 实例
) {
    /** 图片缓存 */
    private val imageCache = ConcurrentHashMap<String, Bitmap>(32)

    /** CSS 缓存 */
    private var cssCache: String? = null

    /**
     * 加载 CSS 样式表
     *
     * 从 MDD 中查找 *.css 资源并读取内容
     */
    fun loadCss(): String? {
        cssCache?.let { return it }

        if (mddParser == null) return null

        return try {
            // 尝试加载常见的 CSS 文件路径
            val cssPaths = listOf(
                "\\style.css",
                "\\css\\style.css",
                "\\default.css",
            )

            for (path in cssPaths) {
                val bytes = readResource(path)
                if (bytes != null) {
                    val css = String(bytes, Charsets.UTF_8)
                    cssCache = css
                    return css
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 加载图片资源
     *
     * @param path 资源路径（如 \img\xxx.png）
     * @return Bitmap，加载失败返回 null
     */
    fun loadImage(path: String): Bitmap? {
        // 检查缓存
        imageCache[path]?.let { return it }

        if (mddParser == null) return null

        return try {
            val bytes = readResource(path) ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            // 缓存图片
            if (bitmap != null) {
                imageCache[path] = bitmap
            }

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 加载音频资源
     *
     * @param path 资源路径（如 \sound\xxx.mp3）
     * @return 音频数据，加载失败返回 null
     */
    fun loadAudio(path: String): ByteArray? {
        if (mddParser == null) return null

        return try {
            readResource(path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 MDD 读取资源
     */
    private fun readResource(path: String): ByteArray? {
        if (mddParser == null) return null

        return try {
            val method = mddParser.javaClass.getMethod("readResource", String::class.java)
            method.invoke(mddParser, path) as? ByteArray
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        imageCache.values.forEach { it.recycle() }
        imageCache.clear()
        cssCache = null
    }

    /**
     * 释放资源
     */
    fun release() {
        clearCache()
    }

    companion object {
        /**
         * 从 MDD 文件路径创建加载器
         */
        fun fromMddFile(mddPath: String): MddResourceLoader? {
            val mddFile = File(mddPath)
            if (!mddFile.exists()) return null

            return try {
                // 通过反射创建 MdictParser
                val mdictClass = Class.forName("com.shuli.reader.mdict.MdictParser")
                val openMethod = mdictClass.getMethod("open", File::class.java, File::class.java)
                val parser = openMethod.invoke(null, mddFile, null)
                MddResourceLoader(parser)
            } catch (e: Exception) {
                null
            }
        }
    }
}
