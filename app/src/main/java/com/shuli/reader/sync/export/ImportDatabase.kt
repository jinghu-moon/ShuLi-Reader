// Part of T-31 ZIP 导入
package com.shuli.reader.sync.export

import com.shuli.reader.core.database.entity.BookmarkEntity

/**
 * 导入用数据库抽象接口。
 * 遵循依赖倒置原则（DIP），ZipImporter 依赖抽象而非具体实现。
 */
interface ImportDatabase : ExportDatabase {
    /** 清空所有书签 */
    suspend fun clearBookmarks()

    /** 添加书签 */
    suspend fun addBookmark(bookmark: BookmarkEntity)
}
