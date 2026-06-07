package com.shuli.reader.core.parser.html

import org.jsoup.Jsoup

/**
 * HTML 文本提取：Jsoup 元素遍历、block/inline 文本提取、Unicode 样式转换。
 *
 * 从 EpubParser 拆出，可复用于 CBZ/PDF 等其他格式的 HTML 内文本提取。
 */
class HtmlTextExtractor(
    var imagePlaceholder: String = "Image",
) {
    /**
     * 从 HTML 中提取文本，保留段落结构和基本内联格式。
     * - 块级元素（h1~h6, p, div 等）作为段落分隔
     * - 内联格式（b/strong → Unicode 粗体，i/em → Unicode 斜体）
     * - img 标签替换为 [图片] 占位符
     */
    fun extractTextFromHtml(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script, style, meta, link, head").remove()
        val body = doc.body() ?: return ""

        val paragraphs = mutableListOf<String>()
        collectBlockText(body, paragraphs)

        if (paragraphs.isEmpty()) {
            val bodyText = body.text().trim()
            if (bodyText.isNotBlank()) paragraphs.add(bodyText)
        }

        return paragraphs.joinToString("\n\n")
    }

    fun extractChapterTitle(html: String): String? {
        val doc = Jsoup.parse(html)
        return doc.select("h1, h2, h3, title").first()?.text()
    }

    private fun collectBlockText(element: org.jsoup.nodes.Element, out: MutableList<String>) {
        val orphanText = StringBuilder()
        for (node in element.childNodes()) {
            when {
                node is org.jsoup.nodes.TextNode -> {
                    val t = node.text().trim()
                    if (t.isNotBlank()) {
                        if (orphanText.isNotEmpty()) orphanText.append(' ')
                        orphanText.append(t)
                    }
                }
                node is org.jsoup.nodes.Element -> {
                    if (orphanText.isNotEmpty()) {
                        out.add(orphanText.toString())
                        orphanText.clear()
                    }
                    if (node.tagName() == "hr") {
                        out.add("———")
                    } else if (node.tagName() in BLOCK_TAGS) {
                        val text = processInlineContent(node).trim()
                        if (text.isNotBlank()) out.add(text)
                    } else {
                        collectBlockText(node, out)
                    }
                }
            }
        }
        if (orphanText.isNotEmpty()) {
            out.add(orphanText.toString())
        }
    }

    private fun processInlineContent(element: org.jsoup.nodes.Element): String {
        val sb = StringBuilder()
        for (node in element.childNodes()) {
            when {
                node is org.jsoup.nodes.TextNode -> sb.append(node.text())
                node is org.jsoup.nodes.Element && node.tagName() == "img" -> {
                    val alt = node.attr("alt").ifBlank { imagePlaceholder }
                    sb.append("[$alt]")
                }
                node is org.jsoup.nodes.Element && node.tagName() in setOf("svg", "picture", "figure") -> {
                    val img = node.selectFirst("img")
                    val alt = img?.attr("alt")?.ifBlank { null }
                        ?: node.selectFirst("figcaption")?.text()?.ifBlank { null }
                        ?: imagePlaceholder
                    sb.append("[$alt]")
                }
                node is org.jsoup.nodes.Element && node.tagName() in BOLD_TAGS -> {
                    sb.append(toBoldUnicode(processInlineContent(node)))
                }
                node is org.jsoup.nodes.Element && node.tagName() in ITALIC_TAGS -> {
                    sb.append(toItalicUnicode(processInlineContent(node)))
                }
                node is org.jsoup.nodes.Element && node.tagName() !in BLOCK_TAGS -> {
                    sb.append(processInlineContent(node))
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

    companion object {
        private val BLOCK_TAGS = setOf(
            "h1", "h2", "h3", "h4", "h5", "h6",
            "p", "div", "li", "blockquote", "pre", "td", "th", "dt", "dd",
            "article", "section", "header", "footer", "figure", "figcaption",
        )

        private val BOLD_TAGS = setOf("b", "strong")
        private val ITALIC_TAGS = setOf("i", "em")
    }
}
