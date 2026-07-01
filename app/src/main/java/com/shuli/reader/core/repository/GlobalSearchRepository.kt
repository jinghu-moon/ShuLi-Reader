package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/**
 * 全库全文搜索引擎。
 * SQLite LIKE 候选过滤 + SearchTextMatcher 精确匹配 + Flow 事件流 + early stop。
 */
class GlobalSearchRepository(
    private val bookDao: BookDao,
) {

    companion object {
        private const val PAGE_SIZE = 50
        private const val DEFAULT_MAX_RESULTS = 200
        private const val DEFAULT_PER_BOOK_LIMIT = DEFAULT_MAX_RESULTS
        private const val MAX_MATCHES_PER_CHAPTER = 10
    }

    suspend fun getSearchableBooks(): List<GlobalSearchBookOption> = withContext(Dispatchers.IO) {
        val indexedBookIds = bookDao.getIndexedBookIds().toSet()
        bookDao.getAllBooksSync().map { book ->
            GlobalSearchBookOption(
                id = book.id,
                title = book.title,
                author = book.author,
                isIndexed = book.id in indexedBookIds,
            )
        }
    }

    fun search(
        query: String,
        scope: GlobalSearchScope = GlobalSearchScope.All,
        maxResults: Int = DEFAULT_MAX_RESULTS,
        perBookLimit: Int = DEFAULT_PER_BOOK_LIMIT,
    ): Flow<GlobalSearchEvent> = flow {
        if (query.isBlank() || query.trim().length < 2) {
            emit(GlobalSearchEvent.Completed(totalResults = 0))
            return@flow
        }
        if (scope is GlobalSearchScope.Books && scope.bookIds.isEmpty()) {
            emit(GlobalSearchEvent.Completed(totalResults = 0))
            return@flow
        }

        val trimmedQuery = query.trim()
        val perBookCap = perBookLimit.coerceAtLeast(1)
        val perBookCounts = mutableMapOf<Long, Int>()
        var totalFound = 0
        var offset = 0
        var hasMore = true

        try {
            while (hasMore && totalFound < maxResults) {
                currentCoroutineContext().ensureActive()

                val page = withContext(Dispatchers.IO) {
                    when (scope) {
                        GlobalSearchScope.All -> bookDao.searchGlobalContent(
                            trimmedQuery,
                            PAGE_SIZE,
                            offset,
                        )
                        is GlobalSearchScope.Books -> bookDao.searchGlobalContentInBooks(
                            trimmedQuery,
                            scope.bookIds,
                            PAGE_SIZE,
                            offset,
                        )
                    }
                }

                if (page.isEmpty()) {
                    hasMore = false
                    break
                }

                for (match in page) {
                    if (totalFound >= maxResults) break
                    currentCoroutineContext().ensureActive()

                    val currentBookCount = perBookCounts[match.bookId] ?: 0
                    if (currentBookCount >= perBookCap) continue

                    val matchResults = SearchTextMatcher.match(
                        chapterText = match.content,
                        query = trimmedQuery,
                        chapterIndex = match.chapterIndex,
                        chapterTitle = match.chapterTitle,
                        chapterByteStart = match.byteStart,
                        utf16ToByteBlob = match.utf16ToByteBlob,
                        charset = charsetOrUtf8(match.charset),
                        maxMatches = minOf(
                            maxResults - totalFound,
                            perBookCap - currentBookCount,
                            MAX_MATCHES_PER_CHAPTER,
                        ),
                    )

                    for (mr in matchResults) {
                        if (totalFound >= maxResults) break
                        emit(
                            GlobalSearchEvent.Result(
                                GlobalSearchResult(
                                    bookId = match.bookId,
                                    bookTitle = match.bookTitle,
                                    author = match.author,
                                    chapterIndex = mr.chapterIndex,
                                    chapterTitle = mr.chapterTitle,
                                    byteOffset = mr.byteOffset,
                                    context = mr.context,
                                    matchedText = mr.matchedText,
                                )
                            )
                        )
                        totalFound++
                        perBookCounts[match.bookId] = (perBookCounts[match.bookId] ?: 0) + 1
                    }
                }

                offset += page.size
                emit(GlobalSearchEvent.Progress(processedChapters = offset, resultsFound = totalFound))

                if (page.size < PAGE_SIZE) {
                    hasMore = false
                }
            }

            emit(GlobalSearchEvent.Completed(totalResults = totalFound))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(GlobalSearchEvent.Error(e.message ?: "搜索失败"))
        }
    }

    private fun charsetOrUtf8(name: String): Charset {
        return runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
    }
}
