package com.shuli.reader.core.reader.engine.cache

import android.content.Context
import android.util.Log
import com.shuli.reader.core.reader.model.BoxInsetsPx
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import org.json.JSONObject
import java.io.File

/**
 * 持久化 chapterPageCounts 到文件系统。
 *
 * 存储路径：{cacheDir}/page_counts/{bookId}_{layoutHash}.json
 * layoutHash 由影响分页的排版参数拼接后取 MD5 前 8 字节（16 hex 字符）。
 * 每次保存后清理同 bookId 的旧 hash 文件，防止堆积。
 *
 * TODO: 当章节数 > 500 时，Map<Int, Int> 可考虑换为 List<Int>（-1 表示未分页），减半内存。
 */
object PageCountPersistence {

    private const val DIR_NAME = "page_counts"
    private const val TAG = "PageCountPersist"

    // ── Layout Hash ──────────────────────────────────────────────

    /**
     * 计算排版参数的哈希值，用于区分不同布局下的页数缓存。
     * 使用 [Float.toBits] 确保浮点精度一致，包含全部 4 个 BoxInsetsPx 的 16 个值。
     */
    fun computeLayoutHash(config: ReaderLayoutConfig): String {
        var result = config.pageSize.hashCode()
        result = 31 * result + config.textSize.toBits()
        result = 31 * result + config.lineHeight.toBits()
        result = 31 * result + config.paragraphSpacing.toBits()
        result = 31 * result + config.indent.toBits()
        result = 31 * result + config.density.toBits()
        result = 31 * result + config.letterSpacingPx.toBits()
        result = 31 * result + config.useZhLayout.hashCode()
        result = 31 * result + config.bottomJustify.hashCode()
        result = hashBoxInsets(result, config.headerInsets)
        result = hashBoxInsets(result, config.titleInsets)
        result = hashBoxInsets(result, config.bodyInsets)
        result = hashBoxInsets(result, config.footerInsets)
        return result.toString()
    }

    private fun hashBoxInsets(seed: Int, insets: BoxInsetsPx): Int {
        var result = seed
        result = 31 * result + insets.top.toBits()
        result = 31 * result + insets.bottom.toBits()
        result = 31 * result + insets.left.toBits()
        result = 31 * result + insets.right.toBits()
        return result
    }

    // ── Load / Save ──────────────────────────────────────────────

    /**
     * 加载已持久化的页数缓存。文件不存在或解析失败时返回空 Map。
     */
    fun load(context: Context, bookId: String, layoutHash: String): Map<Int, Int> {
        val file = getFile(context, bookId, layoutHash)
        if (!file.exists()) return emptyMap()
        return try {
            val json = JSONObject(file.readText())
            val result = mutableMapOf<Int, Int>()
            for (key in json.keys()) {
                result[key.toInt()] = json.getInt(key)
            }
            Log.d(TAG, "loaded ${result.size} entries for book=$bookId")
            result
        } catch (e: Exception) {
            Log.w(TAG, "failed to load: ${e.message}")
            emptyMap()
        }
    }

    /**
     * 将页数缓存持久化到文件，随后清理同 bookId 的旧 hash 文件。
     */
    fun save(context: Context, bookId: String, layoutHash: String, pageCounts: Map<Int, Int>) {
        if (pageCounts.isEmpty()) return
        val file = getFile(context, bookId, layoutHash)
        try {
            file.parentFile?.mkdirs()
            val json = JSONObject()
            for ((k, v) in pageCounts) {
                json.put(k.toString(), v)
            }
            file.writeText(json.toString())
            Log.d(TAG, "saved ${pageCounts.size} entries for book=$bookId")
        } catch (e: Exception) {
            Log.w(TAG, "failed to save: ${e.message}")
        }
        // 清理同 bookId 的旧 hash 文件，防止长期堆积
        cleanupOldFiles(context, bookId, layoutHash)
    }

    // ── Internal ─────────────────────────────────────────────────

    private fun getFile(context: Context, bookId: String, layoutHash: String): File {
        val dir = File(context.cacheDir, DIR_NAME)
        return File(dir, "${bookId}_$layoutHash.json")
    }

    private fun cleanupOldFiles(context: Context, bookId: String, currentHash: String) {
        val dir = File(context.cacheDir, DIR_NAME)
        if (!dir.isDirectory) return
        // 用正则精确匹配 bookId_hash.json，避免 bookId=1 匹配到 bookId=10/100
        val regex = Regex("^${Regex.escape(bookId)}_[0-9-]+\\.json$")
        val currentFileName = "${bookId}_$currentHash.json"
        dir.listFiles()?.forEach { f ->
            if (regex.matches(f.name) && f.name != currentFileName) {
                f.delete()
            }
        }
    }
}
