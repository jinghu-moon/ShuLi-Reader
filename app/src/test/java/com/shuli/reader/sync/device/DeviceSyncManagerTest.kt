package com.shuli.reader.sync.device

import com.shuli.reader.sync.transport.SyncTransport
import com.shuli.reader.sync.transport.TransportResourceInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Part of T-21 Device 信息同步
class DeviceSyncManagerTest {

    @Test
    fun `uploadDeviceInfo writes to device json`() = runTest {
        val transport = mockk<SyncTransport>(relaxed = true)
        val manager = DeviceSyncManager(transport, deviceId = "dev-123", model = "Pixel 7", appVersion = "1.2.3")
        manager.uploadDeviceInfo(lastSyncAt = 1710000000000L)
        coVerify {
            transport.write(
                eq("device/dev-123.json"),
                match { String(it).contains("\"model\":\"Pixel 7\"") },
                any()
            )
        }
    }

    @Test
    fun `listDevices returns all device infos from remote`() = runTest {
        val transport = mockk<SyncTransport> {
            coEvery { list("device") } returns listOf(
                TransportResourceInfo(path = "device/dev-1.json", isDirectory = false, contentLength = 100),
                TransportResourceInfo(path = "device/dev-2.json", isDirectory = false, contentLength = 100)
            )
            coEvery { read("device/dev-1.json") } returns """{"deviceId":"dev-1","model":"Pixel 7","lastSyncAt":1000}""".toByteArray()
            coEvery { read("device/dev-2.json") } returns """{"deviceId":"dev-2","model":"小米14","lastSyncAt":2000}""".toByteArray()
        }
        val manager = DeviceSyncManager(transport, "dev-1", "Pixel 7", "1.0")
        val devices = manager.listDevices()
        assertEquals(2, devices.size)
        assertEquals(setOf("Pixel 7", "小米14"), devices.map { it.model }.toSet())
    }

    @Test
    fun `removeDevice deletes remote file`() = runTest {
        val transport = mockk<SyncTransport>(relaxed = true)
        val manager = DeviceSyncManager(transport, "dev-1", "Pixel 7", "1.0")
        manager.removeDevice("dev-2")
        coVerify { transport.delete("device/dev-2.json") }
    }
}
