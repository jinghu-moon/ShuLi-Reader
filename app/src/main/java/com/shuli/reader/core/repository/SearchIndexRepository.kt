package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookChapterDao
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.entity.BookContentIndexEntity
import com.shuli.reader.core.parser.ByteWindowReader
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.StreamDecoder
import com.shuli.reader.core.parser.TxtParser
import com.shuli.reader.core.parser.Utf16ToByteCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

/**
 * 章节目录索引构建 + 全文搜索。
 * Actor: 全文搜索工程师、性能工程师
 */
class SearchIndexRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val txtParser: TxtParser,
    private val epubParser: EpubParser,
    private val byteWindowReader: ByteWindowReader,
) {
    private val streamDecoder = StreamDecoder()
    /** 按 bookId 串行化 ensureChapterIndex，防止 importBook + openBook 并发重复构建 */
    private val chapterIndexMutexes = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()

    fun backfillMissingIndexes(): Flow<SearchIndexBackfillProgress> = flow {
        val books = bookDao.getAllBooksSync()
        if (books.isEmpty()) {
            emit(
                SearchIndexBackfillProgress(
                    totalBooks = 0,
                    processedBooks = 0,
                    indexedBooks = 0,
                    skippedBooks = 0,
                    failedBooks = 0,
                    isRunning = false,
                    isCompleted = true,
                )
            )
            return@flow
        }

        var progress = SearchIndexBackfillProgress(totalBooks = books.size)
        emit(progress)

        books.forEach { book ->
            currentCoroutineContext().ensureActive()
            progress = progress.copy(
                currentBookTitle = book.title,
                isRunning = true,
                isCompleted = false,
            )
            emit(progress)

            val alreadyIndexed = bookDao.countBookContentIndex(book.id) > 0
            progress = if (alreadyIndexed) {
                progress.copy(
                    processedBooks = progress.processedBooks + 1,
                    skippedBooks = progress.skippedBooks + 1,
                )
            } else {
                val indexed = try {
                    refreshSearchIndex(book.id) && bookDao.countBookContentIndex(book.id) > 0
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }

                progress.copy(
                    processedBooks = progress.processedBooks + 1,
                    indexedBooks = progress.indexedBooks + if (indexed) 1 else 0,
                    failedBooks = progress.failedBooks + if (indexed) 0 else 1,
                )
            }

            val completed = progress.processedBooks >= progress.totalBooks
            emit(
                progress.copy(
                    currentBookTitle = if (completed) null else progress.currentBookTitle,
                    isRunning = !completed,
                    isCompleted = completed,
                )
            )
        }
    }.flowOn(Dispatchers.IO)

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

    suspend fun refreshSearchIndex(bookId: Long): Boolean {
        ensureChapterIndex(bookId)
        return withContext(Dispatchers.IO) {
            val book = bookDao.getBookById(bookId).first() ?: return@withContext false
            val file = File(book.filePath)
            if (!file.exists()) {
                bookDao.deleteBookContentIndex(bookId)
                return@withContext false
            }
            replaceSearchIndex(bookId, file)
            true
        }
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
                    android.util.Log.d("SearchIndexRepository", "ensureChapterIndex: fingerprint HIT, skip rebuild")
                    return@withContext
                }

                // 指纹未命中，记录原因
                android.util.Log.w("SearchIndexRepository", buildString {
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
            SearchTextMatcher.match(
                chapterText = row.content,
                query = query,
                chapterIndex = row.chapterIndex,
                chapterTitle = row.chapterTitle,
                chapterByteStart = row.byteStart,
                utf16ToByteBlob = row.utf16ToByteBlob,
                charset = Charset.forName(row.charset),
            ).map { it.toSearchResult() }
        }
    }

    private fun SearchTextMatcher.MatchResult.toSearchResult() = SearchResult(
        chapterIndex = chapterIndex,
        chapterTitle = chapterTitle,
        byteOffset = byteOffset,
        context = context,
        matchedText = matchedText,
    )
}

data class SearchIndexBackfillProgress(
    val totalBooks: Int,
    val processedBooks: Int = 0,
    val indexedBooks: Int = 0,
    val skippedBooks: Int = 0,
    val failedBooks: Int = 0,
    val currentBookTitle: String? = null,
    val isRunning: Boolean = true,
    val isCompleted: Boolean = false,
)
