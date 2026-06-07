package com.shuli.reader.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shuli.reader.core.database.entity.BookTagCrossRef
import com.shuli.reader.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

data class TagWithCount(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "color_index") val colorIndex: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "usage_count") val usageCount: Int,
)

@Dao
interface TagDao {
    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN book_tag_cross_ref r ON t.id = r.tag_id
        WHERE r.book_id = :bookId
        ORDER BY r.added_at ASC
    """)
    fun getTagsForBook(bookId: Long): Flow<List<TagEntity>>

    @Query("""
        SELECT t.id, t.name, t.color_index, t.created_at, COUNT(r.book_id) as usage_count
        FROM tags t
        LEFT JOIN book_tag_cross_ref r ON t.id = r.tag_id
        GROUP BY t.id
        ORDER BY usage_count DESC
        LIMIT 100
    """)
    fun getAllTagsWithCount(): Flow<List<TagWithCount>>

    @Query("""
        SELECT t.id, t.name, t.color_index, t.created_at, COUNT(r.book_id) as usage_count
        FROM tags t
        LEFT JOIN book_tag_cross_ref r ON t.id = r.tag_id
        GROUP BY t.id
        ORDER BY usage_count DESC
        LIMIT :limit
    """)
    fun getTopTags(limit: Int = 10): Flow<List<TagWithCount>>

    @Query("""
        SELECT t.id, t.name, t.color_index, t.created_at, COUNT(r.book_id) as usage_count
        FROM tags t
        LEFT JOIN book_tag_cross_ref r ON t.id = r.tag_id
        WHERE t.name LIKE :prefix || '%'
        GROUP BY t.id
        ORDER BY usage_count DESC, t.name ASC
        LIMIT 10
    """)
    suspend fun searchTagsByPrefix(prefix: String): List<TagWithCount>

    @Query("""
        SELECT t.id, t.name, t.color_index, t.created_at, COUNT(r.book_id) as usage_count
        FROM tags t
        LEFT JOIN book_tag_cross_ref r ON t.id = r.tag_id
        WHERE t.name LIKE '%' || :query || '%'
        GROUP BY t.id
        ORDER BY usage_count DESC, t.name ASC
        LIMIT :limit OFFSET :offset
    """)
    fun searchTagsPaged(query: String, limit: Int, offset: Int): Flow<List<TagWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToBook(crossRef: BookTagCrossRef)

    @Query("DELETE FROM book_tag_cross_ref WHERE book_id = :bookId AND tag_id = :tagId")
    suspend fun removeTagFromBook(bookId: Long, tagId: Long)

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Query("UPDATE tags SET name = :newName WHERE id = :tagId")
    suspend fun renameTag(tagId: Long, newName: String)

    @Query("SELECT book_id FROM book_tag_cross_ref WHERE tag_id = :tagId")
    suspend fun getBookIdsForTag(tagId: Long): List<Long>

    @Query("DELETE FROM book_tag_cross_ref WHERE tag_id = :tagId")
    suspend fun removeAllBookTagRefs(tagId: Long)

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsSync(): List<TagEntity>

    @Query("SELECT * FROM book_tag_cross_ref")
    suspend fun getAllBookTagCrossRefs(): List<BookTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagSync(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToBookSync(crossRef: BookTagCrossRef)
}
