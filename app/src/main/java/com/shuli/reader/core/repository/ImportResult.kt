package com.shuli.reader.core.repository

/**
 * 导入统计结果
 */
data class ImportResult(
    val successCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val failedFiles: List<String> = emptyList(),
    val firstDuplicateBookId: Long? = null,
) {
    val totalCount: Int get() = successCount + skippedCount + failedCount
    val hasSuccess: Boolean get() = successCount > 0
    val hasSkipped: Boolean get() = skippedCount > 0
    val hasFailed: Boolean get() = failedCount > 0
    val isAllSuccess: Boolean get() = failedCount == 0 && skippedCount == 0

    companion object {
        val EMPTY = ImportResult()
    }
}

/**
 * 导入配置
 */
data class ImportConfig(
    val maxFolderDepth: Int = 3,           // 文件夹递归最大深度
    val maxFilesPerImport: Int = 100,      // 单次导入最大文件数
    val supportedExtensions: Set<String> = setOf("txt", "epub"),  // 支持的文件扩展名
    val copyToAppDir: Boolean = true,      // 是否复制到应用目录
)

/**
 * 导入进度回调
 */
interface ImportProgressCallback {
    fun onImportStart(totalFiles: Int)
    fun onFileImporting(fileName: String, currentIndex: Int, totalFiles: Int)
    fun onFileImported(fileName: String, success: Boolean, errorMessage: String? = null)
    fun onImportComplete(result: ImportResult)
}
