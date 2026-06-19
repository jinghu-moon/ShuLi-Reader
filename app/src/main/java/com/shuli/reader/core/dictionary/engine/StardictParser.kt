package com.shuli.reader.core.dictionary.engine

import com.shuli.reader.core.dictionary.model.DefinitionType
import com.shuli.reader.core.dictionary.model.DictEntry
import com.shuli.reader.core.dictionary.model.DictFormat
import com.shuli.reader.core.dictionary.model.DictionaryMeta
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Stardict 词典解析器
 *
 * 支持 .ifo + .idx + .dict/.dict.dz 格式
 */
class StardictParser(
    private val ifoPath: String,
) : AutoCloseable {

    private var meta: DictionaryMeta? = null
    private var index: StardictIndex? = null
    private var dictFile: RandomAccessFile? = null
    private var dictZipReader: DictZipReader? = null
    private var charset: Charset = Charsets.UTF_8
    private var sameTypeSequence: String = ""

    /** 词典元数据 */
    val dictionaryMeta: DictionaryMeta?
        get() = meta

    /** 是否已加载 */
    val isLoaded: Boolean
        get() = meta != null && index != null

    /**
     * 加载词典索引
     *
     * 解析 .ifo 文件，加载 .idx 索引
     */
    fun loadIndex(): DictionaryMeta {
        val ifoFile = File(ifoPath)
        if (!ifoFile.exists()) {
            throw IllegalArgumentException("IFO file not found: $ifoPath")
        }

        // 解析 .ifo 文件
        val ifoInfo = parseIfFile(ifoFile)

        // 查找 .idx 文件
        val basePath = ifoPath.substringBeforeLast('.')
        val idxPath = ifoInfo.idxFilePath ?: "$basePath.idx"
        val idxFile = File(idxPath)
        if (!idxFile.exists()) {
            throw IllegalArgumentException("IDX file not found: $idxPath")
        }

        // 查找 .dict 或 .dict.dz 文件
        val dictPath = ifoInfo.dictFilePath ?: "$basePath.dict"
        val dictDzPath = "$dictPath.dz"
        val actualDictPath = when {
            File(dictDzPath).exists() -> dictDzPath
            File(dictPath).exists() -> dictPath
            else -> throw IllegalArgumentException("Dict file not found: $dictPath or $dictDzPath")
        }

        // 设置字符集
        charset = when (ifoInfo.encoding.lowercase()) {
            "utf-8", "utf8" -> Charsets.UTF_8
            "gb2312", "gbk", "gb18030" -> Charset.forName("GBK")
            "big5" -> Charset.forName("Big5")
            "latin-1", "iso-8859-1" -> Charsets.ISO_8859_1
            else -> Charsets.UTF_8
        }

        // 保存 sameTypeSequence
        sameTypeSequence = ifoInfo.sameTypeSequence

        // 加载索引
        index = StardictIndex(idxFile, charset, ifoInfo.wordCount)

        // 打开数据文件
        if (actualDictPath.endsWith(".dz")) {
            dictZipReader = DictZipReader(actualDictPath)
        } else {
            dictFile = RandomAccessFile(actualDictPath, "r")
        }

        val dictKey = ifoFile.nameWithoutExtension
        meta = DictionaryMeta(
            dictKey = dictKey,
            displayName = ifoInfo.bookName,
            format = DictFormat.STAR_DICT,
            langPair = ifoInfo.langPair,
            filePath = ifoPath,
            indexPath = idxPath,
            dataPath = actualDictPath,
            entryCount = ifoInfo.wordCount,
        )

        return meta!!
    }

    /**
     * 查询单词
     */
    fun lookup(word: String): DictEntry? {
        val idx = index ?: return null
        val m = meta ?: return null

        val offsetInfo = idx.findWord(word) ?: return null
        val definition = readDefinition(offsetInfo.dataOffset, offsetInfo.dataSize)

        return DictEntry(
            word = word,
            definition = definition,
            dictKey = m.dictKey,
            dictName = m.displayName,
            definitionType = if (definition.contains("<") && definition.contains(">")) DefinitionType.HTML else DefinitionType.TEXT,
        )
    }

    /**
     * 前缀搜索
     */
    fun searchByPrefix(prefix: String, limit: Int = 20): List<String> {
        val idx = index ?: return emptyList()
        return idx.findByPrefix(prefix, limit)
    }

    /**
     * 读取释义内容
     *
     * 处理 sametypesequence 优化：
     * - 如果 sametypesequence 存在（如 "m" 或 "h"），数据直接是文本内容
     * - 否则，数据格式为：type(1 byte) + data + size(4 bytes)
     */
    private fun readDefinition(offset: Long, size: Int): String {
        val reader = dictZipReader
        val rawBytes = if (reader != null) {
            val raw = reader.read(offset, size, charset)
            raw.toByteArray(charset)
        } else {
            val file = dictFile ?: return ""
            file.seek(offset)
            val bytes = ByteArray(size)
            file.readFully(bytes)
            bytes
        }

        // 如果 sametypesequence 存在，直接返回文本
        if (sameTypeSequence.isNotEmpty()) {
            return String(rawBytes, charset)
        }

        // 否则，解析 type + data + size 格式
        return parseStardictEntry(rawBytes)
    }

    /**
     * 解析 Stardict 条目数据
     *
     * 格式：type(1 byte) + data + size(4 bytes)
     * type: 'm' = 纯文本, 'h' = HTML, 'x' = XDXF, 't' = 词性
     */
    private fun parseStardictEntry(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        var pos = 0
        val result = StringBuilder()

        while (pos < bytes.size) {
            // 读取类型
            val type = bytes[pos].toInt().toChar()
            pos++

            // 读取数据大小（4 字节，大端序）
            if (pos + 4 > bytes.size) break
            val dataSize = ((bytes[pos].toInt() and 0xFF) shl 24) or
                ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            pos += 4

            // 读取数据
            if (pos + dataSize > bytes.size) break
            val data = String(bytes, pos, dataSize, charset)
            pos += dataSize

            // 根据类型处理
            when (type) {
                'm', 'h', 'x' -> {
                    if (result.isNotEmpty()) result.append("\n")
                    result.append(data)
                }
                't' -> {
                    // 词性标记，添加到结果前面
                    result.insert(0, "[$data] ")
                }
                'l' -> {
                    // 词形变化，忽略
                }
                'g' -> {
                    // 语音数据，忽略
                }
                'y' -> {
                    // 词性，添加到结果前面
                    result.insert(0, "$data ")
                }
                'k' -> {
                    // 词组，忽略
                }
                'w' -> {
                    // 音标
                    result.insert(0, "$data ")
                }
                else -> {
                    // 未知类型，添加数据
                    if (result.isNotEmpty()) result.append("\n")
                    result.append(data)
                }
            }
        }

        return result.toString()
    }

    /**
     * 解析 .ifo 文件
     */
    private fun parseIfFile(file: File): IfInfo {
        val info = IfInfo()
        file.readLines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                when (key) {
                    "bookname" -> info.bookName = value
                    "wordcount" -> info.wordCount = value.toIntOrNull() ?: 0
                    "idxfilesize" -> info.idxFileSize = value.toLongOrNull() ?: 0L
                    "idxoffsetbits" -> info.idxOffsetBits = value.toIntOrNull() ?: 32
                    "encoding" -> info.encoding = value
                    "sametypesequence" -> info.sameTypeSequence = value
                    "dicttype" -> info.dictType = value
                }
            }
        }

        // 推断语言对
        info.langPair = inferLangPair(info.bookName, file.nameWithoutExtension)

        return info
    }

    /**
     * 从词典名称推断语言对
     */
    private fun inferLangPair(bookName: String, fileName: String): String {
        val name = "$bookName $fileName".lowercase()
        return when {
            "cedict" in name || "chinese-english" in name || "汉英" in name -> "zh-en"
            "ecdict" in name || "english-chinese" in name || "英汉" in name -> "en-zh"
            "朗文" in name || "longman" in name -> "en-en"
            "牛津" in name || "oxford" in name -> "en-en"
            else -> ""
        }
    }

    override fun close() {
        index?.close()
        dictFile?.close()
        dictZipReader?.close()
        index = null
        dictFile = null
        dictZipReader = null
        meta = null
    }

    /**
     * .ifo 文件解析结果
     */
    private data class IfInfo(
        var bookName: String = "",
        var wordCount: Int = 0,
        var idxFileSize: Long = 0L,
        var idxOffsetBits: Int = 32,
        var encoding: String = "UTF-8",
        var sameTypeSequence: String = "",
        var dictType: String = "",
        var langPair: String = "",
    ) {
        val idxFilePath: String? get() = null  // 由外部推断
        val dictFilePath: String? get() = null // 由外部推断
    }
}
