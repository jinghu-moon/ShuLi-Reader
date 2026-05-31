package com.shuli.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuli.reader.core.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdTime DESC")
    fun getBookmarksByBookId(bookId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    /** 清空所有书签（导入用） */
    @Query("DELETE FROM bookmarks")
    suspend fun deleteAllBookmarks()

    /** T-06: 查询脏书签（同步用） */
    @Query("SELECT * FROM bookmarks WHERE isDirty = 1 AND deleted = 0")
    suspend fun queryDirty(): List<BookmarkEntity>

    /** T-06: 查询所有未删除书签（同步用） */
    @Query("SELECT * FROM bookmarks WHERE deleted = 0")
    suspend fun queryAllActive(): List<BookmarkEntity>
}
