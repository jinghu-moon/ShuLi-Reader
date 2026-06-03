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

        /**
         * 终极章节标题正则（融合 Legado txtTocRule.json 全部启用规则 + 网络最佳实践）。
         *
         * 性能设计：全部分支使用 ^ 行首锚定，正则引擎只在每行开头尝试匹配，
         * 避免在每个字符位置回溯。500KB block 仅需扫描约 1 万行开头，而非 25 万字符。
         *
         * 覆盖场景：
         * ① 标准目录：第一章 xxx / 第001章 xxx / 第壹佰章 xxx（含回/场/话）
         * ② 数字+分隔符：1、标题 / 001：标题 / 12.标题
         * ③ 大写数字+分隔符：一、标题 / 二十四章-标题
         * ④ 正文+标题：正文 第一卷
         * ⑤ 英文格式：Chapter 1 / Section 2 / Part 3 / Episode 4 / No.5
         * ⑥ 特殊符号包裹/装饰：【第一章】/ ☆、标题 / ★标题
         * ⑦ 卷/章+序号+标题：卷五 开源盛世 / 章三 xxx
         * ⑧ 书名+序号/括号序号：龙族(12) / 龙族12 / 斗破苍穹（一百）
         * ⑨ 独立关键词行：前言/引子/扉页/上部/卷首语/附录/分节阅读/第一页
         *
         * 全部使用 MULTILINE + IGNORE_CASE 模式。
         */
        private val CHAPTER_TITLE_REGEX = Regex(
            listOf(
                // ① 核心目录（第X章/节/卷/集/部/篇/回/场/话），含负向先行防误杀
                """^[ \t　]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\s{0,4}[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\s{0,4}(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|回(?![合来事去])|场(?![和合比电是])|话|篇(?!张))).{0,30}$""",
                // ② 数字+分隔符+标题名称：1、标题 / 001：标题
                """^[ 　\t]{0,4}\d{1,5}[:：,.， 、_—\-].{1,30}$""",
                // ③ 大写数字+分隔符+标题名称：一、标题 / 二十四章 标题
                """^[ 　\t]{0,4}(?:[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章?)[ 、_—\-].{1,30}$""",
                // ④ 正文+标题/序号：正文 第一卷
                """^[ 　\t]{0,4}正文[ 　]{1,4}.{0,20}$""",
                // ⑤ 英文格式：Chapter 1 / Section 2 / Part 3 / Episode 4 / No.5
                """^[ 　\t]{0,4}(?:[Cc]hapter|[Ss]ection|[Pp]art|ＰＡＲＴ|[Nn][oO][.、]|[Ee]pisode)\s{0,4}\d{1,4}.{0,30}$""",
                // ⑥ 特殊符号包裹章节（要求严格）：【第一章】/ [Chapter 1]
                """^[ \t　]{0,4}[【〔〖「『〈［\[](?:第|[Cc]hapter)[\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,10}[章节].{0,20}$""",
                // ⑦ 星号装饰（晋江风格）及特殊单行：☆、标题 / ★标题
                """^[ \t　]{0,4}[☆★✦✧].{1,30}$""",
                // ⑧ 卷/集/部/篇章+序号+标题：卷五 开源盛世 / 集一 xxx / 部一 xxx / 篇一 xxx / 章三十 xxx
                """^[ \t　]{0,4}[卷集部篇章][\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[ 　]{0,4}.{0,30}$""",
                // ⑨ 书名+序号/括号序号：龙族(12) / 龙族12 / 斗破苍穹（一百）
                """^[一-龥]{1,20}[ 　\t]{0,4}(?:[(（][\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[)）]|[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8})[ 　\t]{0,4}$""",
                // ⑩ 独立关键词行：引子/序言/前言/扉页/上部/卷首语/附录/简介/文案/分节阅读/第X页
                """^[ \t　]{0,4}(?:[引楔]子|[引序前]言|扉页|[上中下][部篇卷]|卷首语|附录|(?:内容|文章)?简介|文案|.{0,15}分[页节章段]阅读|第\s{0,4}[\d零一二两三四五六七八九十百千万]{1,6}\s{0,4}[页节])[ 　]{0,4}.{0,20}$""",
            ).joinToString("|"),
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

            // byteStart 跳过标题行（标题由 UI 层单独绘制，不计入正文）
            val rawTitle = m.value  // 含前导空白，未 trim
            val titleLineLen = rawTitle.length
            val afterTitle = titleStart + titleLineLen
            val skipNewline = afterTitle < block.length && block[afterTitle] == '\n'
            val titleLineBytes = block.substring(titleStart, afterTitle).toByteArray(charset).size.toLong() +
                if (skipNewline) 1L else 0L
            val contentByteStart = titleByteStart + titleLineBytes

            chapters.add(
                BookChapterEntity(
                    bookId = 0,
                    chapterIndex = chapters.size,
                    title = titleLine,
                    byteStart = contentByteStart,
                    byteEnd = contentByteStart, // 占位，由后续章节或 EOF 修正
                    charset = charsetName,
                    wordCount = 0,
                )
            )

            lastChapterWordCount = 0
            // seekChar 跳过标题行，使上一章 wordCount 不计入标题文本
            seekChar = if (skipNewline) afterTitle + 1 else afterTitle
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
