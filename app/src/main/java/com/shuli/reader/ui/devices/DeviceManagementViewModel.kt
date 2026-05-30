package com.shuli.reader.ui.devices

import com.shuli.reader.sync.device.DeviceSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Part of T-37 设备管理页
class DeviceManagementViewModel(
    private val deviceSyncManager: DeviceSyncManager,
    private val localDeviceId: String = "",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {

    private val _devices = MutableStateFlow<List<DeviceUiItem>>(emptyList())
    val devices: StateFlow<List<DeviceUiItem>> = _devices.asStateFlow()

    init {
        scope.launch {
            loadDevices()
        }
    }

    suspend fun loadDevices() {
        try {
            val deviceList = deviceSyncManager.listDevices()
            val uiItems = deviceList
                .sortedByDescending { it.lastSyncAt }
                .map { device ->
                    DeviceUiItem(
                        deviceId = device.deviceId,
                        model = device.model,
                        manufacturer = device.manufacturer,
                        appVersion = device.appVersion,
                        lastSyncAt = device.lastSyncAt,
                        isSelf = device.deviceId == localDeviceId,
                    )
                }
            _devices.value = uiItems
        } catch (e: Exception) {
            // Handle error silently for now
            _devices.value = emptyList()
        }
    }

    fun removeDevice(deviceId: String) {
        scope.launch {
            try {
                deviceSyncManager.removeDevice(deviceId)
                loadDevices() // Refresh list after removal
            } catch (e: Exception) {
                // Handle error silently for now
            }
        }
    }
}
