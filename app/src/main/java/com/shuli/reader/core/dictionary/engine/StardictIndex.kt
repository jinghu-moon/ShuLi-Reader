package com.shuli.reader.core.dictionary.engine

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

/**
 * Stardict 索引文件解析器
 *
 * 使用 MappedByteBuffer + 3 个 primitive 数组实现零 GC 的高效词条检索
 *
 * 内存布局：
 * - words: 所有单词字符串（连续存储）
 * - wordOffsets: 每个单词在 words 中的起始位置
 * - dataOffsets: 每个词条在 .dict 文件中的偏移
 * - dataSizes: 每个词条的数据大小
 */
class StardictIndex(
    private val idxFile: File,
    private val charset: Charset,
    private val expectedWordCount: Int,
) : AutoCloseable {

    /** 内存映射的索引文件 */
    private var mmap: MappedByteBuffer? = null

    /** 单词字符串存储（连续字节数组） */
    private var wordsData: ByteArray = ByteArray(0)

    /** 每个单词在 wordsData 中的起始位置 */
    private var wordStarts: IntArray = IntArray(0)

    /** 每个单词的长度 */
    private var wordLens: IntArray = IntArray(0)

    /** 每个词条在 .dict 文件中的偏移 */
    private var dataOffsets: LongArray = LongArray(0)

    /** 每个词条的数据大小 */
    private var dataSizes: IntArray = IntArray(0)

    /** 词条数量 */
    private var wordCount: Int = 0

    /** 是否已加载 */
    val isLoaded: Boolean get() = wordCount > 0

    /** 词条数量 */
    fun getWordCount(): Int = wordCount

    init {
        loadIndex()
    }

    /**
     * 加载索引文件到内存映射缓冲区
     */
    private fun loadIndex() {
        val raf = RandomAccessFile(idxFile, "r")
        val fileSize = raf.length()

        // 内存映射整个文件
        mmap = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
            .apply { order(ByteOrder.BIG_ENDIAN) }
        raf.close()

        // 第一遍：计算单词数量和总字符数
        val buf = mmap!!
        buf.position(0)

        var count = 0
        var totalChars = 0
        var pos = 0

        while (pos < fileSize) {
            // 找到 null 终止符
            val wordStart = pos
            while (pos < fileSize && buf.get(pos) != 0.toByte()) {
                pos++
            }
            if (pos >= fileSize) break

            val wordLen = pos - wordStart
            totalChars += wordLen
            count++

            // 跳过 null + 8 bytes (offset + size)
            pos += 9
        }

        wordCount = count

        // 分配数组
        wordsData = ByteArray(totalChars)
        wordStarts = IntArray(count)
        wordLens = IntArray(count)
        dataOffsets = LongArray(count)
        dataSizes = IntArray(count)

        // 第二遍：读取数据
        buf.position(0)
        var wordsPos = 0
        var entryIndex = 0
        pos = 0

        while (pos < fileSize && entryIndex < count) {
            // 读取单词
            val wordStart = pos
            while (pos < fileSize && buf.get(pos) != 0.toByte()) {
                pos++
            }
            if (pos >= fileSize) break

            val wordLen = pos - wordStart
            wordStarts[entryIndex] = wordsPos
            wordLens[entryIndex] = wordLen

            // 复制单词数据
            buf.position(wordStart)
            buf.get(wordsData, wordsPos, wordLen)
            wordsPos += wordLen

            pos++ // 跳过 null

            // 读取 offset 和 size
            if (pos + 8 > fileSize) break

            buf.position(pos)
            dataOffsets[entryIndex] = buf.int.toLong() and 0xFFFFFFFFL
            dataSizes[entryIndex] = buf.int
            pos += 8

            entryIndex++
        }
    }

    /**
     * 获取指定索引的单词
     */
    private fun getWord(index: Int): String {
        val start = wordStarts[index]
        val len = wordLens[index]
        return String(wordsData, start, len, charset)
    }

    /**
     * 精确查找单词
     *
     * 使用二分查找，O(log n) 时间复杂度
     *
     * @return 索引条目，未找到返回 null
     */
    fun findWord(word: String): IndexEntry? {
        val target = word.lowercase()
        val targetBytes = word.toByteArray(charset)

        // 二分查找
        var low = 0
        var high = wordCount - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midWord = getWord(mid).lowercase()

            val cmp = midWord.compareTo(target)
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return IndexEntry(
                    word = getWord(mid),
                    dataOffset = dataOffsets[mid],
                    dataSize = dataSizes[mid],
                )
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
        val target = prefix.lowercase()

        // 找到第一个匹配前缀的位置
        var low = 0
        var high = wordCount - 1
        var startIndex = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midWord = getWord(mid).lowercase()

            if (midWord.startsWith(target)) {
                startIndex = mid
                high = mid - 1 // 继续向前找
            } else if (midWord < target) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (startIndex < 0) return emptyList()

        // 收集匹配的单词
        val result = mutableListOf<String>()
        for (i in startIndex until wordCount) {
            val word = getWord(i)
            if (word.lowercase().startsWith(target)) {
                result.add(word)
                if (result.size >= limit) break
            } else {
                break
            }
        }

        return result
    }

    override fun close() {
        mmap = null
        wordsData = ByteArray(0)
        wordStarts = IntArray(0)
        wordLens = IntArray(0)
        dataOffsets = LongArray(0)
        dataSizes = IntArray(0)
        wordCount = 0
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
