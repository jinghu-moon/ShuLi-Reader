package com.shuli.reader.sync.engine

import com.shuli.reader.sync.transport.SyncTransport

/**
 * 同步编排器（T-23）
 *
 * 支持 CLOUD/LOCAL/BOTH 三种同步目标。
 * BOTH 模式下，云端和本地独立执行，互不阻断。
 */
class SyncOrchestrator(
    private val cloudTransport: SyncTransport,
    private val localTransport: SyncTransport?,
    private val engine: SyncEngine,
) {

    /**
     * 执行同步
     */
    suspend fun sync(target: SyncTarget): SyncOrchestratorResult {
        return when (target) {
            SyncTarget.CLOUD -> {
                val cloudResult = runCatching { engine.sync(cloudTransport) }
                SyncOrchestratorResult(cloudResult, null)
            }
            SyncTarget.LOCAL -> {
                val localResult = localTransport?.let { runCatching { engine.sync(it) } }
                SyncOrchestratorResult(Result.success(Unit), localResult)
            }
            SyncTarget.BOTH -> {
                val cloudResult = runCatching { engine.sync(cloudTransport) }
                val localResult = localTransport?.let { runCatching { engine.sync(it) } }
                SyncOrchestratorResult(cloudResult, localResult)
            }
        }
    }
}
