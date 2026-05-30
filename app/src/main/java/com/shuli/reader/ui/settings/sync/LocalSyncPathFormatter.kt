package com.shuli.reader.ui.settings.sync

// Part of T-41 本地路径友好显示
data class FormattedPath(
    val displayPath: String,
    val storageLabel: String,
)

object LocalSyncPathFormatter {

    /**
     * 格式化本地同步路径为友好显示
     *
     * 将 content:// URI 转换为可读的路径格式。
     * 例如：content://.../primary%3ADocuments%2FShuLiReader → "Documents / ShuLiReader"
     */
    fun format(uriString: String): FormattedPath {
        // Extract the path from the URI
        val decoded = java.net.URLDecoder.decode(uriString, "UTF-8")

        // Extract the tree part after "tree/"
        val treePart = decoded.substringAfter("tree/", "")

        // Split by ":" to get storage ID and path
        val parts = treePart.split(":", limit = 2)
        val storageId = parts.getOrElse(0) { "" }
        val pathPart = parts.getOrElse(1) { "" }

        val isPrimary = storageId == "primary"
        val storageLabel = if (isPrimary) "内部存储" else "外部存储"

        val displayPath = pathPart
            .replace("/", " / ")
            .ifEmpty { "根目录" }

        return FormattedPath(
            displayPath = displayPath,
            storageLabel = storageLabel,
        )
    }

    /**
     * 取消同步说明文案
     */
    fun getCancelSyncExplanation(): String {
        return "取消不会丢失已完成的部分，下次同步时继续"
    }
}
