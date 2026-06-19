package com.shuli.reader.core.database.entity

import androidx.room.ColumnInfo
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
    @ColumnInfo(name = "dict_key")
    val dictKey: String,
    /** 词典显示名称 */
    @ColumnInfo(name = "display_name")
    val displayName: String,
    /** 词典格式：stardict / mdx */
    val format: String,
    /** 词典语言对（如 "en-zh"、"zh-en"） */
    @ColumnInfo(name = "lang_pair")
    val langPair: String = "",
    /** 词典文件路径 */
    @ColumnInfo(name = "file_path")
    val filePath: String,
    /** 索引文件路径（Stardict .idx） */
    @ColumnInfo(name = "index_path")
    val indexPath: String? = null,
    /** 数据文件路径（Stardict .dict / .dict.dz） */
    @ColumnInfo(name = "data_path")
    val dataPath: String? = null,
    /** 词条总数 */
    @ColumnInfo(name = "entry_count")
    val entryCount: Int = 0,
    /** 是否启用 */
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,
    /** 优先级（越小越优先） */
    val priority: Int = 0,
    /** 导入时间 */
    @ColumnInfo(name = "imported_at")
    val importedAt: Long = System.currentTimeMillis(),
    /** 最后使用时间 */
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long = 0L,
)
