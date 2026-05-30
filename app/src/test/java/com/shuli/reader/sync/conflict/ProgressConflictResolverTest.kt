package com.shuli.reader.sync.conflict

import org.junit.Assert.assertEquals
import org.junit.Test

// Part of T-18 ConflictResolver — 进度冲突
class ProgressConflictResolverTest {

    @Test
    fun `higher logicalVersion wins regardless of timestamp`() {
        val local = BookState(version = 5, updatedAt = 1000L, byteOffset = 200L, fileType = "TXT")
        val remote = BookState(version = 3, updatedAt = 9999L, byteOffset = 500L, fileType = "TXT")
        val result = ConflictResolver.resolveProgress(local, remote)
        assertEquals(200L, result.byteOffset) // local wins (higher version)
    }

    @Test
    fun `same version falls back to timestamp`() {
        val local = BookState(version = 2, updatedAt = 1000L, byteOffset = 200L, fileType = "TXT")
        val remote = BookState(version = 2, updatedAt = 2000L, byteOffset = 500L, fileType = "TXT")
        val result = ConflictResolver.resolveProgress(local, remote)
        assertEquals(500L, result.byteOffset) // remote wins (later timestamp)
    }

    @Test
    fun `same version same timestamp TXT uses larger byteOffset`() {
        val local = BookState(version = 1, updatedAt = 1000L, byteOffset = 200L, fileType = "TXT")
        val remote = BookState(version = 1, updatedAt = 1000L, byteOffset = 500L, fileType = "TXT")
        val result = ConflictResolver.resolveProgress(local, remote)
        assertEquals(500L, result.byteOffset)
    }

    @Test
    fun `gap under 5 percent does NOT trigger user conflict dialog`() {
        // total = 1000, local = 900, remote = 950 → diff = 5%
        val local = BookState(version = 1, updatedAt = 1000L, byteOffset = 900L, fileType = "TXT", totalSize = 1000L)
        val remote = BookState(version = 1, updatedAt = 2000L, byteOffset = 950L, fileType = "TXT", totalSize = 1000L)
        val decision = ConflictResolver.classifyProgressConflict(local, remote)
        assertEquals(ConflictDecision.AUTO_MERGE, decision)
    }

    @Test
    fun `gap over 5 percent triggers REQUIRE_USER_INPUT`() {
        val local = BookState(version = 1, updatedAt = 1000L, byteOffset = 200L, fileType = "TXT", totalSize = 1000L)
        val remote = BookState(version = 1, updatedAt = 2000L, byteOffset = 700L, fileType = "TXT", totalSize = 1000L)
        val decision = ConflictResolver.classifyProgressConflict(local, remote)
        assertEquals(ConflictDecision.REQUIRE_USER_INPUT, decision)
    }
}
