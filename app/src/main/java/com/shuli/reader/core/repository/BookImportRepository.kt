package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.TxtParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 文件导入、去重、批量导入。
 * Actor: 导入产品
 */
class BookImportRepository(
    private val bookDao: BookDao,
    private val txtParser: TxtParser,
    private val epubParser: EpubParser,
    private val booksDir: File,
    private val searchIndexRepository: SearchIndexRepository,
    /** 应用级协程作用域：用于后台建索引（v4） */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private val applicationScope: CoroutineScope,
) {
    private val duplicateChecker = DuplicateChecker(bookDao)

    suspend fun importBook(file: File, copyToAppDir: Boolean = true): Long = withContext(Dispatchers.IO) {
        val targetFilePath = if (copyToAppDir) {
            File(booksDir, file.name).absolutePath
        } else {
            file.absolutePath
        }

        // 使用 DuplicateChecker 进行重复检测
        val checkResult = duplicateChecker.checkDuplicate(file, targetFilePath)
        when (checkResult) {
            is DuplicateChecker.CheckResult.DuplicatePath -> {
                throw BookAlreadyExistsException(checkResult.existingBook.id, checkResult.existingBook.title)
            }
            is DuplicateChecker.CheckResult.DuplicateNameAndSize -> {
                throw BookAlreadyExistsException(checkResult.existingBook.id, checkResult.existingBook.title)
            }
            is DuplicateChecker.CheckResult.DuplicateContent -> {
                throw BookAlreadyExistsException(checkResult.existingBook.id, checkResult.existingBook.title)
            }
            is DuplicateChecker.CheckResult.NotDuplicate -> {
                // 继续导入
            }
        }

        var bookCoverPath: String? = null
        val (title, author) = when {
            file.name.endsWith(".txt", ignoreCase = true) -> {
                txtParser.parseMetadata(file)
            }
            file.name.endsWith(".epub", ignoreCase = true) -> {
                val (t, a, coverPathInZip) = epubParser.parseMetadata(file)
                if (coverPathInZip != null) {
                    try {
                        java.util.zip.ZipFile(file).use { zip ->
                            val coverEntry = zip.getEntry(coverPathInZip)
                            if (coverEntry != null) {
                                val coversDir = File(booksDir.parentFile, "covers")
                                coversDir.mkdirs()
                                val ext = coverPathInZip.substringAfterLast(".", "jpg")
                                val coverFile = File(coversDir, "${file.nameWithoutExtension}_cover.$ext")
                                zip.getInputStream(coverEntry).use { input ->
                                    coverFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                bookCoverPath = coverFile.absolutePath
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                t to a
            }
            else -> throw IllegalArgumentException("Unsupported file format: ${file.name}")
        }

        val targetFile = if (copyToAppDir) {
            booksDir.mkdirs()
            val target = File(booksDir, file.name)
            file.copyTo(target, overwrite = true)
            target
        } else {
            file
        }

        // TXT 文件探测 charset 并保存
        val charset = if (file.name.endsWith(".txt", ignoreCase = true)) {
            txtParser.detectCharset(file).name()
        } else {
            "UTF-8"
        }

        val bookEntity = BookEntity(
            title = title,
            author = author,
            filePath = targetFile.absolutePath,
            fileType = if (file.name.endsWith(".epub", ignoreCase = true)) "EPUB" else "TXT",
            fileSize = file.length(),
            coverPath = bookCoverPath,
            lastReadTime = null,
            addedTime = System.currentTimeMillis(),
            readingProgress = 0f,
            charset = charset,
            bookKey = UUID.randomUUID().toString(),
            updatedAt = System.currentTimeMillis(),
        )

        val bookId = bookDao.insertBook(bookEntity)
        if (targetFile.length() <= EAGER_SEARCH_INDEX_MAX_BYTES) {
            runCatching {
                searchIndexRepository.refreshSearchIndex(bookId)
            }
        }
        // v4：导入后立即在 applicationScope 后台异步构建章节目录索引，
        // 不阻塞导入返回；首次打开时若仍未完成会通过 chapterIndexMutexes 复用同一 Job。
        applicationScope.launch {
            runCatching { searchIndexRepository.ensureChapterIndex(bookId) }
                .onFailure { android.util.Log.e("BookImportRepository", "Async ensureChapterIndex failed for bookId=$bookId", it) }
        }
        bookId
    }

    /**
     * 扫描文件夹获取可导入文件列表
     */
    fun scanFolderForBooks(
        folder: File,
        config: ImportConfig = ImportConfig(),
    ): List<File> {
        val result = mutableListOf<File>()
        scanFolderRecursive(folder, config, 0, result)
        return result.take(config.maxFilesPerImport)
    }

    private fun scanFolderRecursive(
        folder: File,
        config: ImportConfig,
        currentDepth: Int,
        result: MutableList<File>,
    ) {
        if (currentDepth > config.maxFolderDepth) return
        if (result.size >= config.maxFilesPerImport) return

        folder.listFiles()?.forEach { file ->
            if (file.isFile) {
                val extension = file.extension.lowercase()
                if (extension in config.supportedExtensions) {
                    result.add(file)
                }
            } else if (file.isDirectory) {
                scanFolderRecursive(file, config, currentDepth + 1, result)
            }
        }
    }

    /**
     * 批量导入文件
     */
    suspend fun importBooks(
        files: List<File>,
        config: ImportConfig = ImportConfig(),
        progressCallback: ImportProgressCallback? = null,
    ): ImportResult = withContext(Dispatchers.IO) {
        var successCount = 0
        var skippedCount = 0
        var failedCount = 0
        val failedFiles = mutableListOf<String>()
        var firstDuplicateBookId: Long? = null

        progressCallback?.onImportStart(files.size)

        files.forEachIndexed { index, file ->
            progressCallback?.onFileImporting(file.name, index + 1, files.size)

            try {
                importBook(file, config.copyToAppDir)
                successCount++
                progressCallback?.onFileImported(file.name, true)
            } catch (e: BookAlreadyExistsException) {
                skippedCount++
                if (firstDuplicateBookId == null) {
                    firstDuplicateBookId = e.bookId
                }
                progressCallback?.onFileImported(file.name, false, e.message)
            } catch (e: Exception) {
                failedCount++
                failedFiles.add(file.name)
                progressCallback?.onFileImported(file.name, false, e.message)
            }
        }

        ImportResult(
            successCount = successCount,
            skippedCount = skippedCount,
            failedCount = failedCount,
            failedFiles = failedFiles,
            firstDuplicateBookId = firstDuplicateBookId,
        )
    }

    private companion object {
        private const val EAGER_SEARCH_INDEX_MAX_BYTES = 2 * 1024 * 1024L
    }
}
