package com.shuli.reader.core.dictionary.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Stardict 索引文件解析器
 *
 * 使用内存映射 + 二分查找实现高效词条检索
 */
class StardictIndex(
    private val idxFile: File,
    private val charset: Charset,
    private val expectedWordCount: Int,
) : AutoCloseable {

    /** 索引条目数组（按单词排序） */
    private var entries: Array<IndexEntry>? = null

    /** 是否已加载 */
    val isLoaded: Boolean get() = entries != null

    /** 词条数量 */
    val wordCount: Int get() = entries?.size ?: 0

    init {
        loadIndex()
    }

    /**
     * 加载索引文件
     */
    private fun loadIndex() {
        val file = RandomAccessFile(idxFile, "r")
        val fileSize = file.length()
        val allBytes = ByteArray(fileSize.toInt())
        file.readFully(allBytes)
        file.close()

        val entryList = mutableListOf<IndexEntry>()
        var pos = 0

        while (pos < allBytes.size) {
            // 读取单词（以 null 结尾）
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

            // 读取偏移和大小（各 4 字节，大端序）
            if (pos + 8 > allBytes.size) break

            val offset = ((allBytes[pos].toInt() and 0xFF) shl 24) or
                ((allBytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((allBytes[pos + 2].toInt() and 0xFF) shl 8) or
                (allBytes[pos + 3].toInt() and 0xFF)
            pos += 4

            val size = ((allBytes[pos].toInt() and 0xFF) shl 24) or
                ((allBytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((allBytes[pos + 2].toInt() and 0xFF) shl 8) or
                (allBytes[pos + 3].toInt() and 0xFF)
            pos += 4

            entryList.add(IndexEntry(word, offset.toLong(), size))
        }

        entries = entryList.toTypedArray()
    }

    /**
     * 精确查找单词
     *
     * @return 索引条目，未找到返回 null
     */
    fun findWord(word: String): IndexEntry? {
        val arr = entries ?: return null
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
                else -> return arr[mid]
            }
        }

        return null
    }

    /**
     * 前缀搜索
     *
     * @return 匹配的单词列表
     */
    fun findByPrefix(prefix: String, limit: Int = 20): List<String> {
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
                high = mid - 1 // 继续向前找
            } else if (midVal < target) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (startIndex < 0) return emptyList()

        // 收集匹配的单词
        val result = mutableListOf<String>()
        for (i in startIndex until arr.size) {
            if (arr[i].word.lowercase().startsWith(target)) {
                result.add(arr[i].word)
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
     * 索引条目
     */
    data class IndexEntry(
        /** 单词 */
        val word: String,
        /** 在 .dict 文件中的偏移 */
        val dataOffset: Long,
        /** 数据大小（字节） */
        val dataSize: Int,
    )
}
