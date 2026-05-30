package com.shuli.reader.sync.engine

/**
 * 同步编排结果（T-23）
 *
 * BOTH 模式下，云端和本地独立执行，互不阻断。
 */
data class SyncOrchestratorResult(
    val cloudResult: Result<Unit>,
    val localResult: Result<Unit>?,
)
