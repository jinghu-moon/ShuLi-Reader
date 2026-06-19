package com.shuli.reader.core.dictionary.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * MDX 词典 HTML 渲染器
 *
 * 将 MDX 释义的 HTML 解析为 AnnotatedString，支持：
 * - 常见 HTML 标签（b, i, u, sup, sub, p, br, div, span 等）
 * - CSS class 和内联样式
 * - entry:// 链接（词典内跳转）
 * - 表格降级为纯文本
 */
object DictMdxRenderer {

    /** entry:// 链接的注解标签 */
    const val LINK_TAG = "dict_link"

    /**
     * 渲染 HTML 为 AnnotatedString
     *
     * @param html HTML 内容
     * @param isDarkMode 是否为暗色模式
     * @return 渲染后的 AnnotatedString
     */
    fun render(html: String, isDarkMode: Boolean = false): AnnotatedString {
        val doc = Jsoup.parseBodyFragment(html)
        return buildAnnotatedString {
            renderNodes(doc.body().childNodes(), this, isDarkMode)
        }
    }

    /**
     * 渲染为纯文本（忽略所有标签）
     */
    fun renderPlainText(html: String): String {
        val doc = Jsoup.parseBodyFragment(html)
        return doc.body().text()
    }

    /**
     * 递归渲染节点列表
     */
    private fun renderNodes(
        nodes: List<Node>,
        builder: AnnotatedString.Builder,
        isDarkMode: Boolean,
    ) {
        for (node in nodes) {
            when (node) {
                is TextNode -> {
                    builder.append(node.text())
                }
                is Element -> {
                    renderElement(node, builder, isDarkMode)
                }
            }
        }
    }

    /**
     * 渲染 HTML 元素
     */
    private fun renderElement(
        element: Element,
        builder: AnnotatedString.Builder,
        isDarkMode: Boolean,
    ) {
        val tagName = element.tagName().lowercase()

        // 表格降级为纯文本
        if (tagName in setOf("table", "tr", "td", "th", "tbody", "thead")) {
            builder.append(element.text())
            return
        }

        // 换行
        if (tagName == "br") {
            builder.append("\n")
            return
        }

        // 段落
        if (tagName in setOf("p", "div", "blockquote")) {
            if (builder.length > 0 && !builder.toString().endsWith("\n")) {
                builder.append("\n")
            }
            renderNodes(element.childNodes(), builder, isDarkMode)
            if (!builder.toString().endsWith("\n")) {
                builder.append("\n")
            }
            return
        }

        // 链接
        if (tagName == "a") {
            val href = element.attr("href")
            if (href.startsWith("entry://")) {
                val target = href.removePrefix("entry://")
                builder.pushStringAnnotation(LINK_TAG, target)
                renderNodes(element.childNodes(), builder, isDarkMode)
                builder.pop()
                return
            }
        }

        // 解析样式
        val style = resolveStyle(element, isDarkMode)

        if (style != null) {
            builder.withStyle(style.toSpanStyle()) {
                renderNodes(element.childNodes(), builder, isDarkMode)
            }
        } else {
            renderNodes(element.childNodes(), builder, isDarkMode)
        }
    }

    /**
     * 解析元素样式
     */
    private fun resolveStyle(element: Element, isDarkMode: Boolean): DictStyle? {
        val tagName = element.tagName().lowercase()
        val className = element.className()
        val inlineStyle = element.attr("style")

        // 从标签名推断样式
        val tagStyle = when (tagName) {
            "b", "strong" -> DictStyle.BOLD
            "i", "em" -> DictStyle.ITALIC
            "u" -> DictStyle.UNDERLINE
            "sup" -> DictStyle.SUPERSCRIPT
            "sub" -> DictStyle.SUBSCRIPT
            "s", "strike", "del" -> DictStyle(textDecoration = TextDecoration.LineThrough)
            else -> null
        }

        // 从 CSS class 解析
        val classStyle = DictStyleResolver.fromClassName(className)

        // 从内联样式解析
        val inlineStyleParsed = DictStyleResolver.fromInlineStyle(inlineStyle)

        // 合并样式（优先级：内联 > class > tag）
        var result = tagStyle ?: DictStyle()
        if (classStyle != null) result = result.merge(classStyle)
        if (inlineStyleParsed != null) result = result.merge(inlineStyleParsed)

        // 暗色模式颜色调整
        if (isDarkMode) {
            result = adjustForDarkMode(result)
        }

        return if (result == DictStyle()) null else result
    }

    /**
     * 合并两个样式
     */
    private fun DictStyle.merge(other: DictStyle): DictStyle {
        return DictStyle(
            color = other.color ?: this.color,
            fontSize = other.fontSize ?: this.fontSize,
            fontWeight = other.fontWeight ?: this.fontWeight,
            fontStyle = other.fontStyle ?: this.fontStyle,
            textDecoration = other.textDecoration ?: this.textDecoration,
            baselineShift = other.baselineShift ?: this.baselineShift,
            backgroundColor = other.backgroundColor ?: this.backgroundColor,
        )
    }

    /**
     * 暗色模式颜色调整
     */
    private fun adjustForDarkMode(style: DictStyle): DictStyle {
        val color = style.color
        val adjustedColor = if (color != null && color != Color.Unspecified) {
            // 将深色文字调整为浅色
            val luminance = color.luminance()
            if (luminance < 0.3f) {
                // 深色文字 → 浅色（反转并提亮）
                Color(
                    red = (1f - color.red * 0.5f).coerceIn(0f, 1f),
                    green = (1f - color.green * 0.5f).coerceIn(0f, 1f),
                    blue = (1f - color.blue * 0.5f).coerceIn(0f, 1f),
                    alpha = color.alpha,
                )
            } else {
                color
            }
        } else {
            color
        }

        return style.copy(color = adjustedColor)
    }

    /**
     * 计算颜色亮度
     */
    private fun Color.luminance(): Float {
        return (0.299f * red + 0.587f * green + 0.114f * blue)
    }
}
