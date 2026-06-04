package com.shuli.reader.sync.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 同步状态机（T-04）
 *
 * 使用 MutableStateFlow 持有状态，提供状态转换验证和 canStartSync 查询。
 */
class SyncStateMachine {

    private val _state = MutableStateFlow(SyncState.IDLE)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** 限流退避截止时间戳（毫秒），RATE_LIMITED 状态下有效 */
    @Volatile
    var retryAfterMs: Long = 0L
        private set

    /**
     * 执行状态转换
     * @throws IllegalArgumentException 如果转换无效
     */
    fun transition(to: SyncState, retryAfterMs: Long = 0L) {
        val from = _state.value
        require(isValidTransition(from, to)) {
            "Invalid transition: $from → $to"
        }
        this.retryAfterMs = if (to == SyncState.RATE_LIMITED) retryAfterMs else 0L
        _state.value = to
    }

    /**
     * 检查当前状态是否允许开始新同步
     */
    fun canStartSync(): Boolean {
        return _state.value in setOf(
            SyncState.IDLE,
            SyncState.SUCCESS,
            SyncState.FAILED,
        )
    }

    /**
     * 验证状态转换是否有效
     */
    private fun isValidTransition(from: SyncState, to: SyncState): Boolean {
        return when (from) {
            SyncState.IDLE -> to in setOf(SyncState.SCANNING, SyncState.CRYPTO_LOCKED)
            SyncState.SCANNING -> to in setOf(SyncState.DOWNLOADING, SyncState.FAILED)
            SyncState.DOWNLOADING -> to in setOf(SyncState.MERGING, SyncState.FAILED)
            SyncState.MERGING -> to in setOf(SyncState.UPLOADING, SyncState.SUCCESS, SyncState.FAILED)
            SyncState.UPLOADING -> to in setOf(SyncState.SUCCESS, SyncState.RATE_LIMITED, SyncState.FAILED)
            SyncState.SUCCESS -> to == SyncState.IDLE
            SyncState.FAILED -> to == SyncState.IDLE
            SyncState.RATE_LIMITED -> to == SyncState.WAITING_RETRY
            SyncState.WAITING_RETRY -> to == SyncState.SCANNING
            SyncState.CRYPTO_LOCKED -> to == SyncState.IDLE
        }
    }
}
