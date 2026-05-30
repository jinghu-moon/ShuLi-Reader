package com.shuli.reader.sync.engine

import com.shuli.reader.sync.manifest.ManifestManager
import com.shuli.reader.sync.manifest.SyncManifest
import com.shuli.reader.sync.state.SyncState
import com.shuli.reader.sync.state.SyncStateMachine
import com.shuli.reader.sync.transport.SyncTransport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-22 SyncEngine 核心编排
class SyncEngineTest {

    @Test
    fun `sync transitions state machine to SUCCESS`() = runTest {
        val transport = mockk<SyncTransport>(relaxed = true)
        coEvery { transport.read("manifest.json") } returns """{"schemaVersion":2,"version":0}""".toByteArray()

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine)

        engine.sync(transport)

        assertEquals(SyncState.SUCCESS, stateMachine.state.value)
    }

    @Test
    fun `sync reads remote manifest`() = runTest {
        val transport = mockk<SyncTransport>(relaxed = true)
        coEvery { transport.read("manifest.json") } returns """{"schemaVersion":2,"version":42,"bookCount":10}""".toByteArray()

        val manifestManager = ManifestManager(transport)
        val stateMachine = SyncStateMachine()
        val engine = SyncEngine(manifestManager, stateMachine)

        engine.sync(transport)

        coVerify { transport.read("manifest.json") }
    }
}
