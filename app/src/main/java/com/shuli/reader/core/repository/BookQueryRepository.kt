package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookShelfRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 书籍查询 + 书架分页 + 基本 CRUD。
 * Actor: 书架产品
 */
class BookQueryRepository(
    private val bookDao: BookDao,
) {
    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    fun getBookshelfPage(limit: Int = DEFAULT_BOOKSHELF_PAGE_SIZE, offset: Int = 0): Flow<List<BookShelfRow>> {
        return bookDao.getBookRowsPage(
            limit = limit.coerceIn(1, MAX_BOOKSHELF_PAGE_SIZE),
            offset = offset.coerceAtLeast(0),
        )
    }

    fun getBookById(id: Long): Flow<BookEntity?> = bookDao.getBookById(id)

    fun searchBooks(query: String): Flow<List<BookEntity>> = bookDao.searchBooks(query)

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

    fun getFavoriteBooks(): Flow<List<BookEntity>> = bookDao.getFavoriteBooks()

    fun getBooksByTagPage(
        tagName: String,
        limit: Int = DEFAULT_BOOKSHELF_PAGE_SIZE,
        offset: Int = 0,
    ): Flow<List<BookShelfRow>> {
        return bookDao.getBookRowsByTagPage(
            tagName = tagName,
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

    private fun String.toFtsPrefixQuery(): String {
        return trim()
            .split(Regex("\\s+"))
            .map { token -> token.filter { it.isLetterOrDigit() } }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ") { "$it*" }
    }

    private companion object {
        private const val DEFAULT_BOOKSHELF_PAGE_SIZE = 100
        private const val MAX_BOOKSHELF_PAGE_SIZE = 500
    }
}
