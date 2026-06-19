package com.shuli.reader.core.dictionary.render

import androidx.compose.ui.text.AnnotatedString
import com.shuli.reader.core.dictionary.model.DefinitionType
import org.jsoup.Jsoup

/**
 * 词典渲染器统一接口
 *
 * 根据释义类型选择合适的渲染器
 */
object DictRenderer {

    /**
     * 渲染释义为 AnnotatedString
     *
     * @param definition 释义内容
     * @param definitionType 释义类型
     * @param isDarkMode 是否为暗色模式
     * @return 渲染后的 AnnotatedString
     */
    fun render(
        definition: String,
        definitionType: DefinitionType,
        isDarkMode: Boolean = false,
    ): AnnotatedString {
        return when (definitionType) {
            DefinitionType.HTML -> DictMdxRenderer.render(definition, isDarkMode)
            DefinitionType.TEXT -> AnnotatedString(definition)
            DefinitionType.XDXF -> renderXdxf(definition, isDarkMode)
            DefinitionType.POS -> AnnotatedString(definition)
        }
    }

    /**
     * 渲染为纯文本
     */
    fun renderPlainText(
        definition: String,
        definitionType: DefinitionType,
    ): String {
        return when (definitionType) {
            DefinitionType.HTML -> DictMdxRenderer.renderPlainText(definition)
            DefinitionType.TEXT -> definition
            DefinitionType.XDXF -> renderXdxfPlainText(definition)
            DefinitionType.POS -> definition
        }
    }

    /**
     * 渲染 XDXF 格式
     *
     * XDXF 是 XML 格式的词典交换格式
     */
    private fun renderXdxf(xml: String, isDarkMode: Boolean): AnnotatedString {
        // XDXF 格式示例：
        // <ar><k>word</k><def><dtrn>definition</dtrn></def></ar>
        return try {
            val doc = Jsoup.parseBodyFragment(xml)
            val sb = StringBuilder()

            // 提取词头
            doc.select("k").forEach { sb.append("【${it.text()}】") }

            // 提取释义
            doc.select("dtrn").forEach { sb.append(it.text()) }

            // 提取例句
            doc.select("ex_orig").forEach { sb.append("\n例：${it.text()}") }

            AnnotatedString(sb.toString())
        } catch (_: Exception) {
            AnnotatedString(xml)
        }
    }

    /**
     * XDXF 转纯文本
     */
    private fun renderXdxfPlainText(xml: String): String {
        return try {
            val doc = Jsoup.parseBodyFragment(xml)
            doc.body().text()
        } catch (_: Exception) {
            xml
        }
    }
}
