package com.shuli.reader.sync.dirty

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-05 PreferencesDirtyTracker
class PreferencesDirtyTrackerTest {

    @Test
    fun `initially has no dirty keys`() = runTest {
        val tracker = PreferencesDirtyTracker()
        assertFalse(tracker.hasDirty())
        assertTrue(tracker.dirtyKeys.value.isEmpty())
    }

    @Test
    fun `markDirty adds key`() = runTest {
        val tracker = PreferencesDirtyTracker()
        tracker.markDirty("fontSize")
        assertEquals(setOf("fontSize"), tracker.dirtyKeys.value)
    }

    @Test
    fun `markDirty multiple keys accumulates`() = runTest {
        val tracker = PreferencesDirtyTracker()
        tracker.markDirty("fontSize")
        tracker.markDirty("themeMode")
        assertEquals(setOf("fontSize", "themeMode"), tracker.dirtyKeys.value)
    }

    @Test
    fun `clearDirty removes all keys`() = runTest {
        val tracker = PreferencesDirtyTracker()
        tracker.markDirty("fontSize")
        tracker.clearDirty()
        assertFalse(tracker.hasDirty())
    }
}
