package com.shuli.reader.core.dictionary.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 词典释义 AST 中间层
 *
 * 将 HTML 解析为结构化的节点树，再渲染为 Compose AnnotatedString
 */
sealed class DictNode {
    /** 文本节点 */
    data class Text(val text: String) : DictNode()

    /** 带样式的节点 */
    data class Styled(
        val style: DictStyle,
        val children: List<DictNode>,
    ) : DictNode()

    /** 链接节点 */
    data class Link(
        val href: String,
        val text: String,
    ) : DictNode()

    /** 换行节点 */
    data object Break : DictNode()

    /** 段落节点 */
    data class Paragraph(val children: List<DictNode>) : DictNode()

    /** 列表节点 */
    data class DictList(val children: List<ListItem>) : DictNode()

    /** 列表项 */
    data class ListItem(val children: List<DictNode>) : DictNode()

    /** 纯文本模式（忽略所有标签） */
    data class PlainText(val text: String) : DictNode()
}

/**
 * 样式定义
 */
data class DictStyle(
    val color: Color? = null,
    val fontSize: Float? = null,  // sp
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val textDecoration: TextDecoration? = null,
    val baselineShift: BaselineShift? = null,
    val backgroundColor: Color? = null,
) {
    /**
     * 转换为 Compose SpanStyle
     */
    fun toSpanStyle(): SpanStyle {
        return SpanStyle(
            color = color ?: Color.Unspecified,
            fontSize = if (fontSize != null) fontSize.sp else TextUnit.Unspecified,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
            baselineShift = baselineShift,
            background = backgroundColor ?: Color.Unspecified,
        )
    }

    companion object {
        /** 粗体 */
        val BOLD = DictStyle(fontWeight = FontWeight.Bold)

        /** 斜体 */
        val ITALIC = DictStyle(fontStyle = FontStyle.Italic)

        /** 下划线 */
        val UNDERLINE = DictStyle(textDecoration = TextDecoration.Underline)

        /** 上标 */
        val SUPERSCRIPT = DictStyle(baselineShift = BaselineShift.Superscript)

        /** 下标 */
        val SUBSCRIPT = DictStyle(baselineShift = BaselineShift.Subscript)

        /** 链接样式（蓝色 + 下划线） */
        val LINK = DictStyle(
            color = Color(0xFF1A73E8),
            textDecoration = TextDecoration.Underline,
        )
    }
}
