package com.shuli.reader.ui.devices

// Part of T-37 设备管理页
data class DeviceUiItem(
    val deviceId: String,
    val model: String,
    val manufacturer: String,
    val appVersion: String,
    val lastSyncAt: Long,
    val isSelf: Boolean,
)
