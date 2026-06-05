package com.shuli.reader.core.parser.epub

import org.jsoup.Jsoup
import java.util.zip.ZipFile

/**
 * EPUB 结构解析器：解析 OPF、NCX、NAV、Spine 等 EPUB 容器结构。
 */
object EpubStructureParser {

    /**
     * OPF 元数据缓存。
     */
    data class OpfMetadata(
        val opfPath: String,
        val opfDir: String,
        val spineItems: List<String>,
        val manifestItems: Map<String, String>,
    )

    /**
     * 解析 container.xml，定位 OPF 文件路径。
     */
    fun findOpfPath(zip: ZipFile, entries: List<java.util.zip.ZipEntry>): String {
        val containerEntry = entries.find { it.name == "META-INF/container.xml" }
            ?: throw IllegalStateException("Invalid EPUB: missing container.xml")

        val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
        val doc = Jsoup.parse(containerXml, "", org.jsoup.parser.Parser.xmlParser())
        return doc.select("rootfile").first()?.attr("full-path")
            ?: throw IllegalStateException("Invalid EPUB: missing rootfile")
    }

    /**
     * 解析 OPF 文件，提取 spine 和 manifest 信息。
     */
    fun parseOpf(zip: ZipFile, opfPath: String): OpfMetadata {
        val opfEntry = zip.getEntry(opfPath)
            ?: throw IllegalStateException("Invalid EPUB: missing OPF file")
        val opfDir = opfPath.substringBeforeLast("/")
        val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
        val doc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

        val spineItems = doc.select("spine itemref").map { it.attr("idref") }
        val manifestItems = doc.select("manifest item").associate {
            it.attr("id") to it.attr("href")
        }

        return OpfMetadata(opfPath, opfDir, spineItems, manifestItems)
    }

    /**
     * 从 nav.xhtml (EPUB3) 或 toc.ncx (EPUB2) 解析章节标题映射。
     * 返回 spineIndex → title 的映射。
     */
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

    /**
     * 构建 href → spineIndex 反向映射。
     */
    fun buildHrefToSpineIndex(
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

    /**
     * 解析 EPUB3 nav.xhtml 中的目录标题。
     */
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

    /**
     * 解析 EPUB2 toc.ncx 中的目录标题。
     */
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
}
