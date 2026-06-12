package com.shuli.reader.feature.reader.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticFeedbackTest {

    // T-1.4.1: hapticFeedback = true 时翻页触发振动
    @Test
    fun performPageTurnHaptic_enabled_callsPerformer() {
        var called = false
        val performer = HapticFeedbackPerformer { called = true }
        performPageTurnHaptic(true, performer)
        assertTrue("performer should be called", called)
    }

    // T-1.4.2: hapticFeedback = false 时不触发
    @Test
    fun performPageTurnHaptic_disabled_doesNotCallPerformer() {
        var called = false
        val performer = HapticFeedbackPerformer { called = true }
        performPageTurnHaptic(false, performer)
        assertFalse("performer should NOT be called", called)
    }
}
