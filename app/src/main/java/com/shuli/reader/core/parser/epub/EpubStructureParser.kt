package com.shuli.reader.core.parser.epub

import com.shuli.reader.core.database.entity.BookChapterEntity
import com.shuli.reader.core.parser.model.Chapter
import org.jsoup.Jsoup
import java.io.File
import java.util.zip.ZipFile

/**
 * EPUB 结构解析：OPF 元数据、NCX/NAV 目录、Spine 序列、章节目录。
 *
 * 从 EpubParser 拆出，独立处理 EPUB 容器结构，不涉及正文内容提取。
 */
class EpubStructureParser {

    /** 缓存的 OPF 元数据，避免 parseChapter() 重复解析 XML */
    data class CachedOpfMetadata(
        val opfPath: String,
        val opfDir: String,
        val spineItems: List<String>,
        val manifestItems: Map<String, String>,
    )

    var cachedOpf: CachedOpfMetadata? = null

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

            var coverHref = doc.select("manifest item[properties=cover-image]").first()?.attr("href")
            if (coverHref.isNullOrBlank()) {
                val coverId = doc.select("metadata meta[name=cover]").first()?.attr("content")
                if (!coverId.isNullOrBlank()) {
                    coverHref = doc.select("manifest item[id=$coverId]").first()?.attr("href")
                }
            }
            if (coverHref.isNullOrBlank()) {
                coverHref = doc.select("manifest item").firstOrNull {
                    val href = it.attr("href").lowercase()
                    val mediaType = it.attr("media-type").lowercase()
                    mediaType.startsWith("image/") && (href.contains("cover") || href.contains("thumb"))
                }?.attr("href")
            }

            val opfDir = opfPath.substringBeforeLast("/")
            val fullCoverPath = if (coverHref != null) {
                if (opfDir.isNotEmpty() && !coverHref.startsWith("/")) "$opfDir/$coverHref" else coverHref
            } else null

            Triple(title, author, fullCoverPath)
        }
    }

    fun findOpfPath(zip: ZipFile, entries: List<java.util.zip.ZipEntry>): String {
        val containerEntry = entries.find { it.name == "META-INF/container.xml" }
            ?: throw IllegalStateException("Invalid EPUB: missing container.xml")
        val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
        val doc = Jsoup.parse(containerXml, "", org.jsoup.parser.Parser.xmlParser())
        return doc.select("rootfile").first()?.attr("full-path")
            ?: throw IllegalStateException("Invalid EPUB: missing rootfile")
    }

    fun parseOpfMetadata(zip: ZipFile, opfPath: String): OpfData {
        val opfEntry = zip.getEntry(opfPath)
            ?: throw IllegalStateException("Invalid EPUB: missing OPF file")
        val opfDir = opfPath.substringBeforeLast("/")
        val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
        val doc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

        val title = doc.select("metadata title").first()?.text() ?: "Unknown"
        val author = doc.select("metadata creator").first()?.text()
        val spineItems = doc.select("spine itemref").map { it.attr("idref") }
        val manifestItems = doc.select("manifest item").associate { it.attr("id") to it.attr("href") }

        cachedOpf = CachedOpfMetadata(opfPath, opfDir, spineItems, manifestItems)

        return OpfData(title, author, opfDir, spineItems, manifestItems)
    }

    /** 轻量扫描章节目录：只提取标题和 spine 索引，不读取正文 */
    fun parseChapterList(
        zip: ZipFile,
        opfDir: String,
        spineItems: List<String>,
        manifestItems: Map<String, String>,
        navTitles: Map<Int, String> = emptyMap(),
        extractTitle: (String) -> String?,
    ): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        for ((spineIdx, spineItem) in spineItems.withIndex()) {
            val href = manifestItems[spineItem] ?: continue
            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
            if (fullPath.contains("..")) continue

            val title = navTitles[spineIdx]
                ?: run {
                    val entry = zip.getEntry(fullPath) ?: return@run null
                    val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
                    extractTitle(htmlContent)
                }
                ?: "Chapter ${chapters.size + 1}"

            chapters.add(Chapter(title = title, spineIndex = spineIdx))
        }
        return chapters
    }

    /** 构建章节目录索引（BookChapterEntity 列表） */
    fun parseChapterIndex(
        zip: ZipFile,
        opfDir: String,
        spineItems: List<String>,
        manifestItems: Map<String, String>,
        navTitles: Map<Int, String>,
        extractTitle: (String) -> String?,
    ): List<BookChapterEntity> {
        val chapters = mutableListOf<BookChapterEntity>()
        for ((spineIdx, spineItem) in spineItems.withIndex()) {
            val href = manifestItems[spineItem] ?: continue
            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
            if (fullPath.contains("..")) continue

            val title = navTitles[spineIdx]
                ?: run {
                    val entry = zip.getEntry(fullPath) ?: return@run null
                    val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
                    extractTitle(htmlContent)
                }
                ?: "Chapter ${chapters.size + 1}"

            chapters.add(
                BookChapterEntity(
                    bookId = 0,
                    chapterIndex = chapters.size,
                    title = title,
                    spineIndex = spineIdx,
                )
            )
        }
        return chapters
    }

    // ── NAV / NCX 目录解析 ──────────────────────────────────

    fun parseNavTitles(
        zip: ZipFile,
        opfDir: String,
        manifestItems: Map<String, String>,
        spineItems: List<String>,
        entries: List<java.util.zip.ZipEntry>,
    ): Map<Int, String> {
        // EPUB3: nav.xhtml
        val navHref = manifestItems.values.find { it.endsWith("nav.xhtml") || it.endsWith("nav.html") }
        if (navHref != null) {
            val navPath = if (opfDir.isNotEmpty()) "$opfDir/$navHref" else navHref
            val navEntry = entries.find { it.name == navPath }
            if (navEntry != null) {
                runCatching {
                    val navContent = zip.getInputStream(navEntry).bufferedReader().readText()
                    return parseNavXhtml(navContent, opfDir, manifestItems, spineItems)
                }
            }
        }

        // EPUB2: toc.ncx
        val ncxId = manifestItems.entries.find { it.value.endsWith("toc.ncx") }?.key
        if (ncxId != null) {
            val ncxHref = manifestItems[ncxId] ?: return emptyMap()
            val ncxPath = if (opfDir.isNotEmpty()) "$opfDir/$ncxHref" else ncxHref
            val ncxEntry = entries.find { it.name == ncxPath }
            if (ncxEntry != null) {
                runCatching {
                    val ncxContent = zip.getInputStream(ncxEntry).bufferedReader().readText()
                    return parseTocNcx(ncxContent, opfDir, manifestItems, spineItems)
                }
            }
        }

        return emptyMap()
    }

    private fun parseNavXhtml(
        navContent: String,
        opfDir: String,
        manifestItems: Map<String, String>,
        spineItems: List<String>,
    ): Map<Int, String> {
        val doc = Jsoup.parse(navContent, "", org.jsoup.parser.Parser.xmlParser())
        val navToc = doc.select("nav[epub:type=toc], nav[*|type=toc], nav").first()
            ?: return emptyMap()
        val hrefToSpine = buildHrefToSpineIndex(manifestItems, spineItems)
        val result = mutableMapOf<Int, String>()
        collectNavLinks(navToc, hrefToSpine, result)
        return result
    }

    private fun collectNavLinks(
        element: org.jsoup.nodes.Element,
        hrefToSpine: Map<String, Int>,
        result: MutableMap<Int, String>,
    ) {
        for (child in element.children()) {
            val link = child.selectFirst("a[href]")
            if (link != null) {
                val href = link.attr("href").substringBefore("#").substringBefore("?")
                val title = link.text().trim()
                if (href.isNotBlank() && title.isNotBlank()) {
                    hrefToSpine[href]?.let { spineIdx -> result[spineIdx] = title }
                }
            }
            collectNavLinks(child, hrefToSpine, result)
        }
    }

    private fun parseTocNcx(
        ncxContent: String,
        opfDir: String,
        manifestItems: Map<String, String>,
        spineItems: List<String>,
    ): Map<Int, String> {
        val doc = Jsoup.parse(ncxContent, "", org.jsoup.parser.Parser.xmlParser())
        val navMap = doc.select("navMap").first() ?: return emptyMap()
        val hrefToSpine = buildHrefToSpineIndex(manifestItems, spineItems)
        val result = mutableMapOf<Int, String>()
        collectNcxNavPoints(navMap, hrefToSpine, result)
        return result
    }

    private fun collectNcxNavPoints(
        parent: org.jsoup.nodes.Element,
        hrefToSpine: Map<String, Int>,
        result: MutableMap<Int, String>,
    ) {
        for (navPoint in parent.children().filter { it.tagName().equals("navPoint", ignoreCase = true) }) {
            val title = navPoint.selectFirst("navLabel > text")?.text()?.trim()
            val src = navPoint.selectFirst("content")?.attr("src")
                ?.substringBefore("#")?.substringBefore("?")
            if (!title.isNullOrBlank() && !src.isNullOrBlank()) {
                hrefToSpine[src]?.let { spineIdx -> result[spineIdx] = title }
            }
            collectNcxNavPoints(navPoint, hrefToSpine, result)
        }
    }

    private fun buildHrefToSpineIndex(
        manifestItems: Map<String, String>,
        spineItems: List<String>,
    ): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for ((spineIdx, idref) in spineItems.withIndex()) {
            val href = manifestItems[idref] ?: continue
            result[href] = spineIdx
        }
        return result
    }

    data class OpfData(
        val title: String,
        val author: String?,
        val opfDir: String,
        val spineItems: List<String>,
        val manifestItems: Map<String, String>,
    )
}
