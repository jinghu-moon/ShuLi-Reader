package com.shuli.reader.core.database.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-06 Room entity dirty flags
class BookmarkEntityTest {

    @Test
    fun `BookmarkEntity default isDirty is true for new items`() {
        val bm = BookmarkEntity(
            id = 1L,
            bookId = 1L,
            createdTime = System.currentTimeMillis(),
            byteOffset = 1000L,
            isDirty = true,
            version = 1,
            syncedVersion = 0,
            deleted = false,
            updatedAt = System.currentTimeMillis(),
            mergeSource = null,
        )
        assertTrue(bm.isDirty)
        assertFalse(bm.deleted)
    }

    @Test
    fun `NoteEntity has isDirty and deleted fields`() {
        val note = NoteEntity(
            id = 1L,
            bookId = 1L,
            createdTime = System.currentTimeMillis(),
            byteStart = 100L,
            byteEnd = 200L,
            noteText = "批注",
            isDirty = true,
            version = 1,
            syncedVersion = 0,
            deleted = false,
            updatedAt = System.currentTimeMillis(),
            mergeSource = null,
        )
        assertTrue(note.isDirty)
        assertFalse(note.deleted)
    }

    @Test
    fun `ReadingProgressEntity has sync fields`() {
        val progress = ReadingProgressEntity(
            id = 1L,
            bookId = 1L,
            pageIndex = 10,
            position = 500,
            updatedTime = System.currentTimeMillis(),
            isDirty = true,
            version = 1,
            syncedVersion = 0,
            deleted = false,
            updatedAt = System.currentTimeMillis(),
            mergeSource = null,
        )
        assertTrue(progress.isDirty)
        assertFalse(progress.deleted)
    }
}
