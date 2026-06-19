package com.shuli.reader.core.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import com.shuli.reader.core.i18n.AppStrings
import java.io.File

/**
 * 字体管理器 — 导入/列表/删除自定义字体
 *
 * 存储路径: context.filesDir/resources/fonts/
 * 字体 key 格式: "custom:{id}"，id = 文件名(不含扩展名)
 */
class FontManager(val context: Context, val strings: AppStrings = AppStrings.ZhHans) {

    /** 内置字体 key */
    companion object {
        const val KEY_SYSTEM = "system"
        const val KEY_HARMONY = "harmony"
        private const val CUSTOM_PREFIX = "custom:"
        private const val FONT_DIR_NAME = "resources/fonts"

        fun isCustomFont(key: String): Boolean = key.startsWith(CUSTOM_PREFIX)

        fun customFontId(key: String): String? {
            if (!isCustomFont(key)) return null
            return key.removePrefix(CUSTOM_PREFIX)
        }

        fun customFontKey(id: String): String = "$CUSTOM_PREFIX$id"
    }

    private val fontDir: File
        get() = File(context.filesDir, FONT_DIR_NAME).also { it.mkdirs() }

    /** Typeface 缓存，避免重复解析字体文件 */
    private val typefaceCache = HashMap<String, Typeface>()

    /** 字体条目 */
    data class FontEntry(
        val id: String,
        val name: String,
        val file: File,
    ) {
        val key: String get() = customFontKey(id)
    }

    /** 列出所有已导入的字体 */
    fun listFonts(locale: java.util.Locale? = null): List<FontEntry> {
        val dir = fontDir
        if (!dir.exists()) {
            android.util.Log.d(TAG, "listFonts: 目录不存在: ${dir.absolutePath}")
            return emptyList()
        }
        val allFiles = dir.listFiles()
        android.util.Log.d(TAG, "listFonts: 目录=${dir.absolutePath}, 文件总数=${allFiles?.size ?: 0}")
        allFiles?.forEach { f ->
            android.util.Log.d(TAG, "  文件: ${f.name}, isFile=${f.isFile}, ext='${f.extension}', 大小=${f.length()}")
        }
        return allFiles
            ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            ?.sortedBy { it.name }
            ?.map { file ->
                val id = file.nameWithoutExtension
                val displayName = TtfNameReader.readDisplayName(file, locale) ?: id
                FontEntry(id = id, name = displayName, file = file)
            }
            ?: emptyList()
    }

    /** 导入字体文件，返回 FontEntry；失败抛异常 */
    fun importFont(uri: Uri, displayName: String? = null, locale: java.util.Locale? = null): FontEntry {
        val queriedName = queryDisplayName(uri)
        val fileName = displayName
            ?: queriedName
            ?: uri.lastPathSegment
            ?: "font_${System.currentTimeMillis()}"
        val cleanName = sanitizeFileName(fileName)

        // 确保目录存在
        val dir = fontDir
        if (!dir.exists()) {
            val created = dir.mkdirs()
            android.util.Log.d(TAG, "importFont: 创建目录 ${dir.absolutePath}, 结果=$created")
        }

        val dest = File(dir, cleanName)

        android.util.Log.d(TAG, "importFont: uri=$uri")
        android.util.Log.d(TAG, "importFont: displayName=$displayName, queriedName=$queriedName, lastPathSegment=${uri.lastPathSegment}")
        android.util.Log.d(TAG, "importFont: cleanName=$cleanName, dest=${dest.absolutePath}")

        // 同名覆盖
        if (dest.exists()) dest.delete()

        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "importFont: openInputStream 异常", e)
            null
        }
        if (inputStream == null) {
            android.util.Log.e(TAG, "importFont: openInputStream 返回 null，无法读取 URI")
            throw IllegalArgumentException(strings.sync.cannotReadFontFile(uri.toString()))
        }

        inputStream.use { input ->
            dest.outputStream().use { output ->
                val bytes = input.copyTo(output)
                android.util.Log.d(TAG, "importFont: 写入完成, 字节数=$bytes, dest.exists=${dest.exists()}, dest.length=${dest.length()}")
            }
        }

        if (!dest.exists() || dest.length() == 0L) {
            throw IllegalArgumentException(strings.sync.fontWriteFailedOrEmpty(dest.absolutePath))
        }

        val id = dest.nameWithoutExtension
        val fontName = TtfNameReader.readDisplayName(dest, locale) ?: id
        android.util.Log.d(TAG, "importFont: 成功, id=$id, fontName=$fontName, 文件名=${dest.name}")
        return FontEntry(id = id, name = fontName, file = dest)
    }

    /** 删除指定字体 */
    fun deleteFont(id: Int): Boolean {
        val entry = listFonts().find { it.id == id.toString() } ?: return false
        typefaceCache.remove(entry.id)
        return entry.file.delete()
    }

    fun deleteFontById(id: String): Boolean {
        val entry = listFonts().find { it.id == id } ?: return false
        typefaceCache.remove(id)
        return entry.file.delete()
    }

    /** 根据 key 加载 Typeface；内置字体返回 null 由调用方处理 */
    fun loadTypeface(fontKey: String): Typeface? {
        if (!isCustomFont(fontKey)) return null
        val id = customFontId(fontKey) ?: return null
        typefaceCache[id]?.let { return it }
        val typeface = loadTypefaceFromFile(id) ?: return null
        typefaceCache[id] = typeface
        return typeface
    }

    private fun loadTypefaceFromFile(id: String): Typeface? {
        val file = File(fontDir, "$id.${FONT_EXTENSION_TTF}")
        if (file.exists()) return Typeface.createFromFile(file)
        val fileOtf = File(fontDir, "$id.${FONT_EXTENSION_OTF}")
        if (fileOtf.exists()) return Typeface.createFromFile(fileOtf)
        return null
    }

    /** 通过 ContentResolver 查询 content URI 的真实文件名（含扩展名） */
    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        // 保留扩展名，清理非法字符
        val dotIndex = name.lastIndexOf('.')
        val base = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext = if (dotIndex > 0) name.substring(dotIndex + 1) else FONT_EXTENSION_TTF
        val cleanBase = base.replace(Regex("[^a-zA-Z0-9_\\-\\u4e00-\\u9fff]"), "_")
        return "$cleanBase.$ext"
    }
}

private val SUPPORTED_EXTENSIONS = setOf("ttf", "otf")
private const val FONT_EXTENSION_TTF = "ttf"
private const val FONT_EXTENSION_OTF = "otf"
private const val TAG = "FontManager"
