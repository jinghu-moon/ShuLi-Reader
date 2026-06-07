package com.shuli.reader.feature.bookshelf

import android.content.Context
import android.net.Uri
import com.shuli.reader.core.repository.BookAlreadyExistsException
import com.shuli.reader.core.repository.BookImportRepository
import com.shuli.reader.core.repository.BookQueryRepository
import com.shuli.reader.core.repository.ImportConfig
import com.shuli.reader.core.repository.ImportResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 书架导入管理：书籍导入（单本/批量/文件夹）与结果反馈。
 *
 * 从 BookshelfViewModel 拆出，SRP —— 只负责"书籍导入"这一变更轴。
 */
internal class BookImportManager(
    private val bookImportRepository: BookImportRepository,
    private val bookQueryRepository: BookQueryRepository,
    private val scope: CoroutineScope,
    private val events: MutableSharedFlow<BookshelfEvent>,
) {

    fun onImportBook(context: Context, uri: Uri) {
        scope.launch {
            try {
                importSingleBook(context, uri)
                events.emit(BookshelfEvent.ShowMessage { it.bookshelf.importSuccess })
            } catch (e: BookAlreadyExistsException) {
                events.emit(BookshelfEvent.ShowMessage { strings -> strings.bookshelf.bookAlreadyInShelf })
                events.emit(BookshelfEvent.HighlightBook(e.bookId))
            } catch (e: Exception) {
                events.emit(BookshelfEvent.ShowMessage { strings -> strings.bookshelf.importFailed(e.toImportErrorMessage(strings)) })
            }
        }
    }

    fun onImportBooks(context: Context, uris: List<Uri>) {
        scope.launch {
            try {
                var successCount = 0
                var skippedCount = 0
                var failCount = 0
                var firstDuplicateId: Long? = null
                uris.forEach { uri ->
                    try {
                        importSingleBook(context, uri)
                        successCount++
                    } catch (e: BookAlreadyExistsException) {
                        skippedCount++
                        if (firstDuplicateId == null) {
                            firstDuplicateId = e.bookId
                        }
                    } catch (e: Exception) {
                        failCount++
                    }
                }
                val result = ImportResult(
                    successCount = successCount,
                    skippedCount = skippedCount,
                    failedCount = failCount,
                    firstDuplicateBookId = firstDuplicateId,
                )
                showImportResult(result)
            } catch (e: Exception) {
                events.emit(BookshelfEvent.ShowMessage { strings -> strings.bookshelf.importFailed(e.toImportErrorMessage(strings)) })
            }
        }
    }

    fun onImportFolder(context: Context, uri: Uri) {
        scope.launch {
            try {
                val folder = File(uri.path ?: throw InvalidFolderPathException())
                if (!folder.isDirectory) {
                    events.emit(BookshelfEvent.ShowMessage { it.bookshelf.importFailed(it.bookshelf.invalidFolder) })
                    return@launch
                }

                val config = ImportConfig()
                val files = bookImportRepository.scanFolderForBooks(folder, config)

                if (files.isEmpty()) {
                    events.emit(BookshelfEvent.ShowMessage { it.bookshelf.importFailed(it.bookshelf.noImportableFiles) })
                    return@launch
                }

                val result = bookImportRepository.importBooks(files, config)
                showImportResult(result)
            } catch (e: Exception) {
                events.emit(BookshelfEvent.ShowMessage { strings -> strings.bookshelf.importFailed(e.toImportErrorMessage(strings)) })
            }
        }
    }

    private suspend fun importSingleBook(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw UnableToReadFileException()

        val fileName = getFileName(context, uri)
        val tempFile = File(context.cacheDir, fileName)

        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        bookImportRepository.importBook(tempFile)
        tempFile.delete()
    }

    private suspend fun showImportResult(result: ImportResult) {
        val message: (com.shuli.reader.core.i18n.AppStrings) -> String = when {
            result.isAllSuccess -> { strings -> strings.bookshelf.importSuccessCount(result.successCount) }
            result.hasSkipped && !result.hasFailed -> { strings -> strings.bookshelf.importSuccessWithSkipped(result.successCount, result.skippedCount) }
            result.hasFailed && !result.hasSkipped -> { strings -> strings.bookshelf.importSuccessWithFailed(result.successCount, result.failedCount) }
            else -> { strings -> strings.bookshelf.importSuccessWithBoth(result.successCount, result.skippedCount, result.failedCount) }
        }
        events.emit(BookshelfEvent.ShowMessage(message))
        result.firstDuplicateBookId?.let {
            events.emit(BookshelfEvent.HighlightBook(it))
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "book.txt"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
        return name
    }
}
