package com.shuli.reader.core.database.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSessionEntityTest {

    @Test
    fun defaultValues() {
        val entity = ReadingSessionEntity(
            bookId = 1L,
            chapterIndex = 0,
            startedAt = 1000L,
            endedAt = 2000L,
            durationSeconds = 1000L,
            dateKey = 20260609,
            hour = 10,
        )

        assertEquals(0L, entity.id)
        assertEquals(1L, entity.bookId)
        assertEquals(0, entity.chapterIndex)
        assertEquals(1000L, entity.startedAt)
        assertEquals(2000L, entity.endedAt)
        assertEquals(1000L, entity.durationSeconds)
        assertEquals(20260609, entity.dateKey)
        assertEquals(10, entity.hour)
        assertTrue(entity.isDirty)
        assertEquals(1, entity.version)
        assertEquals(0, entity.syncedVersion)
        assertFalse(entity.deleted)
        assertEquals(0L, entity.updatedAt)
        assertNull(entity.mergeSource)
    }

    @Test
    fun allFieldsAssignable() {
        val entity = ReadingSessionEntity(
            id = 42L,
            bookId = 7L,
            chapterIndex = 3,
            startedAt = 100_000L,
            endedAt = 200_000L,
            durationSeconds = 500L,
            dateKey = 20260101,
            hour = 23,
            isDirty = false,
            version = 5,
            syncedVersion = 4,
            deleted = true,
            updatedAt = 999_999L,
            mergeSource = "remote",
        )

        assertEquals(42L, entity.id)
        assertEquals(7L, entity.bookId)
        assertEquals(3, entity.chapterIndex)
        assertEquals(100_000L, entity.startedAt)
        assertEquals(200_000L, entity.endedAt)
        assertEquals(500L, entity.durationSeconds)
        assertEquals(20260101, entity.dateKey)
        assertEquals(23, entity.hour)
        assertFalse(entity.isDirty)
        assertEquals(5, entity.version)
        assertEquals(4, entity.syncedVersion)
        assertTrue(entity.deleted)
        assertEquals(999_999L, entity.updatedAt)
        assertEquals("remote", entity.mergeSource)
    }

    @Test
    fun copyOnlyChangesTargetField() {
        val original = ReadingSessionEntity(
            bookId = 1L,
            chapterIndex = 0,
            startedAt = 1000L,
            endedAt = 2000L,
            durationSeconds = 1000L,
            dateKey = 20260609,
            hour = 10,
        )

        val modified = original.copy(durationSeconds = 999L)

        assertEquals(999L, modified.durationSeconds)
        assertEquals(original.bookId, modified.bookId)
        assertEquals(original.chapterIndex, modified.chapterIndex)
        assertEquals(original.startedAt, modified.startedAt)
        assertEquals(original.endedAt, modified.endedAt)
        assertEquals(original.dateKey, modified.dateKey)
        assertEquals(original.hour, modified.hour)
    }
}
