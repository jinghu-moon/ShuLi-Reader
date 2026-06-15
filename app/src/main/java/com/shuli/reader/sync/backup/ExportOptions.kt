// Part of T-30 ZIP 明文导出
package com.shuli.reader.sync.backup

/**
 * 导出配置选项。
 *
 * @param includeBookFiles 是否包含书籍文件（.txt/.epub）
 * @param includeBookmarks 是否包含书签
 * @param includeNotes 是否包含笔记
 * @param includeProgress 是否包含阅读进度
 * @param includeConfig 是否包含阅读器配置
 * @param encryptionPassword 导出加密密码（null = 明文导出）
 */
data class ExportOptions(
    val includeBookFiles: Boolean = true,
    val includeBookmarks: Boolean = true,
    val includeNotes: Boolean = true,
    val includeProgress: Boolean = true,
    val includeConfig: Boolean = true,
    val encryptionPassword: String? = null,
)
