package com.shuli.reader.mdict.engine

import com.shuli.reader.mdict.CorruptDictException
import com.shuli.reader.mdict.UnsupportedDictException
import com.shuli.reader.mdict.io.BlockReader
import com.shuli.reader.mdict.model.MdxHeader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Header Section 解析。对应 docs/38 §5.1。
 *
 * 布局：`[length:4 大端][header_str: UTF-16LE XML][adler32:4]`，
 * keySectionStart = 4 + length + 4。
 */
object HeaderParser {

    private val ATTR_REGEX = Regex("""(\w+)="(.*?)"""", RegexOption.DOT_MATCHES_ALL)

    fun parse(reader: BlockReader): MdxHeader {
        // length 是大端 uint32
        val lenBytes = reader.read(0, 4)
        val headerLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.BIG_ENDIAN).int.toLong()
        if (headerLen <= 0 || headerLen + 8 > reader.size) {
            throw CorruptDictException("invalid header length=$headerLen")
        }
        val xmlBytes = reader.read(4, headerLen.toInt())
        val xml = String(xmlBytes, Charsets.UTF_16LE)
        val keySectionStart = 4L + headerLen + 4L // 跳过 header adler32

        val attrs = parseAttrs(xml)
        val isMdd = xml.contains("Library_Data")

        val version = (attrs["GeneratedByEngineVersion"] ?: "2.0").toFloatOrNull()
            ?: throw CorruptDictException("missing/invalid GeneratedByEngineVersion")
        val numberWidth = if (version >= 2.0f) 8 else 4

        val encrypted = parseEncrypted(attrs["Encrypted"])
        if ((encrypted and 1) != 0) {
            throw UnsupportedDictException(
                "record-level Salsa20 encryption (Encrypted&1) is not supported"
            )
        }

        val encodingName = attrs["Encoding"].orEmpty()
        val (charset, unitWidth) = resolveCharset(encodingName, isMdd)

        return MdxHeader(
            isMdd = isMdd,
            version = version,
            numberWidth = numberWidth,
            encodingName = encodingName,
            charset = charset,
            unitWidth = unitWidth,
            encrypted = encrypted,
            format = attrs["Format"].orEmpty(),
            styleSheet = parseStyleSheet(attrs["StyleSheet"]),
            keySectionStart = keySectionStart,
        )
    }

    private fun parseAttrs(xml: String): Map<String, String> =
        ATTR_REGEX.findAll(xml).associate { it.groupValues[1] to unescape(it.groupValues[2]) }

    private fun unescape(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&amp;", "&")

    /** No/空→0；Yes→1；否则按整数解析。 */
    private fun parseEncrypted(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        return when (raw.trim().lowercase()) {
            "no" -> 0
            "yes" -> 1
            else -> raw.trim().toIntOrNull()
                ?: throw CorruptDictException("invalid Encrypted=$raw")
        }
    }

    /**
     * 编码归一化（docs/38 §3）：
     * MDD 强制 UTF-16LE；GBK/GB2312→GB18030；UTF-16→UTF-16LE；空→UTF-8。
     * 返回 (charset, unitWidth)，unitWidth 为编码单元字节数（UTF-16=2，其余=1）。
     */
    private fun resolveCharset(encoding: String, isMdd: Boolean): Pair<Charset, Int> {
        if (isMdd) return Charsets.UTF_16LE to 2
        return when (encoding.uppercase().replace("-", "")) {
            "", "UTF8" -> Charsets.UTF_8 to 1
            "UTF16" -> Charsets.UTF_16LE to 2
            "GBK", "GB2312" -> Charset.forName("GB18030") to 1
            "BIG5" -> Charset.forName("Big5") to 1
            else -> Charset.forName(encoding) to 1
        }
    }

    /** StyleSheet：每 3 行一组 → 编号: (前缀, 后缀)。空则返回空表。 */
    private fun parseStyleSheet(raw: String?): Map<Int, Pair<String, String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val lines = raw.split("\r\n", "\n", "\r")
        val map = LinkedHashMap<Int, Pair<String, String>>()
        var i = 0
        while (i + 2 < lines.size) {
            val num = lines[i].trim().toIntOrNull() ?: break
            map[num] = lines[i + 1] to lines[i + 2]
            i += 3
        }
        return map
    }
}
