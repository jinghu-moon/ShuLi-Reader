package com.shuli.reader.sync.engine

import com.shuli.reader.sync.manifest.ManifestManager
import com.shuli.reader.sync.state.SyncState
import com.shuli.reader.sync.state.SyncStateMachine
import com.shuli.reader.sync.transport.SyncTransport

/**
 * 同步引擎（T-22）
 *
 * 核心编排器，驱动同步状态机完成完整的同步流程。
 * 流程：IDLE → SCANNING → DOWNLOADING → MERGING → UPLOADING → SUCCESS
 */
class SyncEngine(
    private val manifestManager: ManifestManager,
    private val stateMachine: SyncStateMachine,
) {

    /**
     * 执行同步
     */
    suspend fun sync(transport: SyncTransport) {
        try {
            // IDLE → SCANNING
            stateMachine.transition(SyncState.SCANNING)

            // 读取远端 manifest
            val remoteManifest = manifestManager.readManifest()

            // SCANNING → DOWNLOADING
            stateMachine.transition(SyncState.DOWNLOADING)

            // TODO: 下载远端数据（T-22 后续步骤）

            // DOWNLOADING → MERGING
            stateMachine.transition(SyncState.MERGING)

            // TODO: 合并数据（T-22 后续步骤）

            // MERGING → UPLOADING
            stateMachine.transition(SyncState.UPLOADING)

            // TODO: 上传本地数据（T-22 后续步骤）

            // UPLOADING → SUCCESS
            stateMachine.transition(SyncState.SUCCESS)
        } catch (e: Exception) {
            // 任何阶段失败 → FAILED
            try {
                stateMachine.transition(SyncState.FAILED)
            } catch (ignored: Exception) {
                // 状态机可能已经不在可转换状态
            }
            throw e
        }
    }
}
