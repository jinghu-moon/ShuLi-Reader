package com.shuli.reader.sync.notification

import com.shuli.reader.core.i18n.AppStrings
import com.shuli.reader.sync.state.SyncState
import com.shuli.reader.sync.state.SyncStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 同步通知协调器（T-25）
 *
 * 订阅状态机状态变化，仅在状态变化时更新通知。
 */
class SyncNotificationCoordinator(
    private val stateMachine: SyncStateMachine,
    private val notifier: SyncNotifier,
    private val strings: AppStrings = AppStrings.ZhHans,
) {

    /**
     * 启动通知协调
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            stateMachine.state.collect { state ->
                if (state != SyncState.IDLE) {
                    val text = SyncStateTextMapper.map(state, strings)
                    notifier.update(text)
                }
            }
        }
    }
}
