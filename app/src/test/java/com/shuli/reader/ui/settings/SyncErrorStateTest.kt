package com.shuli.reader.ui.settings

import com.shuli.reader.sync.state.SyncState
import com.shuli.reader.sync.state.SyncStateMachine
import com.shuli.reader.ui.settings.sync.SyncErrorType
import com.shuli.reader.ui.settings.sync.SyncSummaryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-40 错误状态 UI
@OptIn(ExperimentalCoroutinesApi::class)
class SyncErrorStateTest {

    @Test
    fun `FAILED state shows auth error card`() = runTest(UnconfinedTestDispatcher()) {
        val stateMachine = SyncStateMachine()
        stateMachine.transition(SyncState.SCANNING)
        stateMachine.transition(SyncState.FAILED)
        val vm = SyncSummaryViewModel(stateMachine, scope = backgroundScope)
        val state = vm.cloudSyncUiState.value
        assertEquals(SyncErrorType.AUTH_FAILED, state.errorType)
    }

    @Test
    fun `RATE_LIMITED state shows retry timer and links`() = runTest(UnconfinedTestDispatcher()) {
        val stateMachine = SyncStateMachine()
        stateMachine.transition(SyncState.SCANNING)
        stateMachine.transition(SyncState.DOWNLOADING)
        stateMachine.transition(SyncState.MERGING)
        stateMachine.transition(SyncState.UPLOADING)
        stateMachine.transition(SyncState.RATE_LIMITED)
        val vm = SyncSummaryViewModel(stateMachine, scope = backgroundScope)
        val state = vm.cloudSyncUiState.value
        assertTrue(state.rateLimitRetryAfterMs > 0L)
        assertTrue(state.showRateLimitLinks)
    }

    @Test
    fun `CRYPTO_LOCKED state shows password input`() = runTest(UnconfinedTestDispatcher()) {
        val stateMachine = SyncStateMachine()
        stateMachine.transition(SyncState.CRYPTO_LOCKED)
        val vm = SyncSummaryViewModel(stateMachine, scope = backgroundScope)
        val state = vm.cloudSyncUiState.value
        assertTrue(state.requiresPasswordInput)
    }
}
