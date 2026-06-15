package com.shuli.reader.feature.sync.settings

import com.shuli.reader.core.i18n.AppStrings
import com.shuli.reader.sync.engine.SyncOrchestrator
import com.shuli.reader.sync.engine.SyncTarget
import com.shuli.reader.sync.engine.state.SyncState
import com.shuli.reader.sync.engine.state.SyncStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 同步摘要 ViewModel（T-33）
 *
 * 暴露云端同步卡片 UI 状态。
 */
class SyncSummaryViewModel(
    private val stateMachine: SyncStateMachine,
    private val orchestrator: SyncOrchestrator? = null,
    private val strings: AppStrings = AppStrings.ZhHans,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {

    private val _cloudSyncUiState = MutableStateFlow(CloudSyncCardUiState())
    val cloudSyncUiState: StateFlow<CloudSyncCardUiState> = _cloudSyncUiState.asStateFlow()

    init {
        scope.launch {
            stateMachine.state.collect { state ->
                _cloudSyncUiState.value = when (state) {
                    SyncState.IDLE -> CloudSyncCardUiState(isLoading = false, statusText = strings.sync.ready)
                    SyncState.SCANNING -> CloudSyncCardUiState(isLoading = true, statusText = strings.sync.scanningLocalChanges)
                    SyncState.DOWNLOADING -> CloudSyncCardUiState(isLoading = true, statusText = strings.sync.downloadingRemoteData)
                    SyncState.MERGING -> CloudSyncCardUiState(isLoading = true, statusText = strings.sync.mergingData)
                    SyncState.UPLOADING -> CloudSyncCardUiState(isLoading = true, statusText = strings.sync.uploadingBookmarksNotes)
                    SyncState.SUCCESS -> CloudSyncCardUiState(isLoading = false, statusText = strings.sync.syncComplete)
                    SyncState.FAILED -> CloudSyncCardUiState(
                        isLoading = false,
                        statusText = strings.sync.syncFailed,
                        errorType = SyncErrorType.AUTH_FAILED,
                    )
                    SyncState.RATE_LIMITED -> CloudSyncCardUiState(
                        isLoading = false,
                        statusText = strings.sync.rateLimitedWaitRetry,
                        errorType = SyncErrorType.RATE_LIMITED,
                        rateLimitRetryAfterMs = 30000L,
                        showRateLimitLinks = true,
                    )
                    SyncState.WAITING_RETRY -> CloudSyncCardUiState(isLoading = false, statusText = strings.sync.waitingRetry)
                    SyncState.CRYPTO_LOCKED -> CloudSyncCardUiState(
                        isLoading = false,
                        statusText = strings.sync.cryptoLocked,
                        errorType = SyncErrorType.CRYPTO_LOCKED,
                        requiresPasswordInput = true,
                    )
                }
            }
        }
    }

    /**
     * 触发手动同步
     */
    fun triggerManualSync(target: SyncTarget) {
        scope.launch {
            orchestrator?.sync(target)
        }
    }
}
