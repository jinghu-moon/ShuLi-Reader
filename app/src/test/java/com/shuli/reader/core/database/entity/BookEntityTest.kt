package com.shuli.reader.core.database.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-01 BookEntity sync fields extension
class BookEntityTest {

    @Test
    fun `BookEntity has bookKey, fastHash, isDirty, version, syncedVersion fields`() {
        val entity = BookEntity(
            id = 0L,
            title = "测试书",
            author = "作者",
            filePath = "/path/to/book.txt",
            fileType = "TXT",
            fileSize = 1000L,
            coverPath = null,
            lastReadTime = null,
            addedTime = System.currentTimeMillis(),
            bookKey = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            fastHash = "abc123",
            fullHash = null,
            isDirty = false,
            version = 1,
            syncedVersion = 0,
            updatedAt = 1710000000000L,
            remoteBookKey = null
        )
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", entity.bookKey)
        assertEquals("abc123", entity.fastHash)
        assertNull(entity.fullHash)
        assertFalse(entity.isDirty)
        assertEquals(1, entity.version)
        assertEquals(0, entity.syncedVersion)
        assertEquals(1710000000000L, entity.updatedAt)
        assertNull(entity.remoteBookKey)
    }

    @Test
    fun `BookEntity sync fields have correct defaults`() {
        val entity = BookEntity(
            id = 0L,
            title = "测试书",
            author = "作者",
            filePath = "/path/to/book.txt",
            fileType = "TXT",
            fileSize = 1000L,
            coverPath = null,
            lastReadTime = null,
            addedTime = System.currentTimeMillis(),
            bookKey = "test-key",
            fastHash = "hash",
            updatedAt = System.currentTimeMillis()
        )
        assertFalse(entity.isDirty)
        assertEquals(1, entity.version)
        assertEquals(0, entity.syncedVersion)
        assertNull(entity.fullHash)
        assertNull(entity.remoteBookKey)
    }
}
