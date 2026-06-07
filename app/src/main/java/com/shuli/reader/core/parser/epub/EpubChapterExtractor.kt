package com.shuli.reader.core.parser.epub

import com.shuli.reader.core.parser.html.HtmlTextExtractor
import com.shuli.reader.core.parser.model.Chapter
import java.io.File
import java.util.zip.ZipFile

/**
 * 按 spine index 提取 EPUB 章节 HTML 并转换为纯文本。
 *
 * 从 EpubParser 拆出，依赖 [EpubStructureParser] 获取 OPF 元数据，
 * 依赖 [HtmlTextExtractor] 提取正文。
 */
class EpubChapterExtractor(
    private val structureParser: EpubStructureParser,
    private val htmlExtractor: HtmlTextExtractor,
) {
    /** 根据章节索引，动态解析并提取单个章节的正文文本 */
    fun parseChapter(file: File, spineIndex: Int): String {
        return try {
            ZipFile(file).use { zip ->
                val opf = structureParser.cachedOpf
                val spineItems: List<String>
                val manifestItems: Map<String, String>
                val opfDir: String

                if (opf != null) {
                    spineItems = opf.spineItems
                    manifestItems = opf.manifestItems
                    opfDir = opf.opfDir
                } else {
                    val entries = zip.entries().toList()
                    val opfPath = structureParser.findOpfPath(zip, entries)
                    val opfEntry = zip.getEntry(opfPath) ?: return ""
                    opfDir = opfPath.substringBeforeLast("/")
                    val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
                    val doc = org.jsoup.Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())
                    spineItems = doc.select("spine itemref").map { it.attr("idref") }
                    manifestItems = doc.select("manifest item").associate { it.attr("id") to it.attr("href") }
                }

                val idref = spineItems.getOrNull(spineIndex) ?: return ""
                val href = manifestItems[idref] ?: return ""
                val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
                if (fullPath.contains("..")) return ""

                val entry = zip.getEntry(fullPath) ?: return ""
                val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
                htmlExtractor.extractTextFromHtml(htmlContent)
            }
        } catch (e: Exception) {
            android.util.Log.e("EpubChapterExtractor", "Failed to parse chapter $spineIndex", e)
            ""
        }
    }

    /** 解析全部章节，返回章节和对应的正文内容 */
    fun parseChaptersWithContent(
        zip: ZipFile,
        opfDir: String,
        spineItems: List<String>,
        manifestItems: Map<String, String>,
    ): List<Pair<Chapter, String>> {
        val result = mutableListOf<Pair<Chapter, String>>()

        for ((spineIdx, spineItem) in spineItems.withIndex()) {
            val href = manifestItems[spineItem] ?: continue
            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
            if (fullPath.contains("..")) continue

            val entry = zip.getEntry(fullPath) ?: continue
            val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
            val textContent = htmlExtractor.extractTextFromHtml(htmlContent)

            if (textContent.isNotBlank()) {
                val title = htmlExtractor.extractChapterTitle(htmlContent) ?: "Chapter ${result.size + 1}"
                val chapter = Chapter(title = title, spineIndex = spineIdx)
                result.add(chapter to textContent)
            }
        }

        return result
    }
}
