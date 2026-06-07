package com.shuli.reader.core.parser

import com.shuli.reader.core.database.entity.BookChapterEntity
import com.shuli.reader.core.parser.epub.EpubChapterExtractor
import com.shuli.reader.core.parser.epub.EpubStructureParser
import com.shuli.reader.core.parser.html.HtmlTextExtractor
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * EPUB 解析器聚合入口。
 *
 * SRP 拆分后，结构解析委托给 [EpubStructureParser]，
 * 章节内容提取委托给 [EpubChapterExtractor]，
 * HTML 文本提取委托给 [HtmlTextExtractor]。
 */
class EpubParser(
    imagePlaceholder: String = "Image",
) {
    private val htmlExtractor = HtmlTextExtractor(imagePlaceholder)
    private val structureParser = EpubStructureParser()
    private val chapterExtractor = EpubChapterExtractor(structureParser, htmlExtractor)

    var imagePlaceholder: String
        get() = htmlExtractor.imagePlaceholder
        set(value) { htmlExtractor.imagePlaceholder = value }

    fun parseMetadata(file: File): Triple<String, String?, String?> {
        return structureParser.parseMetadata(file)
    }

    /** 轻量解析：只提取元数据 + 章节目录（含 spineIndex），不读取章节正文。 */
    suspend fun parse(file: File): BookContent = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val opfPath = structureParser.findOpfPath(zip, entries)
            val opfData = structureParser.parseOpfMetadata(zip, opfPath)

            val navTitles = structureParser.parseNavTitles(
                zip, opfData.opfDir, opfData.manifestItems, opfData.spineItems, entries,
            )
            val chapters = structureParser.parseChapterList(
                zip, opfData.opfDir, opfData.spineItems, opfData.manifestItems, navTitles,
            ) { html -> htmlExtractor.extractChapterTitle(html) }

            BookContent(
                title = opfData.title,
                author = opfData.author,
                encoding = "UTF-8",
                totalLength = 0L,
                chapters = chapters,
                content = "",
            )
        }
    }

    /** 构建章节目录索引：只提取标题和 spineIndex，不读取正文。 */
    suspend fun parseChapterIndex(file: File): List<BookChapterEntity> = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val opfPath = structureParser.findOpfPath(zip, entries)
            val opfData = structureParser.parseOpfMetadata(zip, opfPath)

            val navTitles = structureParser.parseNavTitles(
                zip, opfData.opfDir, opfData.manifestItems, opfData.spineItems, entries,
            )

            structureParser.parseChapterIndex(
                zip, opfData.opfDir, opfData.spineItems, opfData.manifestItems, navTitles,
            ) { html -> htmlExtractor.extractChapterTitle(html) }
        }
    }

    /** 完整解析：提取元数据 + 全部章节正文。仅用于搜索索引构建等需要全文的场景。 */
    suspend fun parseWithContent(file: File): BookContent = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val opfPath = structureParser.findOpfPath(zip, entries)
            val opfData = structureParser.parseOpfMetadata(zip, opfPath)

            val chaptersWithContent = chapterExtractor.parseChaptersWithContent(
                zip, opfData.opfDir, opfData.spineItems, opfData.manifestItems,
            )
            val chapters = chaptersWithContent.map { it.first }
            val totalLength = chaptersWithContent.sumOf { it.second.length.toLong() }

            BookContent(
                title = opfData.title,
                author = opfData.author,
                encoding = "UTF-8",
                totalLength = totalLength,
                chapters = chapters,
                content = "",
            )
        }
    }

    /** 根据章节索引，动态解析并提取单个章节的正文文本 */
    fun parseChapter(file: File, spineIndex: Int): String {
        return chapterExtractor.parseChapter(file, spineIndex)
    }

    /** 测试辅助：暴露 extractTextFromHtml 供单元测试验证 */
    companion object {
        @JvmStatic
        fun extractTextFromHtmlForTest(html: String): String = HtmlTextExtractor().extractTextFromHtml(html)
    }
}
