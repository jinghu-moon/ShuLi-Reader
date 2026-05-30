package com.shuli.reader.ui.settings

import com.shuli.reader.sync.engine.SyncOrchestrator
import com.shuli.reader.sync.engine.SyncOrchestratorResult
import com.shuli.reader.sync.engine.SyncTarget
import com.shuli.reader.sync.state.SyncState
import com.shuli.reader.sync.state.SyncStateMachine
import com.shuli.reader.ui.settings.sync.SyncSummaryViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

// Part of T-33 设置主页 — 同步摘要卡
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SyncSummaryViewModelTest {

    @Test
    fun `cloudSyncUiState reflects SyncState SUCCESS`() = runTest {
        val stateMachine = SyncStateMachine()
        stateMachine.transition(SyncState.SCANNING)
        stateMachine.transition(SyncState.DOWNLOADING)
        stateMachine.transition(SyncState.MERGING)
        stateMachine.transition(SyncState.UPLOADING)
        stateMachine.transition(SyncState.SUCCESS)
        val vm = SyncSummaryViewModel(stateMachine, scope = backgroundScope)
        val state = vm.cloudSyncUiState.value
        assertFalse(state.isLoading)
    }

    @Test
    fun `triggerManualSync calls orchestrator with CLOUD target`() = runTest(UnconfinedTestDispatcher()) {
        val stateMachine = SyncStateMachine()
        val mockOrchestrator = mockk<SyncOrchestrator>()
        var calledWith: SyncTarget? = null
        coEvery { mockOrchestrator.sync(any()) } coAnswers {
            calledWith = firstArg()
            SyncOrchestratorResult(Result.success(Unit), null)
        }
        val vm = SyncSummaryViewModel(stateMachine, orchestrator = mockOrchestrator, scope = backgroundScope)
        vm.triggerManualSync(SyncTarget.CLOUD)
        assertEquals(SyncTarget.CLOUD, calledWith)
    }
}
