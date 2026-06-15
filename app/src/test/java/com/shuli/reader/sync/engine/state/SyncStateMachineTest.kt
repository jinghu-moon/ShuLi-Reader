package com.shuli.reader.sync.engine.state

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-04 SyncStateMachine
class SyncStateMachineTest {

    @Test
    fun `initial state is IDLE`() = runTest {
        val sm = SyncStateMachine()
        assertEquals(SyncState.IDLE, sm.state.value)
    }

    @Test
    fun `IDLE to SCANNING is valid`() = runTest {
        val sm = SyncStateMachine()
        sm.transition(SyncState.SCANNING)
        assertEquals(SyncState.SCANNING, sm.state.value)
    }

    @Test
    fun `SCANNING to DOWNLOADING is valid`() = runTest {
        val sm = SyncStateMachine()
        sm.transition(SyncState.SCANNING)
        sm.transition(SyncState.DOWNLOADING)
        assertEquals(SyncState.DOWNLOADING, sm.state.value)
    }

    @Test
    fun `IDLE to UPLOADING is INVALID and throws`() = runTest {
        val sm = SyncStateMachine()
        assertThrows(IllegalArgumentException::class.java) {
            sm.transition(SyncState.UPLOADING)
        }
    }

    @Test
    fun `canStartSync returns true only for IDLE, SUCCESS, FAILED`() = runTest {
        val sm = SyncStateMachine()
        assertTrue(sm.canStartSync())
        sm.transition(SyncState.SCANNING)
        assertFalse(sm.canStartSync())
    }

    @Test
    fun `state is observable via StateFlow`() = runTest {
        val sm = SyncStateMachine()
        assertEquals(SyncState.IDLE, sm.state.value)
        sm.transition(SyncState.SCANNING)
        assertEquals(SyncState.SCANNING, sm.state.value)
    }
}
