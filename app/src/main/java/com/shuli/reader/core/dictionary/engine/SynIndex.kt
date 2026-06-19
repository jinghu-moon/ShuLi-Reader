package com.shuli.reader.core.dictionary.engine

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset

/**
 * Stardict .syn 同义词索引解析器
 *
 * .syn 文件格式：
 * [ synonym_word: UTF-8 字符串，以 \0 结尾 ]
 * [ original_word_index: 32 位无符号整数，网络字节序 ]
 *
 * 排序规则与 .idx 相同。多个同义词可指向同一个原始词条。
 */
class SynIndex(
    private val synFile: File,
    private val charset: Charset = Charsets.UTF_8,
) : AutoCloseable {

    /** 同义词条目数组 */
    private var entries: Array<SynEntry>? = null

    /** 是否已加载 */
    val isLoaded: Boolean get() = entries != null

    /** 同义词数量 */
    val wordCount: Int get() = entries?.size ?: 0

    init {
        loadIndex()
    }

    /**
     * 加载 .syn 文件
     */
    private fun loadIndex() {
        if (!synFile.exists() || synFile.length() == 0L) {
            entries = emptyArray()
            return
        }

        val raf = java.io.RandomAccessFile(synFile, "r")
        val fileSize = raf.length()
        val allBytes = ByteArray(fileSize.toInt())
        raf.readFully(allBytes)
        raf.close()

        val entryList = mutableListOf<SynEntry>()
        var pos = 0

        while (pos < allBytes.size) {
            // 找到 null 终止符
            var wordEnd = -1
            for (i in pos until allBytes.size) {
                if (allBytes[i] == 0.toByte()) {
                    wordEnd = i
                    break
                }
            }
            if (wordEnd < 0) break

            val wordBytes = allBytes.copyOfRange(pos, wordEnd)
            val word = String(wordBytes, charset)
            pos = wordEnd + 1

            // 读取 original_word_index（4 字节，大端序）
            if (pos + 4 > allBytes.size) break

            val originalIndex = ((allBytes[pos].toInt() and 0xFF) shl 24) or
                ((allBytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((allBytes[pos + 2].toInt() and 0xFF) shl 8) or
                (allBytes[pos + 3].toInt() and 0xFF)
            pos += 4

            entryList.add(SynEntry(word, originalIndex))
        }

        entries = entryList.toTypedArray()
    }

    /**
     * 查找同义词
     *
     * @param word 同义词
     * @return 指向的原始词条索引，未找到返回 -1
     */
    fun findSynonym(word: String): Int {
        val arr = entries ?: return -1
        val target = word.lowercase()

        // 二分查找
        var low = 0
        var high = arr.size - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = arr[mid].word.lowercase()

            val cmp = midVal.compareTo(target)
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return arr[mid].originalIndex
            }
        }

        return -1
    }

    /**
     * 前缀搜索同义词
     */
    fun findByPrefix(prefix: String, limit: Int = 10): List<SynEntry> {
        val arr = entries ?: return emptyList()
        val target = prefix.lowercase()

        // 找到第一个匹配前缀的位置
        var low = 0
        var high = arr.size - 1
        var startIndex = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = arr[mid].word.lowercase()

            if (midVal.startsWith(target)) {
                startIndex = mid
                high = mid - 1
            } else if (midVal < target) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (startIndex < 0) return emptyList()

        // 收集匹配的同义词
        val result = mutableListOf<SynEntry>()
        for (i in startIndex until arr.size) {
            if (arr[i].word.lowercase().startsWith(target)) {
                result.add(arr[i])
                if (result.size >= limit) break
            } else {
                break
            }
        }

        return result
    }

    override fun close() {
        entries = null
    }

    /**
     * 同义词条目
     */
    data class SynEntry(
        /** 同义词 */
        val word: String,
        /** 指向的原始词条索引 */
        val originalIndex: Int,
    )
}
