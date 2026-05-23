package com.shuli.reader.core.parser

import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

class TxtParser {

    companion object {
        private val CHAPTER_PATTERNS = listOf(
            Regex("^第[一二三四五六七八九十百千零\\d]+[章节回卷集部篇]"),
            Regex("^Chapter\\s+\\d+", RegexOption.IGNORE_CASE),
            Regex("^卷[一二三四五六七八九十百千零\\d]+"),
            Regex("^\\d+[\\..、]\\s*\\S"),
        )

        // 整合 Legado 经典文件名解析规则
        private val NAME_AUTHOR_PATTERNS = listOf(
            Regex("(.*?)《([^《》]+)》.*?作者[：:](.*)"),
            Regex("(.*?)《([^《》]+)》(.*)"),
            Regex("(^)(.+) 作者[：:](.+)$"),
            Regex("(^)(.+) by (.+)$")
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
        val bytes = file.readBytes()
        val charset = detectCharset(bytes, file)
        val content = String(bytes, charset)

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

    private fun detectCharset(bytes: ByteArray, file: File): Charset {
        return try {
            val detector = org.mozilla.universalchardet.UniversalDetector(null)
            detector.handleData(bytes, 0, minOf(bytes.size, 4096))
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
        val lines = content.lines()
        val chapterPositions = mutableListOf<Pair<String, Int>>()

        var currentPos = 0
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                for (pattern in CHAPTER_PATTERNS) {
                    if (pattern.containsMatchIn(trimmed)) {
                        chapterPositions.add(trimmed to currentPos)
                        break
                    }
                }
            }
            currentPos += line.length + 1
        }

        if (chapterPositions.isEmpty()) return emptyList()

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
