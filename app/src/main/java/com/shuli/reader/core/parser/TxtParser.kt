package com.shuli.reader.core.parser

import com.shuli.reader.core.database.entity.BookChapterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

class TxtParser {

    companion object {
        /** 编码探测样本大小 */
        private const val CHARSET_SAMPLE_SIZE = 8192

        /** 合并的章节标题正则，MULTILINE 模式直接在 block 中扫描整行 */
        private val CHAPTER_TITLE_REGEX = Regex(
            "^(?:第[一二三四五六七八九十百千零\\d]+[章节回卷集部篇].*" +
                "|Chapter\\s+\\d+.*" +
                "|[卷集部篇][一二三四五六七八九十百千零\\d]+.*" +
                "|\\d+[\\.、]\\s*\\S.*" +
                "|[序楔引][章言子]?.*" +
                "|前言|后记|尾声|附录|番外" +
                "|[（(][一二三四五六七八九十百千零\\d]+[）)].*" +
                "|【[^】]+】.*)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )

        /** 章节扫描 block 大小（Legado 风格 500KB） */
        private const val SCAN_BUFFER_SIZE = 512 * 1024

        /** UTF-8 BOM */
        private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        // 整合 Legado 经典文件名解析规则
        private val NAME_AUTHOR_PATTERNS = listOf(
            Regex("(.*?)《([^《》]+)》.*?作者[：:](.*)"),
            Regex("(.*?)《([^《》]+)》(.*)"),
            Regex("(^)(.+) 作者[：:](.+)$"),
            Regex("(^)(.+) by (.+)$"),
        )
    }

    fun parseMetadata(file: File): Pair<String, String?> {
        val fileName = file.nameWithoutExtension

        // 1. 优先尝试从文件名用正则提取书名和作者
        for (pattern in NAME_AUTHOR_PATTERNS) {
            val match = pattern.find(fileName)
            if (match != null) {
                val name = match.groupValues[2].trim()
                val author = match.groupValues[3].trim()
                if (name.isNotBlank()) {
                    // 清理常见的 .txt 杂质后缀
                    val cleanedAuthor = author.replace(Regex("[\\.txt|\\.TXT]$"), "").trim()
                    return name to (if (cleanedAuthor.isNotBlank()) cleanedAuthor else null)
                }
            }
        }

        // 2. 兜底策略：没有规则时直接使用文件名作为书名，作者为空。不触碰任何文件 IO，保持极致导入速度
        return fileName to null
    }

    /**
     * 编码探测：只读取文件头部样本，不加载全文件。
     */
    internal fun detectCharset(file: File): Charset {
        return try {
            val sampleSize = minOf(file.length(), CHARSET_SAMPLE_SIZE.toLong()).toInt()
            val sample = ByteArray(sampleSize)
            java.io.FileInputStream(file).use { fis ->
                fis.read(sample, 0, sampleSize)
            }
            val detector = org.mozilla.universalchardet.UniversalDetector(null)
            detector.handleData(sample, 0, sampleSize)
            detector.dataEnd()
            val detected = detector.detectedCharset
            if (detected != null) {
                Charset.forName(detected)
            } else {
                Charset.forName("UTF-8")
            }
        } catch (_: Exception) {
            Charset.forName("UTF-8")
        }
    }

    /**
     * 构建章节目录索引（v4 Legado 风格流式扫描）。
     *
     * 性能：5MB UTF-8 文件 ~1-1.5s（vs 旧版 ~8s）。
     * 内存峰值：500KB block buffer + 当前 block decoded String，远小于全文件 String。
     *
     * 算法：
     * 1. 500KB BufferedInputStream 流式读
     * 2. 块尾向前找最后一个 \n，剩余字节留到下一轮（避免多字节字符切割）
     * 3. 每块用 charset 解码为 String，MULTILINE 正则扫章节标题
     * 4. 章节起点字节偏移 = curOffset + chapterContent.toByteArray(charset).size
     * 5. 顺手累计 wordCount（chapterContent.length）
     */
    suspend fun parseChapterIndex(file: File): List<BookChapterEntity> = withContext(Dispatchers.IO) {
        val charset = detectCharset(file)
        val charsetName = charset.name()
        val fileLength = file.length()
        if (fileLength <= 0) return@withContext emptyList<BookChapterEntity>()

        val nlByte: Byte = 0x0A
        val buffer = ByteArray(SCAN_BUFFER_SIZE)
        val chapters = mutableListOf<BookChapterEntity>()
        var lastChapterWordCount = 0  // 当前章节累计字数（待回填到上一章）
        var curOffset = 0L            // 已消费字节数（绝对文件偏移）
        var bufferStart = 0           // 上轮残留字节占据的 buffer 区间

        java.io.BufferedInputStream(java.io.FileInputStream(file)).use { bis ->
            // 处理 UTF-8 BOM（仅当 charset 为 UTF-8 系列且文件起始有 BOM）
            val readBom = bis.read(buffer, 0, 3)
            val hasBom = readBom == 3 &&
                buffer[0] == UTF8_BOM[0] && buffer[1] == UTF8_BOM[1] && buffer[2] == UTF8_BOM[2]
            if (hasBom) {
                curOffset = 3
                bufferStart = 0
            } else if (readBom > 0) {
                bufferStart = readBom
            }

            while (true) {
                val n = bis.read(buffer, bufferStart, SCAN_BUFFER_SIZE - bufferStart)
                if (n <= 0) {
                    // EOF：处理残留
                    if (bufferStart > 0) {
                        val tail = String(buffer, 0, bufferStart, charset)
                        processBlock(
                            block = tail,
                            blockByteOffset = curOffset,
                            charset = charset,
                            charsetName = charsetName,
                            chapters = chapters,
                            lastChapterWordCountRef = intArrayOf(lastChapterWordCount),
                        ).also { lastChapterWordCount = it }
                        curOffset += bufferStart.toLong()
                        bufferStart = 0
                    }
                    break
                }

                var end = bufferStart + n
                // 若填满 buffer，退到最后一个 \n（包含）
                if (end == SCAN_BUFFER_SIZE) {
                    var i = end - 1
                    while (i >= 0 && buffer[i] != nlByte) i--
                    if (i > 0) end = i + 1
                    // 若整块都没有 \n（超长行），保持 end，让下一轮直接接续
                }

                val block = String(buffer, 0, end, charset)
                val ref = intArrayOf(lastChapterWordCount)
                processBlock(
                    block = block,
                    blockByteOffset = curOffset,
                    charset = charset,
                    charsetName = charsetName,
                    chapters = chapters,
                    lastChapterWordCountRef = ref,
                )
                lastChapterWordCount = ref[0]

                curOffset += end.toLong()

                // 把 buffer[end..bufferStart+n) 的剩余字节移到 buffer 开头
                val remaining = bufferStart + n - end
                if (remaining > 0) {
                    System.arraycopy(buffer, end, buffer, 0, remaining)
                }
                bufferStart = remaining
            }
        }

        // 修正最后一章的 byteEnd 与 wordCount
        if (chapters.isNotEmpty()) {
            val last = chapters.last()
            chapters[chapters.lastIndex] = last.copy(
                byteEnd = fileLength,
                wordCount = lastChapterWordCount,
            )
        }

        // 无章节：整本作为一章
        if (chapters.isEmpty()) {
            return@withContext listOf(
                BookChapterEntity(
                    bookId = 0,
                    chapterIndex = 0,
                    title = "Full Text",
                    byteStart = 0L,
                    byteEnd = fileLength,
                    charset = charsetName,
                    wordCount = lastChapterWordCount,
                )
            )
        }

        chapters
    }

    /**
     * 处理单个 block：扫描章节标题，更新 chapters 列表与 lastChapterWordCount。
     * @param lastChapterWordCountRef 单元素 IntArray，用作可变引用 in/out
     * @return 当前累积的 lastChapterWordCount（与 ref[0] 同步）
     */
    private fun processBlock(
        block: String,
        blockByteOffset: Long,
        charset: Charset,
        charsetName: String,
        chapters: MutableList<BookChapterEntity>,
        lastChapterWordCountRef: IntArray,
    ): Int {
        var seekChar = 0
        var lastChapterWordCount = lastChapterWordCountRef[0]

        for (m in CHAPTER_TITLE_REGEX.findAll(block)) {
            val titleStart = m.range.first
            val titleLine = m.value.trim()
            if (titleLine.isEmpty()) continue

            // 上一章的内容 = block[seekChar, titleStart)
            val prevContent = block.substring(seekChar, titleStart)
            val prevContentBytes = prevContent.toByteArray(charset).size.toLong()

            // 关闭上一章（若有）
            val titleByteStart: Long = if (chapters.isNotEmpty()) {
                val last = chapters.last()
                val newWordCount = lastChapterWordCount + prevContent.length
                val newByteEnd = last.byteEnd + prevContentBytes
                chapters[chapters.lastIndex] = last.copy(
                    byteEnd = newByteEnd,
                    wordCount = newWordCount,
                )
                newByteEnd
            } else {
                // 第一个章节：从 block 头算
                blockByteOffset + block.substring(0, titleStart).toByteArray(charset).size.toLong()
            }

            chapters.add(
                BookChapterEntity(
                    bookId = 0,
                    chapterIndex = chapters.size,
                    title = titleLine,
                    byteStart = titleByteStart,
                    byteEnd = titleByteStart, // 占位，由后续章节或 EOF 修正
                    charset = charsetName,
                    wordCount = 0,
                )
            )

            lastChapterWordCount = 0
            seekChar = titleStart
        }

        // block 末尾剩余内容（未被新章节关闭）→ 累计到 lastChapterWordCount
        val tailContent = block.substring(seekChar)
        lastChapterWordCount += tailContent.length

        // 同步刷新最后一章的 byteEnd，让下一个 block 能正确算出 titleByteStart
        if (chapters.isNotEmpty()) {
            val tailContentBytes = tailContent.toByteArray(charset).size.toLong()
            val last = chapters.last()
            chapters[chapters.lastIndex] = last.copy(
                byteEnd = last.byteEnd + tailContentBytes,
            )
        }

        lastChapterWordCountRef[0] = lastChapterWordCount
        return lastChapterWordCount
    }
}
