package com.shuli.reader.core.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/**
 * TtfNameReader 单元测试：使用合成的最小 TTF 二进制验证 name 表解析。
 */
class TtfNameReaderTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun prefersFullNameOverFamily() {
        val file = writeSyntheticTtf(
            platform = 3, encoding = 1, language = 0x0409,
            names = mapOf(
                1 to "My Font Family",
                4 to "My Font Regular",
                6 to "MyFont-Regular",
            )
        )
        // Full name differs from Family and no Subfamily → use Full.
        assertEquals("My Font Regular", TtfNameReader.readDisplayName(file))
    }

    @Test
    fun combinesFamilyWithNonRegularSubfamily() {
        // 模拟 鸿蒙黑体.ttf：Family="鸿蒙" + Subfamily="黑体" → "鸿蒙黑体"
        val file = writeSyntheticTtf(
            platform = 3, encoding = 1, language = 0x0409,
            names = mapOf(
                1 to "鸿蒙",
                2 to "黑体",
                4 to "鸿蒙",
            )
        )
        assertEquals("鸿蒙黑体", TtfNameReader.readDisplayName(file))
    }

    @Test
    fun skipsRegularSubfamily() {
        val file = writeSyntheticTtf(
            platform = 3, encoding = 1, language = 0x0409,
            names = mapOf(
                1 to "My Font",
                2 to "Regular",
                4 to "My Font",
            )
        )
        // Subfamily=Regular → 不拼接，回退到 Family
        assertEquals("My Font", TtfNameReader.readDisplayName(file))
    }

    @Test
    fun prefersFullOverFamilyWhenNoSubfamily() {
        val file = writeSyntheticTtf(
            platform = 3, encoding = 1, language = 0x0409,
            names = mapOf(
                1 to "Font Family",
                4 to "Font Full Name",
                6 to "FontPostScript",
            )
        )
        assertEquals("Font Full Name", TtfNameReader.readDisplayName(file))
    }

    @Test
    fun fallsBackToFamilyWhenFullMissing() {
        val file = writeSyntheticTtf(
            platform = 3, encoding = 1, language = 0x0409,
            names = mapOf(
                1 to "Font Family",
                6 to "FontPostScript",
            )
        )
        assertEquals("Font Family", TtfNameReader.readDisplayName(file))
    }

    @Test
    fun readsUnicodeUtf16Name() {
        val file = writeSyntheticTtf(
            platform = 0, encoding = 3, language = 0,
            names = mapOf(1 to "思源宋体")
        )
        assertEquals("思源宋体", TtfNameReader.readDisplayName(file))
    }

    @Test
    fun zhCnLocalePrefersChineseEntry() {
        // 双语 name 表：先英文，后中文。zh_CN 应优先选择中文条目。
        val file = writeMultiLanguageTtf(
            nameId = 1,
            entries = listOf(
                Entry(pid = 3, eid = 1, lid = 0x0409, text = "HarmonyOS Sans SC"),
                Entry(pid = 3, eid = 1, lid = 0x0804, text = "鸿蒙黑体"),
            )
        )
        assertEquals(
            "鸿蒙黑体",
            TtfNameReader.readDisplayName(file, java.util.Locale.SIMPLIFIED_CHINESE)
        )
    }

    @Test
    fun enLocalePrefersEnglishEntry() {
        val file = writeMultiLanguageTtf(
            nameId = 1,
            entries = listOf(
                Entry(pid = 3, eid = 1, lid = 0x0804, text = "鸿蒙黑体"),
                Entry(pid = 3, eid = 1, lid = 0x0409, text = "HarmonyOS Sans SC"),
            )
        )
        assertEquals(
            "HarmonyOS Sans SC",
            TtfNameReader.readDisplayName(file, java.util.Locale.ENGLISH)
        )
    }

    @Test
    fun zhTwLocalePrefersTraditionalChineseEntry() {
        val file = writeMultiLanguageTtf(
            nameId = 1,
            entries = listOf(
                Entry(pid = 3, eid = 1, lid = 0x0804, text = "鸿蒙"),          // 简体
                Entry(pid = 3, eid = 1, lid = 0x0404, text = "鴻蒙"),          // 繁体
                Entry(pid = 3, eid = 1, lid = 0x0409, text = "HarmonyOS"),     // 英文
            )
        )
        assertEquals(
            "鴻蒙",
            TtfNameReader.readDisplayName(file, java.util.Locale.TRADITIONAL_CHINESE)
        )
    }

    @Test
    fun returnsNullForNonFontFile() {
        val file = tempDir.newFile("not_a_font.ttf").apply {
            writeText("this is not a real font")
        }
        assertNull(TtfNameReader.readDisplayName(file))
    }

    @Test
    fun returnsNullForMissingFile() {
        val file = File(tempDir.root, "does_not_exist.ttf")
        assertNull(TtfNameReader.readDisplayName(file))
    }

    @Test
    fun returnsNullForEmptyFile() {
        val file = tempDir.newFile("empty.ttf")
        assertNull(TtfNameReader.readDisplayName(file))
    }

    // ─── 合成 TTF 工具 ──────────────────────────────────────────────

    private data class Entry(val pid: Int, val eid: Int, val lid: Int, val text: String)

    private fun writeMultiLanguageTtf(nameId: Int, entries: List<Entry>): File {
        val bos = ByteArrayOutputStream()
        val out = DataOutputStream(bos)

        val encoded = entries.map { e ->
            val charset = if ((e.pid == 3 && (e.eid == 1 || e.eid == 10)) || e.pid == 0)
                Charsets.UTF_16BE
            else Charsets.US_ASCII
            e to e.text.toByteArray(charset)
        }
        val stringBytes = run {
            val sb = ByteArrayOutputStream()
            for ((_, bytes) in encoded) sb.write(bytes)
            sb.toByteArray()
        }

        val numTables = 1
        val nameTableOffset = 12 + 16 * numTables
        val nameRecordSize = 12 * entries.size
        val stringStorageRel = 6 + nameRecordSize

        // Header
        out.writeInt(0x00010000)
        out.writeShort(numTables)
        out.writeShort(16)
        out.writeShort(0)
        out.writeShort(0)
        // Directory
        out.write("name".toByteArray(Charsets.US_ASCII))
        out.writeInt(0)
        out.writeInt(nameTableOffset)
        out.writeInt(6 + nameRecordSize + stringBytes.size)
        // Name header
        out.writeShort(0)
        out.writeShort(entries.size)
        out.writeShort(stringStorageRel)
        // Records
        var cursor = 0
        for ((e, bytes) in encoded) {
            out.writeShort(e.pid)
            out.writeShort(e.eid)
            out.writeShort(e.lid)
            out.writeShort(nameId)
            out.writeShort(bytes.size)
            out.writeShort(cursor)
            cursor += bytes.size
        }
        // String storage
        out.write(stringBytes)
        out.flush()

        val file = tempDir.newFile("multilang_${System.nanoTime()}.ttf")
        file.writeBytes(bos.toByteArray())
        return file
    }

    private fun writeSyntheticTtf(
        platform: Int,
        encoding: Int,
        language: Int,
        names: Map<Int, String>,
    ): File {
        val bytes = buildSyntheticTtf(platform, encoding, language, names)
        val file = tempDir.newFile("synthetic_${System.nanoTime()}.ttf")
        file.writeBytes(bytes)
        return file
    }

    private fun buildSyntheticTtf(
        platform: Int,
        encoding: Int,
        language: Int,
        names: Map<Int, String>,
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        val out = DataOutputStream(bos)

        // 编码 name 字符串
        val encodedNames = names.mapValues { (_, v) ->
            v.toByteArray(Charsets.UTF_16BE)
        }
        val nameIds = names.keys.sorted()
        val stringBytes = run {
            val sb = ByteArrayOutputStream()
            for (id in nameIds) {
                sb.write(encodedNames[id] ?: ByteArray(0))
            }
            sb.toByteArray()
        }

        // 布局
        val numTables = 1
        val tableDirSize = 16 * numTables
        val nameTableOffset = 12 + tableDirSize
        val nameRecordCount = nameIds.size
        val nameHeaderSize = 6
        val nameRecordSize = 12 * nameRecordCount
        val stringStorageRel = nameHeaderSize + nameRecordSize

        // 1. sfnt header (12 字节)
        out.writeInt(0x00010000)        // sfntVersion
        out.writeShort(numTables)
        out.writeShort(16)              // searchRange
        out.writeShort(0)               // entrySelector
        out.writeShort(0)               // rangeShift

        // 2. table directory: "name" 记录
        out.write("name".toByteArray(Charsets.US_ASCII))
        out.writeInt(0)                 // checksum
        out.writeInt(nameTableOffset)
        out.writeInt(nameHeaderSize + nameRecordSize + stringBytes.size)

        // 3. name 表头
        out.writeShort(0)               // format
        out.writeShort(nameRecordCount)
        out.writeShort(stringStorageRel)

        // 4. name records — 按 nameIds 顺序，与 stringBytes 顺序一致
        var cursor = 0
        for (id in nameIds) {
            val bytes = encodedNames[id] ?: ByteArray(0)
            out.writeShort(platform)
            out.writeShort(encoding)
            out.writeShort(language)
            out.writeShort(id)
            out.writeShort(bytes.size)
            out.writeShort(cursor)
            cursor += bytes.size
        }

        // 5. 字符串存储
        out.write(stringBytes)

        out.flush()
        return bos.toByteArray()
    }
}
