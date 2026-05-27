package com.shuli.reader.core.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

/**
 * 字体管理器 — 导入/列表/删除自定义字体
 *
 * 存储路径: context.filesDir/resources/fonts/
 * 字体 key 格式: "custom:{id}"，id = 文件名(不含扩展名)
 */
class FontManager(private val context: Context) {

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

    /** 字体条目 */
    data class FontEntry(
        val id: String,
        val name: String,
        val file: File,
    ) {
        val key: String get() = customFontKey(id)
    }

    /** 列出所有已导入的字体 */
    fun listFonts(): List<FontEntry> {
        val dir = fontDir
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            ?.sortedBy { it.name }
            ?.map { file ->
                val id = file.nameWithoutExtension
                FontEntry(id = id, name = id, file = file)
            }
            ?: emptyList()
    }

    /** 导入字体文件，返回 FontEntry；失败抛异常 */
    fun importFont(uri: Uri, displayName: String? = null): FontEntry {
        val fileName = displayName
            ?: uri.lastPathSegment
            ?: "font_${System.currentTimeMillis()}"
        val cleanName = sanitizeFileName(fileName)
        val dest = File(fontDir, cleanName)

        // 同名覆盖
        if (dest.exists()) dest.delete()

        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("无法读取字体文件")

        val id = dest.nameWithoutExtension
        return FontEntry(id = id, name = id, file = dest)
    }

    /** 删除指定字体 */
    fun deleteFont(id: Int): Boolean {
        val entry = listFonts().find { it.id == id.toString() } ?: return false
        return entry.file.delete()
    }

    fun deleteFontById(id: String): Boolean {
        val entry = listFonts().find { it.id == id } ?: return false
        return entry.file.delete()
    }

    /** 根据 key 加载 Typeface；内置字体返回 null 由调用方处理 */
    fun loadTypeface(fontKey: String): Typeface? {
        if (!isCustomFont(fontKey)) return null
        val id = customFontId(fontKey) ?: return null
        val file = File(fontDir, "$id.${FONT_EXTENSION_TTF}")
        if (file.exists()) return Typeface.createFromFile(file)
        val fileOtf = File(fontDir, "$id.${FONT_EXTENSION_OTF}")
        if (fileOtf.exists()) return Typeface.createFromFile(fileOtf)
        return null
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
