package com.shuli.reader.core.reader.engine.animation

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderMotionTokensTest {

    @Test
    fun tokens_useStandardMotionDurations() {
        assertEquals(100L, ReaderMotionTokens.MICRO_MS)
        assertEquals(200L, ReaderMotionTokens.SHORT_MS)
        assertEquals(300L, ReaderMotionTokens.MEDIUM_MS)
        assertEquals(500L, ReaderMotionTokens.LONG_MS)
    }

    @Test
    fun allMotionDurations_areStandard() {
        val allowed = setOf(100L, 200L, 300L, 500L)
        val allowedTokens = setOf("MICRO_MS", "SHORT_MS", "MEDIUM_MS", "LONG_MS")
        val fields = ReaderMotionTokens::class.java.declaredFields
        var count = 0
        for (field in fields) {
            if (field.name in allowedTokens) {
                field.isAccessible = true
                val value = field.get(null) as Long
                org.junit.Assert.assertTrue(
                    "Motion token ${field.name} has non-standard value $value",
                    allowed.contains(value)
                )
                count++
            }
        }
        org.junit.Assert.assertTrue("Should check at least 4 tokens", count >= 4)
    }
}
