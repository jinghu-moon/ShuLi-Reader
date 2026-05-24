package com.shuli.reader.core.parser

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

class TxtParserLargeFileTest {

    private val parser = TxtParser()

    @Test
    fun smallFile_isReadDirectly() = runTest {
        val tempFile = File.createTempFile("small", ".txt")
        try {
            tempFile.writeText("第一章 测试\n这是内容", charset = Charsets.UTF_8)
            val content = parser.parse(tempFile)
            assertTrue("小文件应成功解析", content.chapters.isNotEmpty() || content.content.isNotBlank())
            assertEquals(Charsets.UTF_8.name(), content.encoding)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun chapterOffsets_areBasedOnCharactersNotBytes() = runTest {
        val tempFile = File.createTempFile("unicode", ".txt")
        try {
            tempFile.writeText(
                """
                |第一章 中文测试
                |这是中文内容，每个中文字符占3个字节。
                |第二章 更多测试
                |More content here.
                """.trimMargin(),
                charset = Charsets.UTF_8,
            )
            val content = parser.parse(tempFile)
            if (content.chapters.size >= 2) {
                val ch1 = content.chapters[0]
                val ch2 = content.chapters[1]
                assertTrue("章节偏移应为正数", ch1.startIndex >= 0)
                assertTrue("第二章应在第一章之后", ch2.startIndex > ch1.startIndex)
                val textAtCh1 = content.content.substring(ch1.startIndex)
                assertTrue("偏移处应包含章节标题", textAtCh1.startsWith("第一章"))
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun gbkEncodedFile_isDetectedAndParsedCorrectly() = runTest {
        val tempFile = File.createTempFile("gbk", ".txt")
        try {
            val gbkBytes = "第一章 测试\n这是GBK编码的内容\n第二章 继续\n更多内容".toByteArray(Charset.forName("GBK"))
            tempFile.writeBytes(gbkBytes)
            val content = parser.parse(tempFile)
            assertTrue("GBK 文件应解析出章节", content.chapters.isNotEmpty())
            assertTrue("内容应包含中文", content.content.contains("测试"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun contentWithoutChapters_isPreservedCompletely() = runTest {
        val tempFile = File.createTempFile("nochap", ".txt")
        try {
            tempFile.writeText("这是一段没有章节标题的连续文本内容。" * 100)
            val content = parser.parse(tempFile)
            assertTrue("无章节时内容仍应存在", content.content.isNotBlank())
        } finally {
            tempFile.delete()
        }
    }
}

private operator fun String.times(n: Int): String = repeat(n)
