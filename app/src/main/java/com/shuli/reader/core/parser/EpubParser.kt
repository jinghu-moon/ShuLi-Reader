package com.shuli.reader.core.parser

import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

class EpubParser {

    fun parseMetadata(file: File): Triple<String, String?, String?> {
        return ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val opfPath = findOpfPath(zip, entries)
            
            val opfEntry = zip.getEntry(opfPath)
                ?: throw IllegalStateException("Invalid EPUB: missing OPF file")

            val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
            val doc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

            val title = doc.select("metadata dc|title, metadata title").first()?.text() ?: "Unknown"
            val author = doc.select("metadata dc|creator, metadata creator").first()?.text()
            
            // 提取封面相对路径
            // 方式 1: 声明属性为 properties="cover-image"
            var coverHref = doc.select("manifest item[properties=cover-image]").first()?.attr("href")
            
            // 方式 2: metadata 里有 name="cover" 属性
            if (coverHref.isNullOrBlank()) {
                val coverId = doc.select("metadata meta[name=cover]").first()?.attr("content")
                if (!coverId.isNullOrBlank()) {
                    coverHref = doc.select("manifest item[id=$coverId]").first()?.attr("href")
                }
            }
            
            // 方式 3: 模糊名称匹配 (媒体类型为图片，且 href 包含 cover)
            if (coverHref.isNullOrBlank()) {
                coverHref = doc.select("manifest item").firstOrNull {
                    val href = it.attr("href").lowercase()
                    val mediaType = it.attr("media-type").lowercase()
                    mediaType.startsWith("image/") && (href.contains("cover") || href.contains("thumb"))
                }?.attr("href")
            }
            
            val opfDir = opfPath.substringBeforeLast("/")
            val fullCoverPath = if (coverHref != null) {
                if (opfDir.isNotEmpty() && !coverHref.startsWith("/")) {
                    "$opfDir/$coverHref"
                } else {
                    coverHref
                }
            } else {
                null
            }

            Triple(title, author, fullCoverPath)
        }
    }

    suspend fun parse(file: File): BookContent = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()

            val opfPath = findOpfPath(zip, entries)
            val metadata = parseMetadata(zip, opfPath)
            val chaptersWithContent = parseChaptersWithContent(zip, entries, opfPath)

            val chapters = chaptersWithContent.map { it.first }
            val totalLength = chaptersWithContent.sumOf { it.second.length.toLong() }

            BookContent(
                title = metadata.first,
                author = metadata.second,
                encoding = "UTF-8",
                totalLength = totalLength,
                chapters = chapters,
                content = "",
            )
        }
    }

    /**
     * 根据章节索引，动态解析并提取单个章节的正文文本
     */
    fun parseChapter(file: File, spineIndex: Int): String {
        return try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().toList()
                val opfPath = findOpfPath(zip, entries)
                val opfEntry = zip.getEntry(opfPath) ?: return ""
                val opfDir = opfPath.substringBeforeLast("/")

                val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
                val doc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

                val spineItems = doc.select("spine itemref").map { it.attr("idref") }
                val manifestItems = doc.select("manifest item").associate {
                    it.attr("id") to it.attr("href")
                }

                val idref = spineItems.getOrNull(spineIndex) ?: return ""
                val href = manifestItems[idref] ?: return ""
                val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href

                // 防止 Zip Slip 路径穿越
                if (fullPath.contains("..")) return ""

                val entry = entries.find { it.name == fullPath } ?: return ""
                val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
                extractTextFromHtml(htmlContent)
            }
        } catch (e: Exception) {
            android.util.Log.e("EpubParser", "Failed to parse chapter $spineIndex from epub file: ${file.absolutePath}", e)
            ""
        }
    }

    /**
     * 解析章节，返回章节和对应的正文内容
     */
    private fun parseChaptersWithContent(
        zip: ZipFile,
        entries: List<java.util.zip.ZipEntry>,
        opfPath: String,
    ): List<Pair<Chapter, String>> {
        val opfEntry = zip.getEntry(opfPath) ?: return emptyList()
        val opfDir = opfPath.substringBeforeLast("/")

        val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
        val doc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

        val spineItems = doc.select("spine itemref").map { it.attr("idref") }
        val manifestItems = doc.select("manifest item").associate {
            it.attr("id") to it.attr("href")
        }

        val result = mutableListOf<Pair<Chapter, String>>()
        var currentPosition = 0

        for (spineItem in spineItems) {
            val href = manifestItems[spineItem] ?: continue
            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href

            // 防止 Zip Slip 路径穿越
            if (fullPath.contains("..")) continue

            val entry = entries.find { it.name == fullPath } ?: continue
            val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
            val textContent = extractTextFromHtml(htmlContent)

            if (textContent.isNotBlank()) {
                val title = extractChapterTitle(htmlContent) ?: "Chapter ${result.size + 1}"
                val chapter = Chapter(
                    title = title,
                    startIndex = currentPosition,
                    endIndex = currentPosition + textContent.length,
                )
                result.add(chapter to textContent)
                currentPosition += textContent.length + 2
            }
        }

        return result
    }

    private fun findOpfPath(zip: ZipFile, entries: List<java.util.zip.ZipEntry>): String {
        val containerEntry = entries.find { it.name == "META-INF/container.xml" }
            ?: throw IllegalStateException("Invalid EPUB: missing container.xml")

        val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
        val doc = Jsoup.parse(containerXml, "", org.jsoup.parser.Parser.xmlParser())
        val rootFilePath = doc.select("rootfile").first()?.attr("full-path")
            ?: throw IllegalStateException("Invalid EPUB: missing rootfile")

        return rootFilePath
    }

    private fun parseMetadata(zip: ZipFile, opfPath: String): Pair<String, String?> {
        val opfEntry = zip.getEntry(opfPath)
            ?: throw IllegalStateException("Invalid EPUB: missing OPF file")

        val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
        val doc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

        val title = doc.select("metadata title").first()?.text() ?: "Unknown"
        val author = doc.select("metadata creator").first()?.text()

        return title to author
    }

    private fun parseChapters(
        zip: ZipFile,
        entries: List<java.util.zip.ZipEntry>,
        opfPath: String,
    ): List<Chapter> {
        val opfEntry = zip.getEntry(opfPath) ?: return emptyList()
        val opfDir = opfPath.substringBeforeLast("/")

        val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
        val doc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

        val spineItems = doc.select("spine itemref").map { it.attr("idref") }
        val manifestItems = doc.select("manifest item").associate {
            it.attr("id") to it.attr("href")
        }

        val chapters = mutableListOf<Chapter>()
        var currentPosition = 0

        for (spineItem in spineItems) {
            val href = manifestItems[spineItem] ?: continue
            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href

            val entry = entries.find { it.name == fullPath } ?: continue
            val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
            val textContent = extractTextFromHtml(htmlContent)

            if (textContent.isNotBlank()) {
                val title = extractChapterTitle(htmlContent) ?: "Chapter ${chapters.size + 1}"
                chapters.add(
                    Chapter(
                        title = title,
                        startIndex = currentPosition,
                        endIndex = currentPosition + textContent.length,
                    )
                )
                currentPosition += textContent.length + 2
            }
        }

        return chapters
    }

    /**
     * 从 HTML 中提取纯文本，保留段落结构
     */
    private fun extractTextFromHtml(html: String): String {
        val doc = Jsoup.parse(html)

        // 移除脚本和样式
        doc.select("script, style, meta, link, head").remove()

        val body = doc.body() ?: return ""

        // 提取段落和标题
        val paragraphs = mutableListOf<String>()

        // 处理标题
        for (heading in body.select("h1, h2, h3, h4, h5, h6")) {
            val text = heading.text().trim()
            if (text.isNotBlank()) {
                paragraphs.add(text)
            }
        }

        // 处理段落
        for (p in body.select("p, div, li, blockquote")) {
            val text = p.text().trim()
            if (text.isNotBlank()) {
                paragraphs.add(text)
            }
        }

        // 如果没有找到段落，使用整个 body 的文本
        if (paragraphs.isEmpty()) {
            val bodyText = body.text().trim()
            if (bodyText.isNotBlank()) {
                paragraphs.add(bodyText)
            }
        }

        return paragraphs.joinToString("\n\n")
    }

    private fun extractChapterTitle(html: String): String? {
        val doc = Jsoup.parse(html)
        return doc.select("h1, h2, h3, title").first()?.text()
    }
}
