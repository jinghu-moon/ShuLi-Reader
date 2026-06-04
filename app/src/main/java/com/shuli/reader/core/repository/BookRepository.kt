package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookChapterDao
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.dao.ReadingProgressDao
import com.shuli.reader.core.database.entity.BookContentIndexEntity
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookShelfRow
import com.shuli.reader.core.parser.ByteWindowReader
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.TxtParser
import com.shuli.reader.core.parser.DecodedSegment
import com.shuli.reader.core.parser.StreamDecoder
import com.shuli.reader.core.parser.Utf16ToByteCodec
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.UUID

class BookRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val readingProgressDao: ReadingProgressDao,
    private val txtParser: TxtParser,
    private val epubParser: EpubParser,
    private val byteWindowReader: ByteWindowReader,
    private val booksDir: java.io.File,
    /** 应用级协程作用域：用于后台建索引（v4） */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private val applicationScope: CoroutineScope = GlobalScope,
) {
    private val duplicateChecker = DuplicateChecker(bookDao)
    private val streamDecoder = StreamDecoder()
    /** 按 bookId 串行化 ensureChapterIndex，防止 importBook + openBook 并发重复构建 */
    private val chapterIndexMutexes = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()

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

        // 无索引时：按章节加载文本并构建索引
        val charset = Charset.forName(book.charset)
        val chapters = bookChapterDao.getChapters(bookId)
        if (chapters.isEmpty()) return@withContext emptyList()

        val rows = chapters.map { ch ->
            val window = byteWindowReader.loadRange(file, ch.byteStart, ch.byteEnd)
            val segment = streamDecoder.decode(window, charset)
            BookContentIndexEntity(
                bookId = bookId,
                chapterIndex = ch.chapterIndex,
                chapterTitle = ch.title,
                content = segment.text,
                byteStart = ch.byteStart,
                charset = book.charset,
                utf16ToByteBlob = Utf16ToByteCodec.encode(segment.utf16IndexToByte),
            )
        }
        bookDao.replaceBookContentIndex(bookId, rows)
        searchIndexedContent(rows, query)
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
        byteOffset: Long,
        chapterTitle: String?,
        progress: Float,
    ) {
        bookDao.updateReadingPosition(
            bookId = bookId,
            byteOffset = byteOffset,
            chapterTitle = chapterTitle,
            progress = progress,
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
     * 解析书籍内容（EPUB 用）。
     * TXT 不再走全量解析，请使用 loadByteWindow / loadChapterText。
     */
    suspend fun parseBookContent(file: File, fullParse: Boolean = false): BookContent {
        return when {
            file.name.endsWith(".epub", ignoreCase = true) -> {
                if (fullParse) epubParser.parseWithContent(file) else epubParser.parse(file)
            }
            else -> throw IllegalArgumentException("Unsupported file format for parseBookContent: ${file.name}")
        }
    }

    suspend fun getChapterText(file: File, chapterIndex: Int, bookContent: BookContent): String {
        return getChapterText(file, chapterIndex, bookContent.chapters, bookContent.bookId)
    }

    /**
     * 获取章节正文。
     * EPUB: 通过 spineIndex 解析 XHTML
     * TXT: 从 DB 读取字节偏移，按需读取
     */
    suspend fun getChapterText(
        file: File,
        chapterIndex: Int,
        chapters: List<com.shuli.reader.core.parser.model.Chapter>,
        bookId: Long = 0L,
    ): String = withContext(Dispatchers.IO) {
        val chapter = chapters.getOrNull(chapterIndex) ?: return@withContext ""
        return@withContext if (file.name.endsWith(".epub", ignoreCase = true)) {
            epubParser.parseChapter(file, chapter.spineIndex)
        } else {
            // TXT: 从 DB 单条查询字节偏移，按需读取
            if (bookId > 0L) {
                val chapterEntity = bookChapterDao.getChapter(bookId, chapterIndex)
                if (chapterEntity != null) {
                    return@withContext readTxtChapterByEntity(file, chapterEntity)
                }
            }
            // 兜底：用字节偏移直接读取
            android.util.Log.w("BookRepository", "Fallback: no chapter index for bookId=$bookId, chapterIndex=$chapterIndex")
            val charset = txtParser.detectCharset(file)
            val window = byteWindowReader.loadRange(file, chapter.byteStart, chapter.byteEnd)
            streamDecoder.decode(window, charset).text
        }
    }

    /**
     * 按 BookChapterEntity 的字节偏移读取 TXT 章节。
     */
    private fun readTxtChapterByEntity(
        file: File,
        chapter: com.shuli.reader.core.database.entity.BookChapterEntity,
    ): String {
        return try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(chapter.byteStart)
                val byteLength = (chapter.byteEnd - chapter.byteStart).toInt()
                val bytes = ByteArray(byteLength)
                raf.readFully(bytes)
                String(bytes, java.nio.charset.Charset.forName(chapter.charset))
            }
        } catch (e: Exception) {
            android.util.Log.e("BookRepository", "Failed to read chapter by byte offset: ${file.absolutePath}, chapterIndex=${chapter.chapterIndex}", e)
            ""
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
                replaceSearchIndex(bookId, targetFile)
            }
        }
        // v4：导入后立即在 applicationScope 后台异步构建章节目录索引，
        // 不阻塞导入返回；首次打开时若仍未完成会通过 chapterIndexMutexes 复用同一 Job。
        applicationScope.launch {
            runCatching { ensureChapterIndex(bookId) }
                .onFailure { android.util.Log.e("BookRepository", "Async ensureChapterIndex failed for bookId=$bookId", it) }
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

    /**
     * 确保章节目录索引存在且有效。
     * 通过文件指纹（fileSize + lastModified）判断是否需要重建。
     * 如果 DB 中已有匹配指纹的章节记录，则跳过解析。
     * 使用 Mutex 按 bookId 串行化，防止 importBook + openBook 并发重复构建。
     */
    suspend fun ensureChapterIndex(bookId: Long) {
        val mutex = chapterIndexMutexes.getOrPut(bookId) { Mutex() }
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val book = bookDao.getBookById(bookId).first() ?: return@withContext
                val file = File(book.filePath)
                if (!file.exists()) return@withContext

                val currentSize = file.length()
                val currentModified = file.lastModified()

                // 指纹匹配且已有章节记录 → 跳过
                val sizeMatch = book.chapterIndexFileSize == currentSize
                val modifiedMatch = book.chapterIndexLastModified == currentModified
                val builtBefore = book.chapterIndexBuiltAt > 0L
                val hasChapters = bookChapterDao.countChapters(bookId) > 0

                if (sizeMatch && modifiedMatch && builtBefore && hasChapters) {
                    android.util.Log.d("BookRepository", "ensureChapterIndex: fingerprint HIT, skip rebuild")
                    return@withContext
                }

                // 指纹未命中，记录原因
                android.util.Log.w("BookRepository", buildString {
                    append("ensureChapterIndex: fingerprint MISS, rebuilding. ")
                    append("sizeMatch=$sizeMatch (db=${book.chapterIndexFileSize}, file=$currentSize), ")
                    append("modifiedMatch=$modifiedMatch (db=${book.chapterIndexLastModified}, file=$currentModified), ")
                    append("builtBefore=$builtBefore, hasChapters=$hasChapters")
                })

                // 解析章节目录
                val chapters = when {
                    file.name.endsWith(".txt", ignoreCase = true) -> txtParser.parseChapterIndex(file)
                    file.name.endsWith(".epub", ignoreCase = true) -> epubParser.parseChapterIndex(file)
                    else -> emptyList()
                }

                if (chapters.isNotEmpty()) {
                    // 填充 bookId 并持久化
                    val withBookId = chapters.map { it.copy(bookId = bookId) }
                    bookChapterDao.replaceChapters(bookId, withBookId)

                    // v4：TXT 章节都使用同一个 charset；EPUB 的章节 charset 为占位 UTF-8
                    val txtCharset = if (file.name.endsWith(".txt", ignoreCase = true)) {
                        chapters.firstOrNull()?.charset ?: "UTF-8"
                    } else {
                        book.charset
                    }
                    val totalChars = chapters.sumOf { it.wordCount.toLong() }

                    // 更新指纹、总章数、charset、字符总数估算
                    bookDao.updateBook(
                        book.copy(
                            chapterIndexFileSize = currentSize,
                            chapterIndexLastModified = currentModified,
                            chapterIndexBuiltAt = System.currentTimeMillis(),
                            totalChapterNum = chapters.size,
                            charset = txtCharset,
                            estimatedTotalChars = totalChars,
                        )
                    )
                }
            }
        }
    }

    /**
     * 获取已持久化的章节目录列表。
     * 如果索引不存在则返回空列表（调用方应先调用 ensureChapterIndex）。
     */
    suspend fun getChapterIndex(bookId: Long): List<com.shuli.reader.core.database.entity.BookChapterEntity> {
        return bookChapterDao.getChapters(bookId)
    }

    /**
     * 从指定字节位置加载文本窗口（含 utf16IndexToByte 映射）。
     * 用途：续读首屏、进度条跳转后展示。
     */
    suspend fun loadByteWindow(bookId: Long, byteOffset: Long): DecodedSegment? {
        val book = bookDao.getBookById(bookId).first() ?: return null
        val file = File(book.filePath)
        if (!file.exists()) return null
        val charset = Charset.forName(book.charset)
        val window = byteWindowReader.loadWindow(file, byteOffset)
        return streamDecoder.decode(window, charset)
    }

    /**
     * 加载指定章节的文本（含 utf16IndexToByte 映射）。
     * 用途：章节切换。
     */
    suspend fun loadChapterText(bookId: Long, chapterIndex: Int): DecodedSegment? {
        val chapter = bookChapterDao.getChapter(bookId, chapterIndex) ?: return null
        val book = bookDao.getBookById(bookId).first() ?: return null
        val file = File(book.filePath)
        if (!file.exists()) return null
        val charset = Charset.forName(chapter.charset)
        val window = byteWindowReader.loadRange(file, chapter.byteStart, chapter.byteEnd)
        return streamDecoder.decode(window, charset)
    }

    /**
     * 根据字节偏移解析当前章节标题。
     * 用途：续读时显示章节标题。
     */
    suspend fun resolveChapterTitle(bookId: Long, byteOffset: Long): String? {
        val chapters = bookChapterDao.getChapters(bookId)
        if (chapters.isEmpty()) return null
        return chapters.lastOrNull { it.byteStart <= byteOffset }?.title
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
        val book = bookDao.getBookById(bookId).first() ?: return
        val charset = Charset.forName(book.charset)
        val chapters = bookChapterDao.getChapters(bookId)
        if (chapters.isEmpty()) return
        val rows = chapters.map { ch ->
            val window = byteWindowReader.loadRange(file, ch.byteStart, ch.byteEnd)
            val segment = streamDecoder.decode(window, charset)
            BookContentIndexEntity(
                bookId = bookId,
                chapterIndex = ch.chapterIndex,
                chapterTitle = ch.title,
                content = segment.text,
                byteStart = ch.byteStart,
                charset = book.charset,
                utf16ToByteBlob = Utf16ToByteCodec.encode(segment.utf16IndexToByte),
            )
        }
        bookDao.replaceBookContentIndex(bookId, rows)
    }

    private fun searchIndexedContent(rows: List<BookContentIndexEntity>, query: String): List<SearchResult> {
        return rows.flatMap { row ->
            if (row.utf16ToByteBlob.isNotEmpty()) {
                val utf16Map = Utf16ToByteCodec.decode(row.utf16ToByteBlob)
                searchChapterWithBlob(
                    chapterIndex = row.chapterIndex,
                    chapterTitle = row.chapterTitle,
                    chapterByteStart = row.byteStart,
                    chapterText = row.content,
                    utf16Map = utf16Map,
                    query = query,
                )
            } else {
                // 旧数据兜底（无 blob 时退化为 O(n) per match）
                val charset = Charset.forName(row.charset)
                searchChapterLegacy(
                    chapterIndex = row.chapterIndex,
                    chapterTitle = row.chapterTitle,
                    chapterByteStart = row.byteStart,
                    charset = charset,
                    chapterText = row.content,
                    query = query,
                )
            }
        }
    }

    /** O(1) per match：通过 utf16IndexToByte 映射表直接查字节偏移 */
    private fun searchChapterWithBlob(
        chapterIndex: Int,
        chapterTitle: String,
        chapterByteStart: Long,
        chapterText: String,
        utf16Map: IntArray,
        query: String,
    ): List<SearchResult> {
        val lowerChapter = chapterText.lowercase()
        val lowerQuery = query.lowercase()
        val results = mutableListOf<SearchResult>()
        var searchFrom = 0

        while (searchFrom < lowerChapter.length) {
            val matchIndex = lowerChapter.indexOf(lowerQuery, searchFrom)
            if (matchIndex == -1) break

            val byteOffset = if (matchIndex < utf16Map.size) {
                chapterByteStart + utf16Map[matchIndex].toLong()
            } else {
                chapterByteStart // 防御性兜底
            }
            val contextStart = (matchIndex - SEARCH_CONTEXT_RADIUS).coerceAtLeast(0)
            val contextEnd = (matchIndex + query.length + SEARCH_CONTEXT_RADIUS).coerceAtMost(chapterText.length)

            results += SearchResult(
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                byteOffset = byteOffset,
                context = chapterText.substring(contextStart, contextEnd),
                matchedText = chapterText.substring(matchIndex, matchIndex + query.length),
            )
            searchFrom = matchIndex + query.length.coerceAtLeast(1)
        }

        return results
    }

    /** 旧数据兜底：O(n) per match，仅在无 utf16ToByteBlob 时使用 */
    private fun searchChapterLegacy(
        chapterIndex: Int,
        chapterTitle: String,
        chapterByteStart: Long,
        charset: Charset,
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

            val byteOffset = chapterByteStart +
                chapterText.substring(0, matchIndex).toByteArray(charset).size.toLong()
            val contextStart = (matchIndex - SEARCH_CONTEXT_RADIUS).coerceAtLeast(0)
            val contextEnd = (matchIndex + query.length + SEARCH_CONTEXT_RADIUS).coerceAtMost(chapterText.length)

            results += SearchResult(
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                byteOffset = byteOffset,
                context = chapterText.substring(contextStart, contextEnd),
                matchedText = chapterText.substring(matchIndex, matchIndex + query.length),
            )
            searchFrom = matchIndex + query.length.coerceAtLeast(1)
        }

        return results
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
