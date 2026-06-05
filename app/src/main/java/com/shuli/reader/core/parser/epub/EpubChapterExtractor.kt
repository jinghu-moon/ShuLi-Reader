package com.shuli.reader.core.parser.epub

import com.shuli.reader.core.database.entity.BookChapterEntity
import com.shuli.reader.core.parser.html.HtmlTextExtractor
import com.shuli.reader.core.parser.model.Chapter
import java.util.zip.ZipFile

/**
 * EPUB 章节提取器：按 spine index 提取章节内容和标题。
 */
object EpubChapterExtractor {

    /**
     * 轻量扫描：只提取章节标题和 spine 索引，不读取正文。
     */
    fun parseChapterList(
        zip: ZipFile,
        opfDir: String,
        spineItems: List<String>,
        manifestItems: Map<String, String>,
        navTitles: Map<Int, String> = emptyMap(),
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
                    HtmlTextExtractor.extractChapterTitle(htmlContent)
                }
                ?: "Chapter ${chapters.size + 1}"

            chapters.add(
                Chapter(
                    title = title,
                    spineIndex = spineIdx,
                ),
            )
        }
        return chapters
    }

    /**
     * 构建章节目录索引：只提取标题和 spineIndex，不读取正文。
     */
    fun parseChapterIndex(
        zip: ZipFile,
        opfDir: String,
        spineItems: List<String>,
        manifestItems: Map<String, String>,
        navTitles: Map<Int, String>,
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
                    HtmlTextExtractor.extractChapterTitle(htmlContent)
                }
                ?: "Chapter ${chapters.size + 1}"

            chapters.add(
                BookChapterEntity(
                    bookId = 0,
                    chapterIndex = chapters.size,
                    title = title,
                    spineIndex = spineIdx,
                ),
            )
        }
        return chapters
    }

    /**
     * 根据 spineIndex 提取单个章节的正文文本。
     */
    fun parseChapter(
        zip: ZipFile,
        opfDir: String,
        spineItems: List<String>,
        manifestItems: Map<String, String>,
        spineIndex: Int,
        imagePlaceholder: String,
    ): String {
        val idref = spineItems.getOrNull(spineIndex) ?: return ""
        val href = manifestItems[idref] ?: return ""
        val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
        if (fullPath.contains("..")) return ""

        val entry = zip.getEntry(fullPath) ?: return ""
        val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
        return HtmlTextExtractor.extractText(htmlContent, imagePlaceholder)
    }

    /**
     * 解析所有章节，返回章节和对应的正文内容。
     */
    fun parseChaptersWithContent(
        zip: ZipFile,
        opfDir: String,
        spineItems: List<String>,
        manifestItems: Map<String, String>,
        imagePlaceholder: String,
    ): List<Pair<Chapter, String>> {
        val result = mutableListOf<Pair<Chapter, String>>()

        for ((spineIdx, spineItem) in spineItems.withIndex()) {
            val href = manifestItems[spineItem] ?: continue
            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
            if (fullPath.contains("..")) continue

            val entry = zip.getEntry(fullPath) ?: continue
            val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
            val textContent = HtmlTextExtractor.extractText(htmlContent, imagePlaceholder)

            if (textContent.isNotBlank()) {
                val title = HtmlTextExtractor.extractChapterTitle(htmlContent) ?: "Chapter ${result.size + 1}"
                val chapter = Chapter(
                    title = title,
                    spineIndex = spineIdx,
                )
                result.add(chapter to textContent)
            }
        }

        return result
    }
}
