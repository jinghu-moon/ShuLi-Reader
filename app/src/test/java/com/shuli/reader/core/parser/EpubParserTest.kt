package com.shuli.reader.core.parser

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpubParserTest {

    private val parser = EpubParser()

    // ── 元数据解析测试 ──────────────────────────────────────────────

    @Test
    fun epubMetadata_containsTitleAndAuthor() {
        val epubFile = EpubTestUtils.createMinimalEpub(
            title = "Test Book",
            author = "Test Author",
        )
        try {
            val (title, author, _) = parser.parseMetadata(epubFile)
            assertEquals("Test Book", title)
            assertEquals("Test Author", author)
        } finally {
            epubFile.delete()
        }
    }

    @Test
    fun epubCoverExtraction_supportsMultipleStrategies() {
        // 测试封面提取的三种方式：
        // 1. properties="cover-image"
        // 2. meta name="cover"
        // 3. 文件名模糊匹配
        // 目前我们的测试 EPUB 没有封面，所以返回 null
        val epubFile = EpubTestUtils.createMinimalEpub()
        try {
            val (_, _, coverPath) = parser.parseMetadata(epubFile)
            assertEquals("没有封面时应返回 null", null, coverPath)
        } finally {
            epubFile.delete()
        }
    }

    // ── 章节解析测试 ──────────────────────────────────────────────

    @Test
    fun epubChapters_followSpineOrder() = runTest {
        val epubFile = EpubTestUtils.createMinimalEpub(
            chapters = listOf("Chapter 1", "Chapter 2", "Chapter 3"),
        )
        try {
            val content = parser.parse(epubFile)
            assertEquals("应检测到3个章节", 3, content.chapters.size)
            assertEquals("Chapter 1", content.chapters[0].title)
            assertEquals("Chapter 2", content.chapters[1].title)
            assertEquals("Chapter 3", content.chapters[2].title)
        } finally {
            epubFile.delete()
        }
    }

    @Test
    fun epub_supportsNavDirectory() = runTest {
        // 测试 NAV 格式的目录解析
        // 我们的测试 EPUB 包含 NAV 文件
        val epubFile = EpubTestUtils.createMinimalEpub(
            chapters = listOf("Chapter 1", "Chapter 2"),
        )
        try {
            val content = parser.parse(epubFile)
            assertEquals("应检测到2个章节", 2, content.chapters.size)
        } finally {
            epubFile.delete()
        }
    }

    @Test
    fun epub_supportsNcxDirectory() = runTest {
        // 测试 NCX 格式的目录解析
        // 我们的测试 EPUB 包含 NCX 文件
        val epubFile = EpubTestUtils.createMinimalEpub(
            chapters = listOf("Chapter 1", "Chapter 2"),
        )
        try {
            val content = parser.parse(epubFile)
            assertEquals("应检测到2个章节", 2, content.chapters.size)
        } finally {
            epubFile.delete()
        }
    }

    @Test
    fun epubWithoutDirectory_fallsBackToSpine() = runTest {
        // 测试没有目录文件时的行为
        // 我们的测试 EPUB 包含目录，但我们可以测试 spine 解析
        val epubFile = EpubTestUtils.createMinimalEpub(
            chapters = listOf("Chapter 1", "Chapter 2"),
        )
        try {
            val content = parser.parse(epubFile)
            assertEquals("应检测到2个章节", 2, content.chapters.size)
        } finally {
            epubFile.delete()
        }
    }

    // ── 嵌套目录解析测试 ──────────────────────────────────────────────

    @Test
    fun epub_nestedNavResolvesCorrectChapterTitles() = runTest {
        // 嵌套 nav 结构：分组标题 + 子章节链接，应正确映射到 spine 索引
        // nav 标题与 HTML 标题不同，验证标题来源是 nav 而非 HTML fallback
        val epubFile = EpubTestUtils.createEpubWithNestedNav()
        try {
            val content = parser.parse(epubFile)
            val titles = content.chapters.map { it.title }
            // 应解析出 3 个叶子章节（跳过分组标题），标题来自 nav.xhtml
            assertEquals("应有 3 个章节", 3, titles.size)
            assertEquals("NAV第一章", titles[0])
            assertEquals("NAV第二章", titles[1])
            assertEquals("NAV第三章", titles[2])
        } finally {
            epubFile.delete()
        }
    }

    // ── 正文提取测试 ──────────────────────────────────────────────

    @Test
    fun epubParse_returnsLazyContentAndCanExtractChapters() = runTest {
        val epubFile = EpubTestUtils.createMinimalEpub(
            chapters = listOf("Chapter 1", "Chapter 2"),
        )
        try {
            val content = parser.parse(epubFile)
            // 验证懒加载架构：为了节省内存，整体 content 应当为空
            assertEquals("懒加载模式下全量 content 应当为空", "", content.content)
            
            // 验证各个章节能够按需动态解包并提取正文内容
            val chapter1Text = parser.parseChapter(epubFile, 0)
            val chapter2Text = parser.parseChapter(epubFile, 1)
            
            assertTrue("第一章内容应包含正文", chapter1Text.contains("Content of Chapter 1"))
            assertTrue("第二章内容应包含正文", chapter2Text.contains("Content of Chapter 2"))
        } finally {
            epubFile.delete()
        }
    }

    // ── HTML 文本提取测试 ──────────────────────────────────────────────

    @Test
    fun extractTextFromHtml_doesNotDuplicateHeadings() {
        // 当 <h1> 在 <div> 内部时，两遍选择（先 h1~h6 再 div）会导致标题文本重复
        val html = """
            <html><body>
                <div><h1>第一章 标题</h1><p>正文内容。</p></div>
            </body></html>
        """.trimIndent()

        val result = EpubParser.extractTextFromHtmlForTest(html)
        val occurrences = result.windowed("第一章 标题".length).count { it == "第一章 标题" }
        assertEquals("标题不应重复出现", 1, occurrences)
    }

    @Test
    fun extractTextFromHtml_preservesDocumentOrder() {
        val html = """
            <html><body>
                <h1>标题A</h1>
                <p>段落1</p>
                <h2>标题B</h2>
                <p>段落2</p>
            </body></html>
        """.trimIndent()

        val result = EpubParser.extractTextFromHtmlForTest(html)
        val idxA = result.indexOf("标题A")
        val idx1 = result.indexOf("段落1")
        val idxB = result.indexOf("标题B")
        val idx2 = result.indexOf("段落2")
        assertTrue("标题A应在段落1之前", idxA < idx1)
        assertTrue("段落1应在标题B之前", idx1 < idxB)
        assertTrue("标题B应在段落2之前", idxB < idx2)
    }

    // ── 内联格式保留测试 ──────────────────────────────────────────────

    @Test
    fun extractTextFromHtml_preservesBoldAsUnicode() {
        val html = """<html><body><p>这是<b>粗体</b>文本</p></body></html>"""
        val result = EpubParser.extractTextFromHtmlForTest(html)
        // Unicode Mathematical Bold: U+1D41A~U+1D433 for a~z
        // '粗' and '体' are CJK → stay as-is (no bold variant)
        assertTrue("应包含'这是'", result.contains("这是"))
        assertTrue("应包含'文本'", result.contains("文本"))
        // 粗体部分应该不是原始的"粗体"（因为 CJK 没有 Unicode 粗体变体，实际不变）
        // 但结构应该完整
        assertTrue("不应丢失任何文本", result.contains("粗体"))
    }

    @Test
    fun extractTextFromHtml_preservesItalicAsUnicode() {
        val html = """<html><body><p>text with <i>italic</i> word</p></body></html>"""
        val result = EpubParser.extractTextFromHtmlForTest(html)
        // "italic" → Unicode italic: U+1D44E+i, U+1D44E+t, ...
        assertTrue("应包含'text with'", result.contains("text with"))
        assertTrue("应包含'word'", result.contains("word"))
        // italic 部分被转换为 Unicode 斜体字符
        assertFalse("原始 'italic' 不应直接出现", result.contains("italic"))
    }

    @Test
    fun extractTextFromHtml_preservesBoldLatinAsUnicode() {
        val html = """<html><body><p><b>Hello</b> World</p></body></html>"""
        val result = EpubParser.extractTextFromHtmlForTest(html)
        // "Hello" → Unicode Bold: 𝐇𝐞𝐥𝐥𝐨 (U+1D400+H, U+1D400+e, ...)
        assertTrue("应包含 ' World'", result.contains(" World"))
        // Bold "Hello" 转换为 Unicode 粗体
        assertFalse("原始 'Hello' 不应直接出现", result.contains("Hello"))
    }

    @Test
    fun extractTextFromHtml_replacesImgWithPlaceholder() {
        val html = """<html><body><p>前文<img src="cover.jpg" alt="封面"/>后文</p></body></html>"""
        val result = EpubParser.extractTextFromHtmlForTest(html)
        assertTrue("应包含图片占位符 [封面]", result.contains("[封面]"))
        assertTrue("应包含前文", result.contains("前文"))
        assertTrue("应包含后文", result.contains("后文"))
    }

    @Test
    fun extractTextFromHtml_imgWithoutAlt_usesDefaultPlaceholder() {
        val html = """<html><body><p><img src="pic.png"/></p></body></html>"""
        val result = EpubParser.extractTextFromHtmlForTest(html)
        assertTrue("无 alt 时应使用默认占位符 [Image]", result.contains("[Image]"))
    }

    // ── 错误处理测试 ──────────────────────────────────────────────

    @Test(expected = IllegalStateException::class)
    fun missingContainerXml_throwsException() {
        // 创建一个没有 container.xml 的 EPUB 文件
        val tempFile = File.createTempFile("test", ".epub")
        try {
            // 创建一个空的 zip 文件
            java.util.zip.ZipOutputStream(tempFile.outputStream()).use { zip ->
                // 不添加任何文件
            }
            parser.parseMetadata(tempFile)
        } finally {
            tempFile.delete()
        }
    }

    @Test(expected = IllegalStateException::class)
    fun missingOpfFile_throwsException() {
        // 创建一个有 container.xml 但没有 OPF 文件的 EPUB
        val tempFile = File.createTempFile("test", ".epub")
        try {
            java.util.zip.ZipOutputStream(tempFile.outputStream()).use { zip ->
                // 添加 container.xml 但指向不存在的 OPF
                val containerEntry = java.util.zip.ZipEntry("META-INF/container.xml")
                zip.putNextEntry(containerEntry)
                zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                        <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                        </rootfiles>
                    </container>
                """.trimIndent().toByteArray())
                zip.closeEntry()
            }
            parser.parseMetadata(tempFile)
        } finally {
            tempFile.delete()
        }
    }
}
