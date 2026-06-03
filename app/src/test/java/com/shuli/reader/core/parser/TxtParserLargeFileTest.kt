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
    fun smallFile_isParsedWithChapters() = runTest {
        val tempFile = File.createTempFile("small", ".txt")
        try {
            tempFile.writeText("第一章 测试\n这是内容", charset = Charsets.UTF_8)
            val chapters = parser.parseChapterIndex(tempFile)
            assertTrue("小文件应成功解析出章节", chapters.isNotEmpty())
            assertEquals(Charsets.UTF_8.name(), chapters.first().charset)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun chapterOffsets_areBasedOnBytes() = runTest {
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
            val chapters = parser.parseChapterIndex(tempFile)
            if (chapters.size >= 2) {
                val ch1 = chapters[0]
                val ch2 = chapters[1]
                assertTrue("章节偏移应为正数", ch1.byteStart >= 0)
                assertTrue("第二章应在第一章之后", ch2.byteStart > ch1.byteStart)
                // byteStart 指向标题行之后的正文内容
                val contentBytes = "这是中文内容".toByteArray(Charsets.UTF_8)
                val buf = ByteArray(contentBytes.size)
                java.io.RandomAccessFile(tempFile, "r").use { raf ->
                    raf.seek(ch1.byteStart)
                    raf.read(buf)
                }
                val textAtCh1 = String(buf, Charsets.UTF_8)
                assertTrue("偏移处应包含章节正文", textAtCh1.contains("这是中文"))
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
            val chapters = parser.parseChapterIndex(tempFile)
            assertTrue("GBK 文件应解析出章节", chapters.isNotEmpty())
            // 验证编码检测为 GBK 系列（GB18030 是 GBK 的超集，universalchardet 可能返回任一）
            val detectedCharset = parser.detectCharset(tempFile)
            assertTrue(
                "检测到的编码应为 GBK 系列，实际: ${detectedCharset.name()}",
                detectedCharset.name() in listOf("GBK", "GB18030", "GB2312"),
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun contentWithoutChapters_isPreservedAsSingleChapter() = runTest {
        val tempFile = File.createTempFile("nochap", ".txt")
        try {
            tempFile.writeText("这是一段没有章节标题的连续文本内容。" * 100)
            val chapters = parser.parseChapterIndex(tempFile)
            assertEquals("无章节时应生成单个整本章", 1, chapters.size)
            assertEquals("单章应覆盖整个文件", tempFile.length(), chapters[0].byteEnd)
        } finally {
            tempFile.delete()
        }
    }
}

private operator fun String.times(n: Int): String = repeat(n)
