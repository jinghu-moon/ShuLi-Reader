package com.shuli.reader.sync.engine.conflict

/**
 * 设备信息（T-19）
 *
 * 用于 tombstone 清理策略。
 */
data class DeviceInfo(
    val deviceId: String,
    val lastSyncAt: Long = 0L,
)
