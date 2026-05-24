package com.shuli.reader.core.reader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionRangeTest {

    @Test
    fun normalSelection_calculatesLengthCorrectly() {
        val range = SelectionRange(chapterIndex = 0, startPos = 10, endPos = 20, selectedText = "text")
        assertEquals(10, range.length)
        assertFalse(range.isEmpty)
    }

    @Test
    fun emptySelection_hasZeroLength() {
        val range = SelectionRange.cursor(chapterIndex = 0, pos = 5)
        assertEquals(0, range.length)
        assertTrue(range.isEmpty)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeStartPos_throwsException() {
        SelectionRange(chapterIndex = 0, startPos = -1, endPos = 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun endPosBeforeStartPos_throwsException() {
        SelectionRange(chapterIndex = 0, startPos = 20, endPos = 10)
    }

    @Test
    fun cursor_createsEmptySelection() {
        val cursor = SelectionRange.cursor(chapterIndex = 2, pos = 100)
        assertEquals(2, cursor.chapterIndex)
        assertEquals(100, cursor.startPos)
        assertEquals(100, cursor.endPos)
        assertTrue(cursor.isEmpty)
        assertEquals(null, cursor.selectedText)
    }

    @Test
    fun startPosEqualToEndPos_isValidSelection() {
        val range = SelectionRange(chapterIndex = 0, startPos = 5, endPos = 5)
        assertTrue(range.isEmpty)
        assertEquals(0, range.length)
    }
}
