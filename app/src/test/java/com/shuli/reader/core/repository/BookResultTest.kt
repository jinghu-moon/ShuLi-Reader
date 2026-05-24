package com.shuli.reader.core.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookResultTest {

    @Test
    fun success_carriesData() {
        val result = BookResult.Success("test")
        assertEquals("test", result.data)
    }

    @Test
    fun failure_carriesError() {
        val result = BookResult.Failure(BookError.UnsupportedFormat)
        assertTrue(result.error is BookError.UnsupportedFormat)
    }

    @Test
    fun onSuccess_runsForSuccess() {
        var captured: String? = null
        BookResult.Success("hello").onSuccess { captured = it }
        assertEquals("hello", captured)
    }

    @Test
    fun onSuccess_doesNotRunForFailure() {
        var captured: String? = null
        BookResult.Failure(BookError.UnsupportedFormat).onSuccess { captured = it }
        assertNull(captured)
    }

    @Test
    fun onFailure_runsForFailure() {
        var captured: BookError? = null
        BookResult.Failure(BookError.EpubStructureInvalid).onFailure { captured = it }
        assertTrue(captured is BookError.EpubStructureInvalid)
    }

    @Test
    fun onFailure_doesNotRunForSuccess() {
        var captured: BookError? = null
        BookResult.Success("ok").onFailure { captured = it }
        assertNull(captured)
    }

    @Test
    fun duplicateError_carriesBookIdAndTitle() {
        val error = BookError.Duplicate(42L, "测试书")
        assertEquals(42L, error.bookId)
        assertEquals("测试书", error.bookTitle)
    }

    @Test
    fun fileReadFailed_carriesDetail() {
        val error = BookError.FileReadFailed("权限不足")
        assertEquals("权限不足", error.detail)
    }
}
