package com.shuli.reader.core.parser

import com.shuli.reader.core.database.entity.BookChapterEntity
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

class EpubParser {

    /** 缓存的 OPF 元数据，避免 parseChapter() 重复解析 XML */
    private data class CachedOpfMetadata(
        val opfPath: String,
        val opfDir: String,
        val spineItems: List<String>,
        val manifestItems: Map<String, String>,
    )
    private var cachedOpf: CachedOpfMetadata? = null

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

    /**
     * 轻量解析：只提取元数据 + 章节目录（含 spineIndex），不读取章节正文。
     * 正文通过 [parseChapter] 按需加载。首屏速度核心优化点。
     */
    suspend fun parse(file: File): BookContent = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val opfPath = findOpfPath(zip, entries)

            val opfEntry = zip.getEntry(opfPath)
                ?: throw IllegalStateException("Invalid EPUB: missing OPF file")
            val opfDir = opfPath.substringBeforeLast("/")
            val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
            val doc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

            val title = doc.select("metadata title").first()?.text() ?: "Unknown"
            val author = doc.select("metadata creator").first()?.text()
            val spineItems = doc.select("spine itemref").map { it.attr("idref") }
            val manifestItems = doc.select("manifest item").associate {
                it.attr("id") to it.attr("href")
            }

            cachedOpf = CachedOpfMetadata(opfPath, opfDir, spineItems, manifestItems)

            // 轻量模式：只扫描章节标题，不读取正文内容
            val chapters = parseChapterList(zip, opfDir, spineItems, manifestItems)

            BookContent(
                title = title,
                author = author,
                encoding = "UTF-8",
                totalLength = 0L,
                chapters = chapters,
                content = "",
            )
        }
    }

    /**
     * 构建章节目录索引：只提取标题和 spineIndex，不读取正文。
     * 优先从 nav.xhtml / toc.ncx 获取标题（更快），回退到逐章读取 <title>/<h1~h6>。
     */
    suspend fun parseChapterIndex(file: File): List<BookChapterEntity> = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val opfPath = findOpfPath(zip, entries)

            val opfEntry = zip.getEntry(opfPath)
                ?: throw IllegalStateException("Invalid EPUB: missing OPF file")
            val opfDir = opfPath.substringBeforeLast("/")
            val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
            val doc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

            val spineItems = doc.select("spine itemref").map { it.attr("idref") }
            val manifestItems = doc.select("manifest item").associate {
                it.attr("id") to it.attr("href")
            }

            // 缓存 OPF 供后续 parseChapter() 使用
            cachedOpf = CachedOpfMetadata(opfPath, opfDir, spineItems, manifestItems)

            // 优先尝试从 nav.xhtml / toc.ncx 获取章节标题
            val navTitles = parseNavTitles(zip, opfDir, manifestItems, entries)

            val chapters = mutableListOf<BookChapterEntity>()
            for ((spineIdx, spineItem) in spineItems.withIndex()) {
                val href = manifestItems[spineItem] ?: continue
                val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
                if (fullPath.contains("..")) continue

                // 优先使用 nav 标题，回退到读取 HTML
                val title = navTitles[spineIdx]
                    ?: run {
                        val entry = zip.getEntry(fullPath) ?: return@run null
                        val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
                        extractChapterTitle(htmlContent)
                    }
                    ?: "Chapter ${chapters.size + 1}"

                chapters.add(
                    BookChapterEntity(
                        bookId = 0, // 由调用方填充
                        chapterIndex = chapters.size,
                        title = title,
                        spineIndex = spineIdx,
                    )
                )
            }

            chapters
        }
    }

    /**
     * 从 nav.xhtml (EPUB3) 或 toc.ncx (EPUB2) 解析章节标题映射。
     * 返回 spineIndex → title 的映射，失败时返回空 Map（回退到逐章解析）。
     */
    private fun parseNavTitles(
        zip: ZipFile,
        opfDir: String,
        manifestItems: Map<String, String>,
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
                    return parseNavXhtml(navContent, opfDir, manifestItems)
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
                    return parseTocNcx(ncxContent, opfDir, manifestItems)
                }
            }
        }

        return emptyMap()
    }

    /**
     * 解析 EPUB3 nav.xhtml 中的目录标题。
     * 返回 href → title 映射（href 为 manifest 中的相对路径）。
     */
    private fun parseNavXhtml(
        navContent: String,
        opfDir: String,
        manifestItems: Map<String, String>,
    ): Map<Int, String> {
        val doc = Jsoup.parse(navContent, "", org.jsoup.parser.Parser.xmlParser())
        val navToc = doc.select("nav[epub:type=toc], nav[*|type=toc], nav").first()
            ?: return emptyMap()

        // 构建 href → spineIndex 的反向映射
        val hrefToSpine = mutableMapOf<String, Int>()
        for ((id, href) in manifestItems) {
            // 不处理，由调用方构建
        }

        val result = mutableMapOf<Int, String>()
        val links = navToc.select("a[href]")
        // 简化：返回标题列表，由调用方按顺序匹配 spine
        // 由于 nav 中的顺序通常与 spine 一致，按顺序返回
        return links.mapIndexed { idx, link ->
            idx to link.text().trim()
        }.filter { it.second.isNotBlank() }.toMap()
    }

    /**
     * 解析 EPUB2 toc.ncx 中的目录标题。
     */
    private fun parseTocNcx(
        ncxContent: String,
        opfDir: String,
        manifestItems: Map<String, String>,
    ): Map<Int, String> {
        val doc = Jsoup.parse(ncxContent, "", org.jsoup.parser.Parser.xmlParser())
        val navPoints = doc.select("navMap > navPoint")
        if (navPoints.isEmpty()) return emptyMap()

        return navPoints.mapIndexed { idx, navPoint ->
            val title = navPoint.select("navLabel > text").first()?.text()?.trim()
                ?: "Chapter ${idx + 1}"
            idx to title
        }.toMap()
    }

    /**
     * 完整解析：提取元数据 + 全部章节正文。仅用于搜索索引构建等需要全文的场景。
     */
    suspend fun parseWithContent(file: File): BookContent = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val opfPath = findOpfPath(zip, entries)

            val opfEntry = zip.getEntry(opfPath)
                ?: throw IllegalStateException("Invalid EPUB: missing OPF file")
            val opfDir = opfPath.substringBeforeLast("/")
            val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
            val doc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

            val title = doc.select("metadata title").first()?.text() ?: "Unknown"
            val author = doc.select("metadata creator").first()?.text()
            val spineItems = doc.select("spine itemref").map { it.attr("idref") }
            val manifestItems = doc.select("manifest item").associate {
                it.attr("id") to it.attr("href")
            }

            cachedOpf = CachedOpfMetadata(opfPath, opfDir, spineItems, manifestItems)

            val chaptersWithContent = parseChaptersWithContent(zip, opfDir, spineItems, manifestItems)
            val chapters = chaptersWithContent.map { it.first }
            val totalLength = chaptersWithContent.sumOf { it.second.length.toLong() }

            BookContent(
                title = title,
                author = author,
                encoding = "UTF-8",
                totalLength = totalLength,
                chapters = chapters,
                content = "",
            )
        }
    }

    /**
     * 根据章节索引，动态解析并提取单个章节的正文文本
     * 优先使用缓存的 OPF 元数据，避免重复解析 XML
     */
    fun parseChapter(file: File, spineIndex: Int): String {
        return try {
            ZipFile(file).use { zip ->
                val opf = cachedOpf
                val spineItems: List<String>
                val manifestItems: Map<String, String>
                val opfDir: String

                if (opf != null) {
                    // 使用缓存的 OPF 元数据，跳过 XML 解析
                    spineItems = opf.spineItems
                    manifestItems = opf.manifestItems
                    opfDir = opf.opfDir
                } else {
                    // 缓存未命中，回退到完整解析
                    val entries = zip.entries().toList()
                    val opfPath = findOpfPath(zip, entries)
                    val opfEntry = zip.getEntry(opfPath) ?: return ""
                    opfDir = opfPath.substringBeforeLast("/")

                    val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
                    val doc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

                    spineItems = doc.select("spine itemref").map { it.attr("idref") }
                    manifestItems = doc.select("manifest item").associate {
                        it.attr("id") to it.attr("href")
                    }
                }

                val idref = spineItems.getOrNull(spineIndex) ?: return ""
                val href = manifestItems[idref] ?: return ""
                val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href

                // 防止 Zip Slip 路径穿越
                if (fullPath.contains("..")) return ""

                val entry = zip.getEntry(fullPath) ?: return ""
                val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
                extractTextFromHtml(htmlContent)
            }
        } catch (e: Exception) {
            android.util.Log.e("EpubParser", "Failed to parse chapter $spineIndex from epub file: ${file.absolutePath}", e)
            ""
        }
    }

    /**
     * 轻量扫描：只提取章节标题和 spine 索引，不读取正文。
     * 通过读取 HTML 中的 <title> / <h1~h6> 获取标题，正文跳过。
     */
    private fun parseChapterList(
        zip: ZipFile,
        opfDir: String,
        spineItems: List<String>,
        manifestItems: Map<String, String>,
    ): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        for ((spineIdx, spineItem) in spineItems.withIndex()) {
            val href = manifestItems[spineItem] ?: continue
            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
            if (fullPath.contains("..")) continue

            val entry = zip.getEntry(fullPath) ?: continue
            // 只读标题，不提取全文
            val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
            val title = extractChapterTitle(htmlContent) ?: "Chapter ${chapters.size + 1}"
            chapters.add(
                Chapter(
                    title = title,
                    startIndex = 0,
                    endIndex = 0,
                    spineIndex = spineIdx,
                )
            )
        }
        return chapters
    }

    /**
     * 解析章节，返回章节和对应的正文内容
     */
    private fun parseChaptersWithContent(
        zip: ZipFile,
        opfDir: String,
        spineItems: List<String>,
        manifestItems: Map<String, String>,
    ): List<Pair<Chapter, String>> {

        val result = mutableListOf<Pair<Chapter, String>>()
        var currentPosition = 0

        for ((spineIdx, spineItem) in spineItems.withIndex()) {
            val href = manifestItems[spineItem] ?: continue
            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href

            // 防止 Zip Slip 路径穿越
            if (fullPath.contains("..")) continue

            val entry = zip.getEntry(fullPath) ?: continue
            val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
            val textContent = extractTextFromHtml(htmlContent)

            if (textContent.isNotBlank()) {
                val title = extractChapterTitle(htmlContent) ?: "Chapter ${result.size + 1}"
                val chapter = Chapter(
                    title = title,
                    startIndex = currentPosition,
                    endIndex = currentPosition + textContent.length,
                    spineIndex = spineIdx,
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
