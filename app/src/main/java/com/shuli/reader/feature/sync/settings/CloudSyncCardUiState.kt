package com.shuli.reader.feature.sync.settings

/**
 * 云端同步卡片 UI 状态（T-33, T-40）
 */
data class CloudSyncCardUiState(
    val isLoading: Boolean = false,
    val lastSyncText: String = "",
    val statusText: String = "",
    val errorType: SyncErrorType = SyncErrorType.NONE,
    val rateLimitRetryAfterMs: Long = 0L,
    val showRateLimitLinks: Boolean = false,
    val requiresPasswordInput: Boolean = false,
)
