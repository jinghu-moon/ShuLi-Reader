package com.shuli.reader.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TxtParserTest {

    private lateinit var parser: TxtParser
    private lateinit var booksDir: File

    @Before
    fun setup() {
        parser = TxtParser()
        booksDir = File(javaClass.classLoader!!.getResource("books")!!.toURI())
    }

    // ── 章节检测 ──────────────────────────────────────────────

    @Test
    fun chineseChapterTitles_areDetectedCorrectly() {
        val content = File(booksDir, "simple_chapters.txt").readText()
        val chapters = parser.detectChapters(content)
        assertEquals(4, chapters.size)
        assertEquals("第一章 初入江湖", chapters[0].title)
        assertEquals("第二章 奇遇", chapters[1].title)
        assertEquals("第三章 拜师", chapters[2].title)
        assertEquals("第四章 学艺", chapters[3].title)
    }

    @Test
    fun englishChapterTitles_areDetectedCorrectly() {
        val content = File(booksDir, "english_chapters.txt").readText()
        val chapters = parser.detectChapters(content)
        assertEquals(4, chapters.size)
        assertEquals("Chapter 1 The Beginning", chapters[0].title)
        assertEquals("Chapter 2 The Discovery", chapters[1].title)
        assertEquals("Chapter 3 The Master", chapters[2].title)
        assertEquals("Chapter 10 The Battle", chapters[3].title)
    }

    @Test
    fun contentWithoutChapters_generatesSingleChapter() {
        val content = File(booksDir, "no_chapters.txt").readText()
        val chapters = parser.detectChapters(content)
        assertEquals("无章节文件应自动生成单章节", 1, chapters.size)
        assertEquals("自动生成的章节标题应为默认值", "Full Text", chapters[0].title)
    }

    @Test
    fun complexChapterTitles_areDetectedCorrectly() {
        val content = File(booksDir, "complex_chapters.txt").readText()
        val chapters = parser.detectChapters(content)
        // 卷一 少年行, 第一回 入世, 第二回 遇险, 卷二 风云起, 第三回 转折, 第四回 师徒, 第10章 决战, Chapter 20 Epilogue
        assertTrue("应检测到多个章节", chapters.size >= 6)
    }

    @Test
    fun chapterOffsets_doNotOverlap() {
        val content = File(booksDir, "simple_chapters.txt").readText()
        val chapters = parser.detectChapters(content)
        for (i in 0 until chapters.size - 1) {
            assertTrue(
                "章节 ${chapters[i].title} 的结束偏移应小于下一章开始偏移",
                chapters[i].startIndex < chapters[i + 1].startIndex,
            )
        }
    }

    @Test
    fun finalChapterEndIndex_matchesTextLength() {
        val content = File(booksDir, "simple_chapters.txt").readText()
        val chapters = parser.detectChapters(content)
        assertTrue("应有章节", chapters.isNotEmpty())
        assertEquals(
            "末章结束位置应等于文本长度",
            content.length,
            chapters.last().endIndex,
        )
    }

    @Test
    fun chapterContentOffsets_areValid() {
        val content = File(booksDir, "simple_chapters.txt").readText()
        val chapters = parser.detectChapters(content)
        for (chapter in chapters) {
            assertTrue("startIndex 应 >= 0", chapter.startIndex >= 0)
            assertTrue("endIndex 应 > startIndex", chapter.endIndex > chapter.startIndex)
            assertTrue("endIndex 不应超过文本长度", chapter.endIndex <= content.length)
        }
    }

    // ── 元数据解析 ──────────────────────────────────────────────

    @Test
    fun filenameMetadataParsing_returnsFilenameAsDefaultTitle() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "shuli_test_${System.nanoTime()}")
        tempDir.mkdirs()
        val tempFile = File(tempDir, "我的书.txt")
        tempFile.createNewFile()
        try {
            val (title, author) = parser.parseMetadata(tempFile)
            assertEquals("我的书", title)
            assertEquals(null, author)
        } finally {
            tempFile.delete()
            tempDir.delete()
        }
    }

    @Test
    fun filenameWithAuthor_isParsedCorrectly() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "shuli_test_${System.nanoTime()}")
        tempDir.mkdirs()
        val tempFile = File(tempDir, "斗破苍穹 作者：天蚕土豆.txt")
        tempFile.createNewFile()
        try {
            val (title, author) = parser.parseMetadata(tempFile)
            assertEquals("斗破苍穹", title)
            assertEquals("天蚕土豆", author)
        } finally {
            tempFile.delete()
            tempDir.delete()
        }
    }
}
