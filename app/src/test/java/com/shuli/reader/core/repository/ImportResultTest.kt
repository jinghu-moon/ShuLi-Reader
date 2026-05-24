package com.shuli.reader.core.repository

import org.junit.Assert.*
import org.junit.Test

class ImportResultTest {

    @Test
    fun defaultValues_areAllZero() {
        val result = ImportResult()
        assertEquals(0, result.successCount)
        assertEquals(0, result.skippedCount)
        assertEquals(0, result.failedCount)
        assertTrue(result.failedFiles.isEmpty())
        assertNull(result.firstDuplicateBookId)
    }

    @Test
    fun totalCount_returnsSum() {
        val result = ImportResult(successCount = 5, skippedCount = 3, failedCount = 2)
        assertEquals(10, result.totalCount)
    }

    @Test
    fun hasSuccess_returnsTrueWhenSuccessCountExists() {
        val result = ImportResult(successCount = 1)
        assertTrue(result.hasSuccess)

        val empty = ImportResult(successCount = 0)
        assertFalse(empty.hasSuccess)
    }

    @Test
    fun hasSkipped_returnsTrueWhenSkippedCountExists() {
        val result = ImportResult(skippedCount = 1)
        assertTrue(result.hasSkipped)

        val empty = ImportResult(skippedCount = 0)
        assertFalse(empty.hasSkipped)
    }

    @Test
    fun hasFailed_returnsTrueWhenFailedCountExists() {
        val result = ImportResult(failedCount = 1)
        assertTrue(result.hasFailed)

        val empty = ImportResult(failedCount = 0)
        assertFalse(empty.hasFailed)
    }

    @Test
    fun isAllSuccess_returnsTrueWhenNoFailureOrSkipExists() {
        val result = ImportResult(successCount = 5)
        assertTrue(result.isAllSuccess)

        val withSkipped = ImportResult(successCount = 5, skippedCount = 1)
        assertFalse(withSkipped.isAllSuccess)

        val withFailed = ImportResult(successCount = 5, failedCount = 1)
        assertFalse(withFailed.isAllSuccess)
    }

    @Test
    fun empty_isAllZeroConstant() {
        assertEquals(ImportResult(), ImportResult.EMPTY)
    }
}
