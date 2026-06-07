package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.database.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * 文件夹 CRUD + 书籍归类。
 * Actor: 文件夹产品
 */
class FolderRepository(
    private val bookDao: BookDao,
) {
    fun getAllFolders(): Flow<List<FolderEntity>> = bookDao.getAllFolders()

    suspend fun createFolder(name: String): Long {
        return bookDao.insertFolder(FolderEntity(name = name))
    }

    suspend fun updateFolder(folder: FolderEntity) {
        bookDao.updateFolder(folder)
    }

    suspend fun deleteFolder(folderId: Long) {
        bookDao.deleteFolderById(folderId)
    }

    suspend fun moveBooksToFolder(bookIds: List<Long>, folderId: Long?) {
        bookDao.moveBooksToFolder(bookIds, folderId)
    }
}
