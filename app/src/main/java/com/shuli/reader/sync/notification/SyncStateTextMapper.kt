package com.shuli.reader.sync.notification

import com.shuli.reader.sync.state.SyncState

/**
 * 同步状态文案映射器（T-25）
 */
object SyncStateTextMapper {

    fun map(state: SyncState): String {
        return when (state) {
            SyncState.IDLE -> ""
            SyncState.SCANNING -> "正在扫描本地变更..."
            SyncState.DOWNLOADING -> "正在下载远端数据..."
            SyncState.MERGING -> "正在合并数据..."
            SyncState.UPLOADING -> "正在上传书签与笔记..."
            SyncState.SUCCESS -> "同步完成"
            SyncState.FAILED -> "同步失败"
            SyncState.RATE_LIMITED -> "请求过于频繁，等待重试..."
            SyncState.WAITING_RETRY -> "等待重试..."
            SyncState.CRYPTO_LOCKED -> "加密锁未解锁"
        }
    }
}
