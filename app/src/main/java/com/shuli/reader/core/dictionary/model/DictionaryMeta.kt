package com.shuli.reader.core.dictionary.model

/**
 * 词典元数据（内存模型）
 */
data class DictionaryMeta(
    /** 词典唯一标识 */
    val dictKey: String,
    /** 显示名称 */
    val displayName: String,
    /** 词典格式 */
    val format: DictFormat,
    /** 语言对（如 "en-zh"） */
    val langPair: String = "",
    /** 主文件路径（.ifo 或 .mdx） */
    val filePath: String,
    /** 索引文件路径（Stardict .idx） */
    val indexPath: String? = null,
    /** 数据文件路径（Stardict .dict/.dict.dz） */
    val dataPath: String? = null,
    /** 词条总数 */
    val entryCount: Int = 0,
    /** 是否启用 */
    val isEnabled: Boolean = true,
    /** 优先级 */
    val priority: Int = 0,
)
