package com.shuli.reader.sync.device

import com.shuli.reader.sync.transport.SyncTransport
import kotlinx.serialization.json.Json

/**
 * 设备同步管理器（T-21）
 *
 * 负责设备信息的上传、列表和删除。
 */
class DeviceSyncManager(
    private val transport: SyncTransport,
    private val deviceId: String,
    private val model: String,
    private val appVersion: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 上传当前设备信息
     */
    suspend fun uploadDeviceInfo(lastSyncAt: Long) {
        val info = DeviceInfo(
            deviceId = deviceId,
            model = model,
            appVersion = appVersion,
            lastSyncAt = lastSyncAt,
        )
        val data = json.encodeToString(DeviceInfo.serializer(), info).toByteArray()
        transport.write("device/$deviceId.json", data)
    }

    /**
     * 列出所有已同步设备
     */
    suspend fun listDevices(): List<DeviceInfo> {
        val resources = transport.list("device")
        return resources
            .filter { it.path.endsWith(".json") }
            .mapNotNull { resource ->
                val data = transport.read(resource.path) ?: return@mapNotNull null
                try {
                    json.decodeFromString<DeviceInfo>(data.decodeToString())
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * 移除设备（删除远端文件）
     */
    suspend fun removeDevice(deviceId: String) {
        transport.delete("device/$deviceId.json")
    }
}
