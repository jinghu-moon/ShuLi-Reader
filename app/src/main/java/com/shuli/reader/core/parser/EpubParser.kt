package com.shuli.reader.core.parser

import com.shuli.reader.core.database.entity.BookChapterEntity
import com.shuli.reader.core.parser.epub.EpubChapterExtractor
import com.shuli.reader.core.parser.epub.EpubStructureParser
import com.shuli.reader.core.parser.html.HtmlTextExtractor
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.util.zip.ZipFile

/**
 * EPUB 解析器聚合入口。
 *
 * 内部职责委托给：
 * - [EpubStructureParser]：OPF/NCX/NAV/Spine 结构解析
 * - [EpubChapterExtractor]：章节内容提取
 * - [HtmlTextExtractor]：HTML 文本提取
 */
class EpubParser(
    imagePlaceholder: String = "Image",
) {
    var imagePlaceholder: String = imagePlaceholder

    /** 缓存的 OPF 元数据，避免 parseChapter() 重复解析 XML */
    private var cachedOpf: EpubStructureParser.OpfMetadata? = null

    fun parseMetadata(file: File): Triple<String, String?, String?> {
        return ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val opfPath = EpubStructureParser.findOpfPath(zip, entries)

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
     */
    suspend fun parse(file: File): BookContent = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val opfPath = EpubStructureParser.findOpfPath(zip, entries)
            val opf = EpubStructureParser.parseOpf(zip, opfPath)
            cachedOpf = opf

            val title = Jsoup.parse(
                zip.getInputStream(zip.getEntry(opfPath)).bufferedReader().readText(),
                "",
                org.jsoup.parser.Parser.xmlParser(),
            ).select("metadata title").first()?.text() ?: "Unknown"

            val author = Jsoup.parse(
                zip.getInputStream(zip.getEntry(opfPath)).bufferedReader().readText(),
                "",
                org.jsoup.parser.Parser.xmlParser(),
            ).select("metadata creator").first()?.text()

            val navTitles = EpubStructureParser.parseNavTitles(zip, opf.opfDir, opf.manifestItems, opf.spineItems, entries)
            val chapters = EpubChapterExtractor.parseChapterList(zip, opf.opfDir, opf.spineItems, opf.manifestItems, navTitles)

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
     */
    suspend fun parseChapterIndex(file: File): List<BookChapterEntity> = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val opfPath = EpubStructureParser.findOpfPath(zip, entries)
            val opf = EpubStructureParser.parseOpf(zip, opfPath)
            cachedOpf = opf

            val navTitles = EpubStructureParser.parseNavTitles(zip, opf.opfDir, opf.manifestItems, opf.spineItems, entries)
            EpubChapterExtractor.parseChapterIndex(zip, opf.opfDir, opf.spineItems, opf.manifestItems, navTitles)
        }
    }

    /**
     * 完整解析：提取元数据 + 全部章节正文。
     */
    suspend fun parseWithContent(file: File): BookContent = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val opfPath = EpubStructureParser.findOpfPath(zip, entries)
            val opf = EpubStructureParser.parseOpf(zip, opfPath)
            cachedOpf = opf

            val doc = Jsoup.parse(
                zip.getInputStream(zip.getEntry(opfPath)).bufferedReader().readText(),
                "",
                org.jsoup.parser.Parser.xmlParser(),
            )
            val title = doc.select("metadata title").first()?.text() ?: "Unknown"
            val author = doc.select("metadata creator").first()?.text()

            val chaptersWithContent = EpubChapterExtractor.parseChaptersWithContent(
                zip, opf.opfDir, opf.spineItems, opf.manifestItems, imagePlaceholder,
            )
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
     * 根据章节索引，动态解析并提取单个章节的正文文本。
     */
    fun parseChapter(file: File, spineIndex: Int): String {
        return try {
            ZipFile(file).use { zip ->
                val opf = cachedOpf
                    ?: run {
                        val entries = zip.entries().toList()
                        val opfPath = EpubStructureParser.findOpfPath(zip, entries)
                        EpubStructureParser.parseOpf(zip, opfPath).also { cachedOpf = it }
                    }

                EpubChapterExtractor.parseChapter(
                    zip, opf.opfDir, opf.spineItems, opf.manifestItems, spineIndex, imagePlaceholder,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("EpubParser", "Failed to parse chapter $spineIndex from epub file: ${file.absolutePath}", e)
            ""
        }
    }

    /** 测试辅助：暴露 extractText 供单元测试验证 */
    companion object {
        @JvmStatic
        fun extractTextFromHtmlForTest(html: String): String = HtmlTextExtractor.extractText(html)
    }
}
