package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.TagDao
import com.shuli.reader.core.database.dao.TagWithCount
import com.shuli.reader.core.database.entity.BookTagCrossRef
import com.shuli.reader.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

class TagRepository(
    private val tagDao: TagDao,
    private val bookDao: com.shuli.reader.core.database.dao.BookDao? = null,
) {
    fun getTagsForBook(bookId: Long): Flow<List<TagEntity>> =
        tagDao.getTagsForBook(bookId)

    fun getAllTagsWithCount(): Flow<List<TagWithCount>> =
        tagDao.getAllTagsWithCount()

    fun getTopTags(limit: Int = 10): Flow<List<TagWithCount>> =
        tagDao.getTopTags(limit)

    fun searchTagsPaged(query: String, limit: Int = 50, offset: Int = 0): Flow<List<TagWithCount>> =
        tagDao.searchTagsPaged(query, limit, offset)

    suspend fun searchTagsByPrefix(prefix: String): List<TagWithCount> =
        tagDao.searchTagsByPrefix(prefix)

    suspend fun addTagToBook(bookId: Long, tagName: String) {
        val trimmed = tagName.trim()
        if (trimmed.isEmpty() || trimmed.length > 20) return

        val colorIndex = Math.abs(trimmed.hashCode()) % TAG_COLOR_COUNT
        var tag = tagDao.getTagByName(trimmed)
        if (tag == null) {
            val newId = tagDao.insertTag(
                TagEntity(name = trimmed, colorIndex = colorIndex)
            )
            tag = TagEntity(id = newId, name = trimmed, colorIndex = colorIndex)
        }

        tagDao.addTagToBook(BookTagCrossRef(bookId = bookId, tagId = tag.id))
        markBookDirty(bookId)
    }

    suspend fun removeTagFromBook(bookId: Long, tagId: Long) {
        tagDao.removeTagFromBook(bookId, tagId)
        markBookDirty(bookId)
    }

    suspend fun renameTag(tagId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.length > 20) return
        val existing = tagDao.getTagByName(trimmed)
        if (existing != null && existing.id != tagId) return
        tagDao.renameTag(tagId, trimmed)
        markAffectedBooksDirty(tagId)
    }

    suspend fun deleteTag(tagId: Long) {
        val affectedBookIds = tagDao.getBookIdsForTag(tagId)
        tagDao.deleteTag(tagId)
        for (bookId in affectedBookIds) {
            markBookDirty(bookId)
        }
    }

    suspend fun mergeTags(sourceTagId: Long, targetTagId: Long) {
        val bookIds = tagDao.getBookIdsForTag(sourceTagId)
        for (bookId in bookIds) {
            tagDao.addTagToBook(BookTagCrossRef(bookId = bookId, tagId = targetTagId))
        }
        tagDao.deleteTag(sourceTagId)
        for (bookId in bookIds) {
            markBookDirty(bookId)
        }
    }

    private suspend fun markBookDirty(bookId: Long) {
        val book = bookDao?.getBookByIdSync(bookId) ?: return
        bookDao.updateBook(
            book.copy(
                isDirty = true,
                version = book.version + 1,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun markAffectedBooksDirty(tagId: Long) {
        val bookIds = tagDao.getBookIdsForTag(tagId)
        for (bookId in bookIds) {
            markBookDirty(bookId)
        }
    }

    companion object {
        const val TAG_COLOR_COUNT = 6
    }
}
