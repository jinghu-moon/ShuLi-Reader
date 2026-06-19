package com.shuli.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 词库元数据表
 *
 * 存储已导入词典的基本信息
 */
@Entity(tableName = "dict_meta")
data class DictMetaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 词典唯一标识（文件名哈希） */
    val dictKey: String,
    /** 词典显示名称 */
    val displayName: String,
    /** 词典格式：stardict / mdx */
    val format: String,
    /** 词典语言对（如 "en-zh"、"zh-en"） */
    val langPair: String = "",
    /** 词典文件路径 */
    val filePath: String,
    /** 索引文件路径（Stardict .idx） */
    val indexPath: String? = null,
    /** 数据文件路径（Stardict .dict / .dict.dz） */
    val dataPath: String? = null,
    /** 词条总数 */
    val entryCount: Int = 0,
    /** 是否启用 */
    val isEnabled: Boolean = true,
    /** 优先级（越小越优先） */
    val priority: Int = 0,
    /** 导入时间 */
    val importedAt: Long = System.currentTimeMillis(),
    /** 最后使用时间 */
    val lastUsedAt: Long = 0L,
)
