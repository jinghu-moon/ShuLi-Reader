package com.shuli.reader.core.dictionary.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration

/**
 * CSS 样式解析器
 *
 * 将 CSS class 和内联样式映射为 DictStyle
 */
object DictStyleResolver {

    /** 常见 CSS class 到样式的映射 */
    private val CLASS_MAP = mapOf(
        // 字体样式
        "bold" to DictStyle.BOLD,
        "b" to DictStyle.BOLD,
        "strong" to DictStyle.BOLD,
        "italic" to DictStyle.ITALIC,
        "i" to DictStyle.ITALIC,
        "em" to DictStyle.ITALIC,
        "underline" to DictStyle.UNDERLINE,
        "u" to DictStyle.UNDERLINE,

        // 上标下标
        "sup" to DictStyle.SUPERSCRIPT,
        "super" to DictStyle.SUPERSCRIPT,
        "sub" to DictStyle.SUBSCRIPT,

        // 链接
        "link" to DictStyle.LINK,
        "ref" to DictStyle.LINK,

        // 词性标签
        "pos" to DictStyle(color = Color(0xFF666666)),
        "part-of-speech" to DictStyle(color = Color(0xFF666666)),

        // 音标
        "phonetic" to DictStyle(color = Color(0xFF888888)),
        "pinyin" to DictStyle(color = Color(0xFF888888)),

        // 释义
        "definition" to DictStyle(),
        "def" to DictStyle(),

        // 例句
        "example" to DictStyle(color = Color(0xFF555555)),
        "sentence" to DictStyle(color = Color(0xFF555555)),

        // 标题
        "headword" to DictStyle(fontWeight = FontWeight.Bold),
        "title" to DictStyle(fontWeight = FontWeight.Bold),
    )

    /** 常见内联样式属性解析 */
    private val STYLE_PARSERS: Map<String, (String) -> DictStyle?> = mapOf(
        "color" to { parseColor(it)?.let { c -> DictStyle(color = c) } },
        "font-weight" to { parseFontWeight(it)?.let { w -> DictStyle(fontWeight = w) } },
        "font-style" to { parseFontStyle(it)?.let { s -> DictStyle(fontStyle = s) } },
        "text-decoration" to { parseTextDecoration(it)?.let { d -> DictStyle(textDecoration = d) } },
        "font-size" to { parseFontSize(it)?.let { s -> DictStyle(fontSize = s) } },
        "background-color" to { parseColor(it)?.let { c -> DictStyle(backgroundColor = c) } },
    )

    /**
     * 从 CSS class 名称解析样式
     */
    fun fromClassName(className: String?): DictStyle? {
        if (className.isNullOrBlank()) return null

        // 支持多个 class（空格分隔）
        val classes = className.split("\\s+".toRegex())
        var result = DictStyle()

        for (cls in classes) {
            val style = CLASS_MAP[cls.lowercase()]
            if (style != null) {
                result = result.merge(style)
            }
        }

        return if (result == DictStyle()) null else result
    }

    /**
     * 从内联 style 属性解析样式
     */
    fun fromInlineStyle(styleStr: String?): DictStyle? {
        if (styleStr.isNullOrBlank()) return null

        var result = DictStyle()

        // 解析 "key: value; key: value" 格式
        val declarations = styleStr.split(";")
        for (decl in declarations) {
            val parts = decl.split(":", limit = 2)
            if (parts.size != 2) continue

            val key = parts[0].trim().lowercase()
            val value = parts[1].trim()

            val parser = STYLE_PARSERS[key]
            if (parser != null) {
                val style = parser(value)
                if (style != null) {
                    result = result.merge(style)
                }
            }
        }

        return if (result == DictStyle()) null else result
    }

    /**
     * 合并两个样式（后者覆盖前者）
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
     * 解析颜色值
     */
    private fun parseColor(value: String): Color? {
        val v = value.trim().lowercase()
        return when {
            v.startsWith("#") -> {
                try {
                    val hex = v.removePrefix("#")
                    when (hex.length) {
                        6 -> Color(android.graphics.Color.parseColor("#FF$hex"))
                        8 -> Color(android.graphics.Color.parseColor("#$hex"))
                        else -> null
                    }
                } catch (_: Exception) {
                    null
                }
            }
            v.startsWith("rgb") -> {
                try {
                    val nums = v.substringAfter("(").substringBefore(")")
                        .split(",").map { it.trim().toInt() }
                    if (nums.size >= 3) Color(nums[0], nums[1], nums[2]) else null
                } catch (_: Exception) {
                    null
                }
            }
            else -> NAMED_COLORS[v]
        }
    }

    /**
     * 解析 font-weight
     */
    private fun parseFontWeight(value: String): FontWeight? {
        return when (value.trim().lowercase()) {
            "bold", "700", "800", "900" -> FontWeight.Bold
            "normal", "400" -> FontWeight.Normal
            "lighter", "300" -> FontWeight.Light
            "bolder", "600" -> FontWeight.Medium
            else -> null
        }
    }

    /**
     * 解析 font-style
     */
    private fun parseFontStyle(value: String): FontStyle? {
        return when (value.trim().lowercase()) {
            "italic" -> FontStyle.Italic
            "normal" -> FontStyle.Normal
            else -> null
        }
    }

    /**
     * 解析 text-decoration
     */
    private fun parseTextDecoration(value: String): TextDecoration? {
        return when (value.trim().lowercase()) {
            "underline" -> TextDecoration.Underline
            "line-through" -> TextDecoration.LineThrough
            "none" -> TextDecoration.None
            else -> null
        }
    }

    /**
     * 解析 font-size（px → sp）
     */
    private fun parseFontSize(value: String): Float? {
        val v = value.trim().lowercase()
        return try {
            when {
                v.endsWith("px") -> v.removeSuffix("px").toFloat()
                v.endsWith("pt") -> v.removeSuffix("pt").toFloat() * 1.33f
                v.endsWith("em") -> v.removeSuffix("em").toFloat() * 16f
                v.endsWith("%") -> v.removeSuffix("%").toFloat() / 100f * 16f
                else -> v.toFloatOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 常见颜色名称映射 */
    private val NAMED_COLORS = mapOf(
        "black" to Color.Black,
        "white" to Color.White,
        "red" to Color.Red,
        "green" to Color(0xFF008000),
        "blue" to Color.Blue,
        "yellow" to Color.Yellow,
        "gray" to Color.Gray,
        "grey" to Color.Gray,
        "orange" to Color(0xFFFFA500),
        "purple" to Color(0xFF800080),
        "brown" to Color(0xFFA52A2A),
        "pink" to Color(0xFFFFC0CB),
        "cyan" to Color.Cyan,
        "magenta" to Color.Magenta,
    )
}
