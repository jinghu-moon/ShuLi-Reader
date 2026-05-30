package com.shuli.reader.sync.device

import kotlinx.serialization.Serializable

/**
 * 设备信息（T-21）
 *
 * 用于设备同步和管理。
 */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val model: String = "",
    val manufacturer: String = "",
    val appVersion: String = "",
    val lastSyncAt: Long = 0L,
)
