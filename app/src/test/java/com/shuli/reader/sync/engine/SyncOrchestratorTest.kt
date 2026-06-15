package com.shuli.reader.sync.engine

import com.shuli.reader.sync.network.transport.LocalFileTransport
import com.shuli.reader.sync.network.transport.SyncTransport
import com.shuli.reader.sync.network.transport.WebDavTransport
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

// Part of T-23 SyncOrchestrator — BOTH 模式独立失败语义
class SyncOrchestratorTest {

    @Test
    fun `BOTH mode local fails cloud still executes`() = runTest {
        val localTransport = mockk<SyncTransport>(relaxed = true)
        val cloudTransport = mockk<SyncTransport>(relaxed = true)
        val engine = mockk<SyncEngine> {
            coEvery { sync(cloudTransport) } returns Unit
            coEvery { sync(localTransport) } throws IOException("disk full")
        }
        val orchestrator = SyncOrchestrator(cloudTransport, localTransport, engine)
        val result = orchestrator.sync(SyncTarget.BOTH)
        assertTrue(result.localResult!!.isFailure)
        assertTrue(result.cloudResult.isSuccess)
    }

    @Test
    fun `BOTH mode cloud fails local result still returned`() = runTest {
        val localTransport = mockk<SyncTransport>(relaxed = true)
        val cloudTransport = mockk<SyncTransport>(relaxed = true)
        val engine = mockk<SyncEngine> {
            coEvery { sync(cloudTransport) } throws IOException("auth failed")
            coEvery { sync(localTransport) } returns Unit
        }
        val orchestrator = SyncOrchestrator(cloudTransport, localTransport, engine)
        val result = orchestrator.sync(SyncTarget.BOTH)
        assertTrue(result.cloudResult.isFailure)
        assertTrue(result.localResult!!.isSuccess)
    }

    @Test
    fun `CLOUD mode null local result`() = runTest {
        val cloud = mockk<SyncTransport>(relaxed = true)
        val engine = mockk<SyncEngine>(relaxed = true)
        val orchestrator = SyncOrchestrator(cloud, localTransport = null, engine)
        val result = orchestrator.sync(SyncTarget.CLOUD)
        assertNull(result.localResult)
    }
}
