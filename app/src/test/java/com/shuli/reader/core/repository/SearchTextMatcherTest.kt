package com.shuli.reader.core.repository

import com.shuli.reader.core.parser.Utf16ToByteCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextMatcherTest {

    @Test
    fun match_findsChineseQueryAndUsesUtf16ByteMap() {
        val text = "甲A😊乙搜索丙"
        val blob = Utf16ToByteCodec.encode(utf16ToUtf8Map(text))

        val results = SearchTextMatcher.match(
            chapterText = text,
            query = "搜索",
            chapterIndex = 2,
            chapterTitle = "第二章",
            chapterByteStart = 100L,
            utf16ToByteBlob = blob,
            charset = Charsets.UTF_8,
        )

        assertEquals(1, results.size)
        assertEquals(2, results.single().chapterIndex)
        assertEquals("第二章", results.single().chapterTitle)
        assertEquals(111L, results.single().byteOffset)
        assertEquals("搜索", results.single().matchedText)
        assertTrue(results.single().context.contains("搜索"))
    }

    @Test
    fun match_ignoresAsciiCaseAndKeepsOriginalMatchedText() {
        val results = SearchTextMatcher.match(
            chapterText = "Alpha beta ALPHA",
            query = "alpha",
            chapterIndex = 0,
            chapterTitle = "正文",
            chapterByteStart = 0L,
        )

        assertEquals(2, results.size)
        assertEquals("Alpha", results[0].matchedText)
        assertEquals("ALPHA", results[1].matchedText)
    }

    @Test
    fun match_doesNotSplitSurrogatePairInContext() {
        val text = "😊" + "a".repeat(19) + "搜索"

        val results = SearchTextMatcher.match(
            chapterText = text,
            query = "搜索",
            chapterIndex = 0,
            chapterTitle = "正文",
            chapterByteStart = 0L,
        )

        assertEquals(1, results.size)
        assertTrue(results.single().context.startsWith("😊"))
    }

    @Test
    fun match_respectsMaxMatches() {
        val results = SearchTextMatcher.match(
            chapterText = "搜索 搜索 搜索",
            query = "搜索",
            chapterIndex = 0,
            chapterTitle = "正文",
            chapterByteStart = 0L,
            maxMatches = 2,
        )

        assertEquals(2, results.size)
    }

    private fun utf16ToUtf8Map(text: String): IntArray {
        val map = IntArray(text.length + 1)
        var byteOffset = 0
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            val charCount = Character.charCount(codePoint)
            repeat(charCount) { offset ->
                map[index + offset] = byteOffset
            }
            byteOffset += String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            index += charCount
        }
        map[text.length] = byteOffset
        return map
    }
}
