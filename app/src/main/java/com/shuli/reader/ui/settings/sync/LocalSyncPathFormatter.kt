package com.shuli.reader.ui.settings.sync

import com.shuli.reader.core.i18n.AppStrings

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
     *
     * @param strings 国际化字符串
     */
    fun format(
        uriString: String,
        strings: AppStrings = AppStrings.ZhHans,
    ): FormattedPath {
        // Extract the path from the URI
        val decoded = java.net.URLDecoder.decode(uriString, "UTF-8")

        // Extract the tree part after "tree/"
        val treePart = decoded.substringAfter("tree/", "")

        // Split by ":" to get storage ID and path
        val parts = treePart.split(":", limit = 2)
        val storageId = parts.getOrElse(0) { "" }
        val pathPart = parts.getOrElse(1) { "" }

        val isPrimary = storageId == "primary"
        val storageLabel = if (isPrimary) strings.internalStorage else strings.externalStorage

        val displayPath = pathPart
            .replace("/", " / ")
            .ifEmpty { strings.rootDirectory }

        return FormattedPath(
            displayPath = displayPath,
            storageLabel = storageLabel,
        )
    }
}
