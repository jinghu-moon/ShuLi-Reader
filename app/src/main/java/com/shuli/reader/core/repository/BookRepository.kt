package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.ReadingProgressDao
import com.shuli.reader.core.database.entity.BookContentIndexEntity
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookShelfRow
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.TxtParser
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val txtParser: TxtParser,
    private val epubParser: EpubParser,
    private val booksDir: java.io.File,
) {
    private val duplicateChecker = DuplicateChecker(bookDao)

    /**
     * 在书籍正文中搜索关键词
     * 返回匹配结果列表，包含章节、偏移和上下文
     */
    suspend fun searchInBook(bookId: Long, query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        if (bookDao.countBookContentIndex(bookId) > 0) {
            return@withContext searchIndexedContent(
                rows = bookDao.searchBookContentIndex(bookId, query),
                query = query,
            )
        }

        val book = bookDao.getBookById(bookId).first() ?: return@withContext emptyList()
        val file = java.io.File(book.filePath)
        if (!file.exists()) return@withContext emptyList()

        val bookContent = parseBookContent(file, fullParse = true)
        bookDao.replaceBookContentIndex(bookId, bookContent.toContentIndexRows(bookId))
        searchBookContent(bookContent, query)
    }
    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    fun getBookshelfPage(limit: Int = DEFAULT_BOOKSHELF_PAGE_SIZE, offset: Int = 0): Flow<List<BookShelfRow>> {
        return bookDao.getBookRowsPage(
            limit = limit.coerceIn(1, MAX_BOOKSHELF_PAGE_SIZE),
            offset = offset.coerceAtLeast(0),
        )
    }

    fun getBookById(id: Long): Flow<BookEntity?> = bookDao.getBookById(id)

    fun searchBooks(query: String): Flow<List<BookEntity>> = bookDao.searchBooks(query)

    // --- Folder & Grouping Support ---

    fun getAllFolders(): Flow<List<com.shuli.reader.core.database.entity.FolderEntity>> = bookDao.getAllFolders()

    suspend fun createFolder(name: String): Long {
        return bookDao.insertFolder(
            com.shuli.reader.core.database.entity.FolderEntity(
                name = name,
            )
        )
    }

    suspend fun updateFolder(folder: com.shuli.reader.core.database.entity.FolderEntity) {
        bookDao.updateFolder(folder)
    }

    suspend fun deleteFolder(folderId: Long) {
        // 删除文件夹的同时，应将里面的书籍移出。这里暂时让 ViewModel 处理，或者在 DAO 层处理。
        // 这里提供简单删除。
        bookDao.deleteFolderById(folderId)
    }

    suspend fun moveBooksToFolder(bookIds: List<Long>, folderId: Long?) {
        bookDao.moveBooksToFolder(bookIds, folderId)
    }

    suspend fun updateBookPinnedSlot(bookId: Long, slot: Int?) {
        bookDao.updateBookPinnedSlot(bookId, slot)
    }

    suspend fun updateFolderPinnedSlot(folderId: Long, slot: Int?) {
        bookDao.updateFolderPinnedSlot(folderId, slot)
    }

    suspend fun clearAllPinnedSlots() {
        bookDao.clearAllBookPinnedSlots()
        bookDao.clearAllFolderPinnedSlots()
    }

    fun searchBooksPage(
        query: String,
        limit: Int = DEFAULT_BOOKSHELF_PAGE_SIZE,
        offset: Int = 0,
    ): Flow<List<BookShelfRow>> {
        val ftsQuery = query.toFtsPrefixQuery()
        if (ftsQuery.isBlank()) return flowOf(emptyList())
        return bookDao.searchBookRowsFtsPage(
            query = ftsQuery,
            limit = limit.coerceIn(1, MAX_BOOKSHELF_PAGE_SIZE),
            offset = offset.coerceAtLeast(0),
        )
    }

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun deleteBook(id: Long) {
        bookDao.deleteBookContentIndex(id)
        bookDao.deleteBookById(id)
    }

    suspend fun updateLastReadTime(bookId: Long) {
        bookDao.updateLastReadTime(bookId, System.currentTimeMillis())
    }

    suspend fun updateReadingProgress(bookId: Long, progress: Float) {
        bookDao.updateReadingProgress(bookId, progress)
    }

    suspend fun updateReadingPosition(
        bookId: Long,
        chapterIndex: Int,
        chapterPos: Int,
        chapterTitle: String?,
        chapterTime: Long,
        totalChapters: Int,
    ) {
        bookDao.updateReadingPosition(
            bookId = bookId,
            chapterIndex = chapterIndex,
            chapterPos = chapterPos,
            chapterTitle = chapterTitle,
            chapterTime = chapterTime,
            totalChapters = totalChapters,
        )
    }

    suspend fun toggleFavorite(bookId: Long) {
        val book = bookDao.getBookById(bookId).first()
        book?.let {
            bookDao.updateFavoriteStatus(bookId, !it.isFavorite)
        }
    }

    suspend fun setFavorite(bookId: Long, isFavorite: Boolean) {
        bookDao.updateFavoriteStatus(bookId, isFavorite)
    }

    suspend fun setCustomCoverPaletteIndex(bookId: Long, paletteIndex: Int?) {
        bookDao.updateCustomCoverPaletteIndex(bookId, paletteIndex)
    }

    fun getFavoriteBooks(): Flow<List<BookEntity>> = bookDao.getFavoriteBooks()

    suspend fun getReadingDuration(bookId: Long): Long {
        return readingProgressDao.getReadingDurationByBookId(bookId) ?: 0L
    }

    fun getReadingDurations(): Flow<Map<Long, Long>> {
        return readingProgressDao.getAllReadingDurations().map { tuples ->
            tuples.associate { it.bookId to it.totalDuration }
        }.flowOn(Dispatchers.Default)
    }

    fun getTodayReadingTime(): Flow<Long?> {
        val todayStart = getTodayStartTimestamp()
        return readingProgressDao.getTodayTotalReadingTime(todayStart)
    }

    /**
     * 解析书籍内容。
     * @param fullParse true 时读取全部章节正文（搜索索引用），false 时 EPUB 只做轻量目录解析。
     */
    suspend fun parseBookContent(file: File, fullParse: Boolean = false): BookContent {
        return when {
            file.name.endsWith(".txt", ignoreCase = true) -> txtParser.parse(file)
            file.name.endsWith(".epub", ignoreCase = true) -> {
                if (fullParse) epubParser.parseWithContent(file) else epubParser.parse(file)
            }
            else -> throw IllegalArgumentException("Unsupported file format: ${file.name}")
        }
    }

    suspend fun getChapterText(file: File, chapterIndex: Int, bookContent: BookContent): String = withContext(Dispatchers.IO) {
        val chapters = bookContent.chapters
        val chapter = chapters.getOrNull(chapterIndex) ?: return@withContext ""
        return@withContext if (file.name.endsWith(".epub", ignoreCase = true)) {
            // 使用 chapter.spineIndex（原始 spine 位置）而非 chapterIndex（过滤后索引）
            // 因为 parseChaptersWithContent 会跳过空白 spine 条目（封面、目录等），
            // 导致 chapters 列表索引与 spine 索引不一致
            epubParser.parseChapter(file, chapter.spineIndex)
        } else {
            val start = chapter.startIndex.coerceIn(0, bookContent.content.length)
            val end = chapter.endIndex.coerceIn(start, bookContent.content.length)
            bookContent.content.substring(start, end)
        }
    }

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
        )

        val bookId = bookDao.insertBook(bookEntity)
        if (targetFile.length() <= EAGER_SEARCH_INDEX_MAX_BYTES) {
            runCatching {
                replaceSearchIndex(bookId, targetFile)
            }
        }
        bookId
    }

    suspend fun refreshSearchIndex(bookId: Long): Boolean = withContext(Dispatchers.IO) {
        val book = bookDao.getBookById(bookId).first() ?: return@withContext false
        val file = File(book.filePath)
        if (!file.exists()) {
            bookDao.deleteBookContentIndex(bookId)
            return@withContext false
        }
        replaceSearchIndex(bookId, file)
        true
    }

    private fun getTodayStartTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
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

    private suspend fun replaceSearchIndex(bookId: Long, file: File) {
        val content = parseBookContent(file, fullParse = true)
        bookDao.replaceBookContentIndex(bookId, content.toContentIndexRows(bookId))
    }

    private fun searchBookContent(bookContent: BookContent, query: String): List<SearchResult> {
        val chapters = bookContent.searchChapters()
        if (chapters.isEmpty()) return emptyList()

        return chapters.flatMapIndexed { chapterIndex, chapter ->
            val chapterStart = chapter.startIndex.coerceIn(0, bookContent.content.length)
            val chapterEnd = chapter.endIndex.coerceIn(chapterStart, bookContent.content.length)
            searchChapter(
                chapterIndex = chapterIndex,
                chapterTitle = chapter.title,
                chapterStart = chapterStart,
                chapterText = bookContent.content.substring(chapterStart, chapterEnd),
                query = query,
            )
        }
    }

    private fun searchIndexedContent(rows: List<BookContentIndexEntity>, query: String): List<SearchResult> {
        return rows.flatMap { row ->
            searchChapter(
                chapterIndex = row.chapterIndex,
                chapterTitle = row.chapterTitle,
                chapterStart = row.chapterStart,
                chapterText = row.content,
                query = query,
            )
        }
    }

    private fun BookContent.searchChapters(): List<Chapter> {
        if (chapters.isNotEmpty()) return chapters
        return if (content.isNotBlank()) {
            listOf(Chapter(title = DEFAULT_CHAPTER_TITLE, startIndex = 0, endIndex = content.length))
        } else {
            emptyList()
        }
    }

    private fun searchChapter(
        chapterIndex: Int,
        chapterTitle: String,
        chapterStart: Int,
        chapterText: String,
        query: String,
    ): List<SearchResult> {
        val lowerChapter = chapterText.lowercase()
        val lowerQuery = query.lowercase()
        val results = mutableListOf<SearchResult>()
        var searchFrom = 0

        while (searchFrom < lowerChapter.length) {
            val matchIndex = lowerChapter.indexOf(lowerQuery, searchFrom)
            if (matchIndex == -1) break

            val absoluteOffset = chapterStart + matchIndex
            val contextStart = (matchIndex - SEARCH_CONTEXT_RADIUS).coerceAtLeast(0)
            val contextEnd = (matchIndex + query.length + SEARCH_CONTEXT_RADIUS).coerceAtMost(chapterText.length)

            results += SearchResult(
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                charOffset = absoluteOffset,
                matchStart = absoluteOffset,
                matchEnd = absoluteOffset + query.length,
                context = chapterText.substring(contextStart, contextEnd),
                matchedText = chapterText.substring(matchIndex, matchIndex + query.length),
            )
            searchFrom = matchIndex + query.length.coerceAtLeast(1)
        }

        return results
    }

    private fun BookContent.toContentIndexRows(bookId: Long): List<BookContentIndexEntity> {
        val chapters = searchChapters()
        if (chapters.isEmpty()) return emptyList()
        return chapters.mapIndexed { index, chapter ->
            val chapterStart = chapter.startIndex.coerceIn(0, content.length)
            val chapterEnd = chapter.endIndex.coerceIn(chapterStart, content.length)
            BookContentIndexEntity(
                bookId = bookId,
                chapterIndex = index,
                chapterTitle = chapter.title,
                chapterStart = chapterStart,
                content = content.substring(chapterStart, chapterEnd),
            )
        }
    }

    private fun String.toFtsPrefixQuery(): String {
        return trim()
            .split(Regex("\\s+"))
            .map { token -> token.filter { it.isLetterOrDigit() } }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ") { "$it*" }
    }

    private companion object {
        private const val SEARCH_CONTEXT_RADIUS = 20
        private const val DEFAULT_CHAPTER_TITLE = "Full Text"
        private const val DEFAULT_BOOKSHELF_PAGE_SIZE = 100
        private const val MAX_BOOKSHELF_PAGE_SIZE = 500
        private const val EAGER_SEARCH_INDEX_MAX_BYTES = 2 * 1024 * 1024L
    }
}

class BookAlreadyExistsException(val bookId: Long, val bookTitle: String) : Exception("Book already exists: $bookTitle")
