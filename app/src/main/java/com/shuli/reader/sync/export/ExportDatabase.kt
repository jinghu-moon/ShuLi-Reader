// Part of T-30 ZIP 明文导出
package com.shuli.reader.sync.export

import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity

/**
 * 导出用数据库抽象接口。
 * 遵循依赖倒置原则（DIP），BackupExporter 依赖抽象而非具体实现。
 */
interface ExportDatabase {
    suspend fun getAllBooks(): List<BookEntity>
    suspend fun getAllBookmarks(): List<BookmarkEntity>
    suspend fun getAllNotes(): List<NoteEntity>
    suspend fun getAllProgress(): List<ReadingProgressEntity>
}
