package com.shuli.reader.sync.conflict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-19 ConflictResolver — 书签/笔记 UUID Merge + Tombstone
class BookmarkMergeTest {

    @Test
    fun `merge keeps items from both local and remote by UUID`() {
        val local = listOf(BookmarkDto(id = "A", byteOffset = 100, updatedAt = 1000, deleted = false))
        val remote = listOf(BookmarkDto(id = "B", byteOffset = 200, updatedAt = 2000, deleted = false))
        val merged = ConflictResolver.mergeBookmarks(local, remote)
        assertEquals(setOf("A", "B"), merged.map { it.id }.toSet())
    }

    @Test
    fun `same UUID remote timestamp wins`() {
        val local = listOf(BookmarkDto(id = "A", byteOffset = 100, updatedAt = 1000, deleted = false))
        val remote = listOf(BookmarkDto(id = "A", byteOffset = 200, updatedAt = 2000, deleted = false))
        val merged = ConflictResolver.mergeBookmarks(local, remote)
        assertEquals(200, merged.single().byteOffset)
    }

    @Test
    fun `deleted tombstone from remote propagates to merged result`() {
        val local = listOf(BookmarkDto(id = "A", byteOffset = 100, updatedAt = 1000, deleted = false))
        val remote = listOf(BookmarkDto(id = "A", byteOffset = 100, updatedAt = 2000, deleted = true))
        val merged = ConflictResolver.mergeBookmarks(local, remote)
        assertTrue(merged.single().deleted)
    }

    @Test
    fun `canCompactTombstone returns false if any device has not synced after deletedAt`() {
        val tombstone = BookmarkDto(id = "A", deleted = true, updatedAt = 5000L)
        val devices = listOf(
            DeviceInfo(deviceId = "d1", lastSyncAt = 6000L),
            DeviceInfo(deviceId = "d2", lastSyncAt = 4000L) // 未同步
        )
        assertFalse(ConflictResolver.canCompactTombstone(tombstone, devices))
    }

    @Test
    fun `canCompactTombstone returns true if all devices synced after deletedAt`() {
        val tombstone = BookmarkDto(id = "A", deleted = true, updatedAt = 5000L)
        val devices = listOf(
            DeviceInfo(deviceId = "d1", lastSyncAt = 6000L),
            DeviceInfo(deviceId = "d2", lastSyncAt = 7000L)
        )
        assertTrue(ConflictResolver.canCompactTombstone(tombstone, devices))
    }
}
