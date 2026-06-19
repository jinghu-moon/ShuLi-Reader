package com.shuli.reader.core.dictionary.model

/**
 * 词典查询结果条目
 */
data class DictEntry(
    /** 词条 */
    val word: String,
    /** 释义内容（HTML 或纯文本） */
    val definition: String,
    /** 来源词典标识 */
    val dictKey: String,
    /** 来源词典名称 */
    val dictName: String,
    /** 释义类型 */
    val definitionType: DefinitionType = DefinitionType.TEXT,
    /** 词典格式 */
    val dictionaryFormat: DictFormat? = null,
    /** 是否为同义词匹配 */
    val isSynonymMatch: Boolean = false,
    /** 音标（如果有） */
    val phonetic: String? = null,
    /** 词性（如果有） */
    val partOfSpeech: String? = null,
) {
    /** 是否为 HTML 格式（基于 definitionType 或内容检测） */
    val isHtml: Boolean
        get() = definitionType == DefinitionType.HTML ||
            (definition.contains("<") && definition.contains(">") && definition.contains("</"))
}

/**
 * 释义类型枚举
 */
enum class DefinitionType {
    /** 纯文本 */
    TEXT,
    /** HTML */
    HTML,
    /** XDXF (XML Dictionary Exchange Format) */
    XDXF,
    /** 词性标记 */
    POS,
}
