// Part of 备份导入数据库接口
package com.shuli.reader.sync.export

import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookTagCrossRef
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import com.shuli.reader.core.database.entity.TagEntity

/**
 * 导入用数据库抽象接口。
 * 遵循依赖倒置原则（DIP），BackupImporter 依赖抽象而非具体实现。
 * 支持全部 4 种实体的 upsert/clear/事务操作。
 */
interface ImportDatabase : ExportDatabase {
    // --- Book ---
    suspend fun upsertBook(book: BookEntity)
    suspend fun clearBooks()
    suspend fun getExistingBookIds(): Set<Long>

    // --- Bookmark ---
    suspend fun upsertBookmark(bookmark: BookmarkEntity)
    suspend fun clearBookmarks()
    suspend fun getExistingBookmarkIds(): Set<Long>

    // --- Note ---
    suspend fun upsertNote(note: NoteEntity)
    suspend fun clearNotes()
    suspend fun getExistingNoteIds(): Set<Long>

    // --- ReadingProgress ---
    suspend fun upsertProgress(progress: ReadingProgressEntity)
    suspend fun clearProgress()
    suspend fun getExistingProgressBookIds(): Set<Long>

    // --- Tags (P1) ---
    suspend fun insertTag(tag: TagEntity): Long
    suspend fun addTagToBook(crossRef: BookTagCrossRef)

    // --- ReadingSession ---
    suspend fun upsertReadingSession(session: com.shuli.reader.core.database.entity.ReadingSessionEntity)
    suspend fun clearReadingSessions()

    // --- Transaction ---
    /** 在数据库事务内执行 [block]，失败时自动回滚 */
    suspend fun runInTransaction(block: suspend () -> Unit)
}
