package com.shuli.reader.core.parser

import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

class TxtParser {

    companion object {
        /** 文件大小阈值：小于该值直接读取，大于等于使用 mmap */
        private const val MMAP_THRESHOLD_BYTES = 2 * 1024 * 1024L // 2MB

        /** 编码探测样本大小 */
        private const val CHARSET_SAMPLE_SIZE = 8192

        private val CHAPTER_PATTERNS = listOf(
            Regex("^第[一二三四五六七八九十百千零\\d]+[章节回卷集部篇]"),
            Regex("^Chapter\\s+\\d+", RegexOption.IGNORE_CASE),
            Regex("^[卷集部篇][一二三四五六七八九十百千零\\d]+"),
            Regex("^\\d+[\\..、]\\s*\\S"),
        )

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

    suspend fun parse(file: File): BookContent = withContext(Dispatchers.IO) {
        val charset = detectCharset(file)
        val content = readContent(file, charset)

        val title = extractTitle(file.nameWithoutExtension, content)
        val chapters = detectChapters(content)

        BookContent(
            title = title,
            author = null,
            encoding = charset.name(),
            totalLength = content.length.toLong(),
            chapters = chapters,
            content = content,
        )
    }

    /**
     * 根据文件大小选择读取策略：
     * - 小文件（< 2MB）：直接 readBytes
     * - 大文件（>= 2MB）：使用 mmap 映射
     */
    private fun readContent(file: File, charset: Charset): String {
        return if (file.length() < MMAP_THRESHOLD_BYTES) {
            String(file.readBytes(), charset)
        } else {
            readWithMmap(file, charset)
        }
    }

    private fun readWithMmap(file: File, charset: Charset): String {
        java.io.FileInputStream(file).use { fis ->
            val buffer: MappedByteBuffer = fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                0,
                file.length(),
            )
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return String(bytes, charset)
        }
    }

    /**
     * 编码探测：只读取文件头部样本，不加载全文件。
     */
    private fun detectCharset(file: File): Charset {
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

    private fun extractTitle(fileName: String, content: String): String {
        val firstLine = content.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it.length in 2..50 }
        return firstLine ?: fileName
    }

    fun detectChapters(content: String): List<Chapter> {
        val chapterPositions = mutableListOf<Pair<String, Int>>()

        // 手动扫描行，正确处理 \r\n、\r、\n 三种换行符
        var lineStart = 0
        while (lineStart <= content.length) {
            val lineEnd = content.indexOfAny(charArrayOf('\r', '\n'), lineStart)
            val actualEnd = if (lineEnd < 0) content.length else lineEnd
            val line = content.substring(lineStart, actualEnd)
            val trimmed = line.trim()

            if (trimmed.isNotEmpty()) {
                for (pattern in CHAPTER_PATTERNS) {
                    if (pattern.containsMatchIn(trimmed)) {
                        chapterPositions.add(trimmed to lineStart)
                        break
                    }
                }
            }

            // 跳过换行符（\r\n 或单个 \r/\n）
            lineStart = when {
                lineEnd < 0 -> content.length + 1 // 到达末尾
                lineEnd < content.length && content[lineEnd] == '\r' &&
                    lineEnd + 1 < content.length && content[lineEnd + 1] == '\n' -> lineEnd + 2
                else -> lineEnd + 1
            }
        }

        if (chapterPositions.isEmpty()) {
            return if (content.isNotBlank()) {
                listOf(Chapter(title = "Full Text", startIndex = 0, endIndex = content.length))
            } else {
                emptyList()
            }
        }

        return chapterPositions.mapIndexed { index, (title, start) ->
            val end = if (index < chapterPositions.size - 1) {
                chapterPositions[index + 1].second
            } else {
                content.length
            }
            Chapter(title = title, startIndex = start, endIndex = end)
        }
    }
}
