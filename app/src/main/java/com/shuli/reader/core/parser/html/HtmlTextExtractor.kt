package com.shuli.reader.core.parser.html

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * HTML 文本提取器：从 XHTML 内容中提取纯文本，保留段落结构和基本内联格式。
 *
 * - 块级元素（h1~h6, p, div 等）作为段落分隔
 * - 内联格式（b/strong → Unicode 粗体，i/em → Unicode 斜体）
 * - img 标签替换为占位符
 */
object HtmlTextExtractor {

    private val BLOCK_TAGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6",
        "p", "div", "li", "blockquote", "pre", "td", "th", "dt", "dd",
        "article", "section", "header", "footer", "figure", "figcaption",
    )

    private val BOLD_TAGS = setOf("b", "strong")
    private val ITALIC_TAGS = setOf("i", "em")

    /**
     * 从 HTML 中提取文本，保留段落结构和基本内联格式。
     */
    fun extractText(html: String, imagePlaceholder: String = "Image"): String {
        val doc = Jsoup.parse(html)
        doc.select("script, style, meta, link, head").remove()
        val body = doc.body() ?: return ""

        val paragraphs = mutableListOf<String>()
        collectBlockText(body, paragraphs, imagePlaceholder)

        if (paragraphs.isEmpty()) {
            val bodyText = body.text().trim()
            if (bodyText.isNotBlank()) paragraphs.add(bodyText)
        }

        return paragraphs.joinToString("\n\n")
    }

    /**
     * 从 HTML 中提取章节标题。
     */
    fun extractChapterTitle(html: String): String? {
        val doc = Jsoup.parse(html)
        return doc.select("h1, h2, h3, title").first()?.text()
    }

    private fun collectBlockText(element: Element, out: MutableList<String>, imagePlaceholder: String) {
        val orphanText = StringBuilder()
        for (node in element.childNodes()) {
            when {
                node is TextNode -> {
                    val t = node.text().trim()
                    if (t.isNotBlank()) {
                        if (orphanText.isNotEmpty()) orphanText.append(' ')
                        orphanText.append(t)
                    }
                }
                node is Element -> {
                    if (orphanText.isNotEmpty()) {
                        out.add(orphanText.toString())
                        orphanText.clear()
                    }
                    if (node.tagName() == "hr") {
                        out.add("———")
                    } else if (node.tagName() in BLOCK_TAGS) {
                        val text = processInlineContent(node, imagePlaceholder).trim()
                        if (text.isNotBlank()) out.add(text)
                    } else {
                        collectBlockText(node, out, imagePlaceholder)
                    }
                }
            }
        }
        if (orphanText.isNotEmpty()) {
            out.add(orphanText.toString())
        }
    }

    private fun processInlineContent(element: Element, imagePlaceholder: String): String {
        val sb = StringBuilder()
        for (node in element.childNodes()) {
            when {
                node is TextNode -> sb.append(node.text())
                node is Element && node.tagName() == "img" -> {
                    val alt = node.attr("alt").ifBlank { imagePlaceholder }
                    sb.append("[$alt]")
                }
                node is Element && node.tagName() in setOf("svg", "picture", "figure") -> {
                    val img = node.selectFirst("img")
                    val alt = img?.attr("alt")?.ifBlank { null }
                        ?: node.selectFirst("figcaption")?.text()?.ifBlank { null }
                        ?: imagePlaceholder
                    sb.append("[$alt]")
                }
                node is Element && node.tagName() in BOLD_TAGS -> {
                    sb.append(toBoldUnicode(processInlineContent(node, imagePlaceholder)))
                }
                node is Element && node.tagName() in ITALIC_TAGS -> {
                    sb.append(toItalicUnicode(processInlineContent(node, imagePlaceholder)))
                }
                node is Element && node.tagName() !in BLOCK_TAGS -> {
                    sb.append(processInlineContent(node, imagePlaceholder))
                }
            }
        }
        return sb.toString()
    }

    /** 将拉丁字母/数字转换为 Unicode Mathematical Bold 字符 */
    private fun toBoldUnicode(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(
                when {
                    c in 'A'..'Z' -> (0x1D400 + (c - 'A')).toChar()
                    c in 'a'..'z' -> (0x1D41A + (c - 'a')).toChar()
                    c in '0'..'9' -> (0x1D7CE + (c - '0')).toChar()
                    else -> c
                },
            )
        }
        return sb.toString()
    }

    /** 将拉丁字母/数字转换为 Unicode Mathematical Italic 字符 */
    private fun toItalicUnicode(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(
                when {
                    c in 'A'..'Z' -> (0x1D434 + (c - 'A')).toChar()
                    c in 'a'..'z' -> (0x1D44E + (c - 'a')).toChar()
                    c == 'h' -> 'ℎ'
                    else -> c
                },
            )
        }
        return sb.toString()
    }
}
