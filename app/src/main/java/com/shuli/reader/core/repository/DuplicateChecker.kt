package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.entity.BookEntity
import java.io.File
import java.security.MessageDigest

/**
 * 重复书籍检测器
 */
class DuplicateChecker(
    private val bookDao: BookDao,
) {
    /**
     * 检查结果
     */
    sealed class CheckResult {
        /** 不重复，可以导入 */
        data object NotDuplicate : CheckResult()

        /** 文件路径重复 */
        data class DuplicatePath(val existingBook: BookEntity) : CheckResult()

        /** 文件名和大小重复（可能是同一文件） */
        data class DuplicateNameAndSize(val existingBook: BookEntity) : CheckResult()

        /** 内容 hash 重复（确认是同一文件） */
        data class DuplicateContent(val existingBook: BookEntity) : CheckResult()
    }

    /**
     * 检查文件是否重复
     * @param file 要检查的文件
     * @param targetPath 目标路径（复制到应用目录后的路径）
     * @param checkContentHash 是否检查内容 hash（大文件慎用）
     */
    suspend fun checkDuplicate(
        file: File,
        targetPath: String,
        checkContentHash: Boolean = false,
    ): CheckResult {
        // 1. 检查目标路径是否已存在
        val existingByPath = bookDao.getBookByFilePath(targetPath)
        if (existingByPath != null) {
            return CheckResult.DuplicatePath(existingByPath)
        }

        // 2. 检查文件名和大小
        val fileName = file.name
        val fileSize = file.length()
        val existingBySize = bookDao.getBooksByFileNameAndSize(fileName, fileSize)
        if (existingBySize.isNotEmpty()) {
            // 如果启用了内容 hash，进一步确认
            if (checkContentHash) {
                val fileHash = calculateFileHash(file)
                val duplicate = existingBySize.find { book ->
                    val existingFile = File(book.filePath)
                    existingFile.exists() && calculateFileHash(existingFile) == fileHash
                }
                if (duplicate != null) {
                    return CheckResult.DuplicateContent(duplicate)
                }
            } else {
                return CheckResult.DuplicateNameAndSize(existingBySize.first())
            }
        }

        return CheckResult.NotDuplicate
    }

    /**
     * 计算文件 MD5 hash（用于大文件时可分块计算）
     */
    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)

        file.inputStream().use { input ->
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
