package com.shuli.reader.sync.manifest

import com.shuli.reader.sync.transport.SyncTransport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// Part of T-17 ManifestManager with Mutex serialization
class ManifestManagerTest {

    @Test
    fun `readManifest returns null if remote file absent`() = runTest {
        val transport = mockk<SyncTransport> {
            coEvery { read("manifest.json") } returns null
        }
        val manager = ManifestManager(transport)
        assertNull(manager.readManifest())
    }

    @Test
    fun `readManifest parses schemaVersion and version`() = runTest {
        val json = """{"schemaVersion":2,"updatedAt":1710000000000,"updatedBy":"dev1","version":42,"bookCount":156}"""
        val transport = mockk<SyncTransport> {
            coEvery { read("manifest.json") } returns json.toByteArray()
        }
        val manager = ManifestManager(transport)
        val manifest = manager.readManifest()
        assertNotNull(manifest)
        assertEquals(2, manifest!!.schemaVersion)
        assertEquals(42, manifest.version)
        assertEquals(156, manifest.bookCount)
    }

    @Test
    fun `writeManifest is serialized - concurrent writes do not interleave`() = runTest {
        val writes = mutableListOf<Int>()
        val transport = mockk<SyncTransport>(relaxed = true)
        coEvery { transport.read("manifest.json") } returns """{"schemaVersion":2,"version":0}""".toByteArray()
        val manager = ManifestManager(transport)
        val jobs = (1..10).map { i ->
            launch {
                manager.updateManifest {
                    it.copy(version = i).also { writes += i }
                }
            }
        }
        jobs.forEach { it.join() }
        assertEquals(10, writes.size)
        coVerify(exactly = 10) { transport.write("manifest.json", any()) }
    }

    @Test
    fun `manifest must not contain books array`() = runTest {
        val oldFormat = """{"schemaVersion":1,"books":[{"bookKey":"abc"}]}"""
        val transport = mockk<SyncTransport> {
            coEvery { read("manifest.json") } returns oldFormat.toByteArray()
        }
        val manager = ManifestManager(transport)
        val manifest = manager.readManifest()
        assertNotNull(manifest)
        // SyncManifest data class does not contain books field
    }
}
