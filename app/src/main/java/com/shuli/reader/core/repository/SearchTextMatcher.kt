package com.shuli.reader.core.repository

import com.shuli.reader.core.parser.Utf16ToByteCodec
import java.nio.charset.Charset

/**
 * 纯文本匹配引擎：从章节文本中提取搜索结果。
 * 单书搜索与全库搜索共用此逻辑，避免两套 offset/context 实现。
 */
object SearchTextMatcher {

    private const val CONTEXT_RADIUS = 20

    data class MatchResult(
        val chapterIndex: Int,
        val chapterTitle: String,
        val byteOffset: Long,
        val context: String,
        val matchedText: String,
    )

    /**
     * 在章节文本中搜索 query，返回所有匹配。
     * 优先使用 utf16ToByteBlob 做 O(1) 字节偏移映射；无 blob 时退化为 O(n) 编码计算。
     *
     * 优化：纯中文 query 不做 lowercase 拷贝，直接原文匹配。
     */
    fun match(
        chapterText: String,
        query: String,
        chapterIndex: Int,
        chapterTitle: String,
        chapterByteStart: Long,
        utf16ToByteBlob: ByteArray = ByteArray(0),
        charset: Charset = Charsets.UTF_8,
        maxMatches: Int = Int.MAX_VALUE,
    ): List<MatchResult> {
        if (query.isEmpty() || chapterText.isEmpty()) return emptyList()

        val ignoreCase = needsIgnoreCase(query)
        val haystack = if (ignoreCase) chapterText.lowercase() else chapterText
        val needle = if (ignoreCase) query.lowercase() else query

        val utf16Map = decodeMapOrNull(utf16ToByteBlob)

        val results = mutableListOf<MatchResult>()
        var searchFrom = 0

        while (searchFrom < haystack.length && results.size < maxMatches) {
            val matchIndex = haystack.indexOf(needle, searchFrom)
            if (matchIndex == -1) break

            val byteOffset = computeByteOffset(
                matchIndex, chapterByteStart, chapterText, utf16Map, charset
            )

            val contextStart = safeSubstringStart(
                chapterText,
                (matchIndex - CONTEXT_RADIUS).coerceAtLeast(0),
            )
            val contextEnd = safeSubstringEnd(
                chapterText,
                (matchIndex + query.length + CONTEXT_RADIUS).coerceAtMost(chapterText.length),
            )

            results += MatchResult(
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                byteOffset = byteOffset,
                context = chapterText.substring(contextStart, contextEnd),
                matchedText = chapterText.substring(matchIndex, matchIndex + query.length),
            )

            searchFrom = matchIndex + query.length.coerceAtLeast(1)
        }

        return results
    }

    private fun decodeMapOrNull(blob: ByteArray): IntArray? {
        if (blob.isEmpty()) return null
        return runCatching { Utf16ToByteCodec.decode(blob) }.getOrNull()
    }

    private fun computeByteOffset(
        matchIndex: Int,
        chapterByteStart: Long,
        chapterText: String,
        utf16Map: IntArray?,
        charset: Charset,
    ): Long {
        if (utf16Map != null && matchIndex < utf16Map.size) {
            return chapterByteStart + utf16Map[matchIndex].toLong()
        }
        // 无 blob 兜底：O(n) 编码计算
        return chapterByteStart + chapterText.substring(0, matchIndex).toByteArray(charset).size.toLong()
    }

    private fun safeSubstringStart(text: String, index: Int): Int {
        if (index <= 0 || index >= text.length) return index
        return if (Character.isLowSurrogate(text[index]) && Character.isHighSurrogate(text[index - 1])) {
            index - 1
        } else {
            index
        }
    }

    private fun safeSubstringEnd(text: String, index: Int): Int {
        if (index <= 0 || index >= text.length) return index
        return if (Character.isHighSurrogate(text[index - 1]) && Character.isLowSurrogate(text[index])) {
            index + 1
        } else {
            index
        }
    }

    /**
     * 只有 query 含 ASCII 字母时才需要 ignoreCase。
     * 纯中文/数字/标点不需要 lowercase 拷贝。
     */
    private fun needsIgnoreCase(query: String): Boolean {
        for (ch in query) {
            if (ch in 'A'..'Z' || ch in 'a'..'z') return true
        }
        return false
    }
}
