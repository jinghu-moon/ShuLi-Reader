// Part of 备份导入数据库接口
package com.shuli.reader.sync.export

import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity

/**
 * 导入用数据库抽象接口。
 * 遵循依赖倒置原则（DIP），BackupImporter 依赖抽象而非具体实现。
 * 支持全部 4 种实体的 upsert/clear/事务操作。
 */
interface ImportDatabase : ExportDatabase {
    // --- Book ---
    suspend fun upsertBook(book: BookEntity)
    suspend fun clearBooks()

    // --- Bookmark ---
    suspend fun upsertBookmark(bookmark: BookmarkEntity)
    suspend fun clearBookmarks()

    // --- Note ---
    suspend fun upsertNote(note: NoteEntity)
    suspend fun clearNotes()

    // --- ReadingProgress ---
    suspend fun upsertProgress(progress: ReadingProgressEntity)
    suspend fun clearProgress()

    // --- Transaction ---
    /** 在数据库事务内执行 [block]，失败时自动回滚 */
    suspend fun runInTransaction(block: suspend () -> Unit)
}
