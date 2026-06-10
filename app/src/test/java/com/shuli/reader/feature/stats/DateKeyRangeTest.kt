package com.shuli.reader.feature.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateKeyRangeTest {

    @Test
    fun containsInRange() {
        val range = DateKeyRange(20260101, 20260131)
        assertTrue(range.contains(20260115))
    }

    @Test
    fun containsOutOfRange() {
        val range = DateKeyRange(20260101, 20260131)
        assertFalse(range.contains(20260201))
    }

    @Test
    fun containsAtBoundaries() {
        val range = DateKeyRange(20260101, 20260131)
        assertTrue(range.contains(20260101))
        assertTrue(range.contains(20260131))
    }
}
