package com.shuli.reader.sync.engine.manifest

import kotlinx.serialization.Serializable

/**
 * 同步清单数据模型（T-17）
 *
 * 轻量全局索引，不含 books 列表。
 * 书籍详情存储在各自的 books/{bookKey}/meta.json 中。
 */
@Serializable
data class SyncManifest(
    val schemaVersion: Int = 2,
    val updatedAt: Long = 0L,
    val updatedBy: String = "",
    val version: Int = 0,
    val bookCount: Int = 0,
)
