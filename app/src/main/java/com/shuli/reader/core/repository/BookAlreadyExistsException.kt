package com.shuli.reader.core.repository

/**
 * 书籍已存在异常（去重检测时抛出）
 */
class BookAlreadyExistsException(val bookId: Long, val bookTitle: String) : Exception("Book already exists: $bookTitle")
