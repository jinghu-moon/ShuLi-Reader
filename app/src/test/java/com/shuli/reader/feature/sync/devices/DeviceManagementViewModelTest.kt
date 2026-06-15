package com.shuli.reader.feature.sync.devices

import com.shuli.reader.sync.device.DeviceInfo
import com.shuli.reader.sync.device.DeviceSyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-37 设备管理页
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceManagementViewModelTest {

    @Test
    fun `loadDevices shows list sorted by lastSyncAt descending`() = runTest(UnconfinedTestDispatcher()) {
        val mockManager = mockk<DeviceSyncManager>()
        coEvery { mockManager.listDevices() } returns listOf(
            DeviceInfo(deviceId = "dev-1", model = "Pixel 7", lastSyncAt = 100),
            DeviceInfo(deviceId = "dev-2", model = "Samsung S23", lastSyncAt = 200),
            DeviceInfo(deviceId = "dev-3", model = "iPhone 15", lastSyncAt = 150),
        )
        val vm = DeviceManagementViewModel(mockManager, localDeviceId = "dev-1", scope = backgroundScope)
        val devices = vm.devices.value
        assertEquals(3, devices.size)
        assertEquals(200L, devices.first().lastSyncAt)
        assertEquals(100L, devices.last().lastSyncAt)
    }

    @Test
    fun `removeDevice calls DeviceSyncManager and refreshes list`() = runTest(UnconfinedTestDispatcher()) {
        val mockManager = mockk<DeviceSyncManager>()
        coEvery { mockManager.removeDevice(any()) } returns Unit
        coEvery { mockManager.listDevices() } returns emptyList()
        val vm = DeviceManagementViewModel(mockManager, scope = backgroundScope)
        vm.removeDevice("dev-2")
        coVerify { mockManager.removeDevice("dev-2") }
    }

    @Test
    fun `current device is marked with isSelf=true`() = runTest(UnconfinedTestDispatcher()) {
        val mockManager = mockk<DeviceSyncManager>()
        coEvery { mockManager.listDevices() } returns listOf(
            DeviceInfo(deviceId = "dev-1", model = "Pixel 7", lastSyncAt = 100),
            DeviceInfo(deviceId = "dev-2", model = "Samsung S23", lastSyncAt = 200),
        )
        val vm = DeviceManagementViewModel(mockManager, localDeviceId = "dev-1", scope = backgroundScope)
        val devices = vm.devices.value
        val self = devices.first { it.deviceId == "dev-1" }
        assertTrue(self.isSelf)
    }
}
