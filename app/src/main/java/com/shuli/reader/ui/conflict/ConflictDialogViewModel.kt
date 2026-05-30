package com.shuli.reader.ui.conflict

import com.shuli.reader.sync.conflict.BookState
import com.shuli.reader.sync.device.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

// Part of T-36 冲突解决弹窗
class ConflictDialogViewModel(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {

    private val _events = MutableSharedFlow<SyncUiEvent>()
    val events: SharedFlow<SyncUiEvent> = _events.asSharedFlow()

    fun onProgressConflictDetected(
        localState: BookState,
        remoteState: BookState,
        remoteDeviceInfo: DeviceInfo?,
    ) {
        scope.launch {
            val deviceName = getDeviceDisplayName(remoteDeviceInfo)
            _events.emit(
                SyncUiEvent.ShowConflictDialog(
                    localState = localState,
                    remoteState = remoteState,
                    remoteDeviceName = deviceName,
                )
            )
        }
    }

    companion object {
        /**
         * 获取设备显示名称
         *
         * 优先使用 model，如果为空则使用 deviceId 的前 6 位作为 fallback。
         */
        fun getDeviceDisplayName(deviceInfo: DeviceInfo?): String {
            if (deviceInfo == null) return "其他设备"
            if (deviceInfo.model.isNotBlank()) return deviceInfo.model
            val shortId = deviceInfo.deviceId.take(6)
            return "其他设备（$shortId）"
        }
    }
}
