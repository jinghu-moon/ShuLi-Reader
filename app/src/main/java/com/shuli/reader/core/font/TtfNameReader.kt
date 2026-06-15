package com.shuli.reader.core.font

import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

/**
 * 轻量 OpenType/TrueType `name` 表解析器。
 *
 * 从字体文件的 name 表读取字体元数据，用于获取真实字体名（而非文件名）。
 * 支持按系统 locale 优先选择本地化条目（如中文系统显示 "鸿蒙黑体" 而非 "HarmonyOS Sans SC"）。
 * 参考：https://learn.microsoft.com/zh-cn/typography/opentype/spec/name
 */
internal object TtfNameReader {

    /** name 表中的名称 ID */
    private const val NAME_FAMILY = 1      // Font Family name
    private const val NAME_SUBFAMILY = 2   // Font Subfamily (字重后缀，如 Regular/黑体)
    private const val NAME_FULL = 4        // Full font name
    private const val NAME_POSTSCRIPT = 6  // PostScript name

    private val REGULAR_TOKENS = setOf("regular", "normal", "standard", "常规", "标准")

    private data class NameEntry(val platformId: Int, val languageId: Int, val value: String)

    /**
     * 从字体文件读取显示名称。
     *
     * 策略：
     * 1. 按 [preferredLocale]（优先）或系统 Locale 选择本地化条目（如 zh → 0x0804 等中文 LCID）
     * 2. 优先使用 Family + Subfamily 拼接（非 Regular 时）以匹配 OS 字体预览（"鸿蒙"+"黑体"="鸿蒙黑体"）
     * 3. 回退链：Full > Family > PostScript
     *
     * @param preferredLocale 应用内语言设置对应的 Locale；为 null 时回退到 [Locale.getDefault]。
     * 返回 null 表示无法解析，调用方应回退到文件名。
     */
    fun readDisplayName(file: File, preferredLocale: Locale? = null): String? {
        if (!file.isFile || !file.canRead() || file.length() < 12) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.readInt() // sfntVersion
                val tableCount = (raf.readShort().toInt() and 0xFFFF)
                // 跳过 searchRange / entrySelector / rangeShift 三个 short
                repeat(3) { raf.readShort() }

                var nameTableOffset = -1L
                for (i in 0 until tableCount) {
                    val tag = readTag(raf)
                    raf.readInt() // checksum
                    val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                    raf.readInt() // length
                    if (tag == "name") {
                        nameTableOffset = offset
                        break
                    }
                }
                if (nameTableOffset < 0) return null

                raf.seek(nameTableOffset)
                raf.readShort() // format
                val nameCount = (raf.readShort().toInt() and 0xFFFF)
                val stringOffset = (raf.readShort().toInt() and 0xFFFF)
                val storageStart = nameTableOffset + stringOffset

                val family = mutableListOf<NameEntry>()
                val subfamily = mutableListOf<NameEntry>()
                val full = mutableListOf<NameEntry>()
                val postscript = mutableListOf<NameEntry>()
                val recordsStart = nameTableOffset + 6 /* format/count/stringOffset header */
                for (i in 0 until nameCount) {
                    raf.seek(recordsStart + i.toLong() * 12)
                    val platformId = (raf.readShort().toInt() and 0xFFFF)
                    val encodingId = (raf.readShort().toInt() and 0xFFFF)
                    val languageId = (raf.readShort().toInt() and 0xFFFF)
                    val nameId = (raf.readShort().toInt() and 0xFFFF)
                    val length = (raf.readShort().toInt() and 0xFFFF)
                    val strOffset = (raf.readShort().toInt() and 0xFFFF)

                    if (nameId != NAME_FAMILY && nameId != NAME_SUBFAMILY &&
                        nameId != NAME_FULL && nameId != NAME_POSTSCRIPT) continue
                    if (length <= 0) continue

                    val strPos = storageStart + strOffset
                    if (strPos + length > raf.length()) continue

                    val bytes = ByteArray(length)
                    raf.seek(strPos)
                    raf.readFully(bytes)

                    val decoded = decode(bytes, platformId, encodingId) ?: continue
                    if (decoded.isBlank()) continue
                    val entry = NameEntry(platformId, languageId, decoded)
                    when (nameId) {
                        NAME_FAMILY -> family += entry
                        NAME_SUBFAMILY -> subfamily += entry
                        NAME_FULL -> full += entry
                        NAME_POSTSCRIPT -> postscript += entry
                    }
                }

                val familyName = pickLocalized(family, preferredLocale)
                val subfamilyName = pickLocalized(subfamily, preferredLocale)
                val fullName = pickLocalized(full, preferredLocale)

                // Family + Subfamily 拼接（非 Regular 时）→ 匹配 OS 预览
                if (familyName != null && subfamilyName != null && !isRegular(subfamilyName)) {
                    val combined = familyName + subfamilyName
                    if (fullName == null || fullName == familyName) return combined
                    if (fullName != combined) return combined
                }

                // 回退链：Full > Family > PostScript
                fullName ?: familyName ?: pickLocalized(postscript, preferredLocale)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从多条同 nameId 的条目中按给定 locale 选择最佳文本。
     * 优先级：精确 locale 匹配 > 同语言 > 中性 (languageId=0) > 首个。
     */
    private fun pickLocalized(entries: List<NameEntry>, preferredLocale: Locale?): String? {
        if (entries.isEmpty()) return null
        if (entries.size == 1) return entries[0].value
        val locale = preferredLocale ?: Locale.getDefault()
        val lang = locale.language
        val country = locale.country
        val preferredLcids = lcidsFor(lang, country)
        // 1. 精确匹配首选 LCID
        preferredLcids.forEach { lcid ->
            entries.firstOrNull { it.languageId == lcid }?.let { return it.value }
        }
        // 2. Mac 平台同语言（languageId 为 Mac 语言代码，简单以 0/1 兜底）
        if (lang == "zh") {
            entries.firstOrNull { it.platformId == 1 && it.languageId in 33..35 }
                ?.let { return it.value }
        } else if (lang == "en") {
            entries.firstOrNull { it.platformId == 1 && it.languageId == 0 }
                ?.let { return it.value }
        }
        // 3. 中性条目 (languageId=0，常见于 Mac 平台)
        entries.firstOrNull { it.languageId == 0 }?.let { return it.value }
        // 4. 任意首个
        return entries[0].value
    }

    /** 返回给定语言/地区的 Windows LCID 偏好列表（高优先在前） */
    private fun lcidsFor(lang: String, country: String): List<Int> = when (lang) {
        "zh" -> when (country) {
            "TW" -> listOf(0x0404, 0x0C04, 0x7C04, 0x0804, 0x1004)  // 繁体优先
            "HK", "MO" -> listOf(0x0C04, 0x0404, 0x7C04, 0x0804, 0x1004)
            else -> listOf(0x0804, 0x1004, 0x7C04, 0x0404, 0x0C04)   // 简体优先
        }
        "ja" -> listOf(0x0411)
        "ko" -> listOf(0x0412, 0x0812)
        "en" -> listOf(0x0409, 0x0809, 0x0C09, 0x1009, 0x1409)
        "ru" -> listOf(0x0419)
        "fr" -> listOf(0x040C, 0x080C, 0x0C0C, 0x100C, 0x140C)
        "de" -> listOf(0x0407, 0x0807, 0x0C07, 0x1007, 0x1407)
        "es" -> listOf(0x040A, 0x080A, 0x0C0A, 0x100A)
        else -> emptyList()
    }

    private fun isRegular(s: String): Boolean {
        val t = s.trim().lowercase()
        return t.isEmpty() || t in REGULAR_TOKENS
    }

    private fun readTag(raf: RandomAccessFile): String {
        val buf = ByteArray(4)
        raf.readFully(buf)
        return String(buf, Charsets.US_ASCII)
    }

    private fun decode(bytes: ByteArray, platformId: Int, encodingId: Int): String? {
        return when (platformId) {
            0 -> String(bytes, Charsets.UTF_16BE)                         // Unicode
            1 -> {                                                        // Macintosh
                if (encodingId == 0) String(bytes, Charsets.US_ASCII)
                else String(bytes, Charsets.UTF_8)
            }
            3 -> {                                                        // Windows
                if (encodingId == 1 || encodingId == 10) String(bytes, Charsets.UTF_16BE)
                else null
            }
            else -> null
        }
    }
}
