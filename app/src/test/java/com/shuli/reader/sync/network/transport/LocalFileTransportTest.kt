package com.shuli.reader.sync.network.transport

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// Part of T-16 LocalFileTransport with atomic write
class LocalFileTransportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `write creates file at correct path`() = runTest {
        val rootDir = tempFolder.newFolder("ShuLiReader")
        val transport = LocalFileTransport(rootDir)
        transport.write("manifest.json", """{"v":2}""".toByteArray())
        val file = File(rootDir, "manifest.json")
        assertTrue(file.exists())
        assertEquals("""{"v":2}""", file.readText())
    }

    @Test
    fun `write is atomic - no temp file left on success`() = runTest {
        val rootDir = tempFolder.newFolder("atomic_test")
        val transport = LocalFileTransport(rootDir)
        transport.write("test.json", "data".toByteArray())
        val tmpFiles = rootDir.listFiles { f -> f.name.startsWith(".tmp-") }
        assertTrue(tmpFiles.isNullOrEmpty())
    }

    @Test
    fun `read returns null for non-existent file`() = runTest {
        val rootDir = tempFolder.newFolder("read_test")
        val transport = LocalFileTransport(rootDir)
        assertNull(transport.read("doesnotexist.json"))
    }

    @Test
    fun `read returns file content`() = runTest {
        val rootDir = tempFolder.newFolder("read_content_test")
        File(rootDir, "test.json").writeText("""{"key":"value"}""")
        val transport = LocalFileTransport(rootDir)
        val result = transport.read("test.json")
        assertNotNull(result)
        assertArrayEquals("""{"key":"value"}""".toByteArray(), result)
    }

    @Test
    fun `list returns files in directory`() = runTest {
        val rootDir = tempFolder.newFolder("list_test")
        File(rootDir, "sub").mkdirs()
        File(rootDir, "sub/a.json").writeText("{}")
        File(rootDir, "sub/b.json").writeText("{}")
        val transport = LocalFileTransport(rootDir)
        val resources = transport.list("sub")
        val paths = resources.map { it.path }
        assertTrue(paths.contains("sub/a.json"))
        assertTrue(paths.contains("sub/b.json"))
    }

    @Test
    fun `delete removes file`() = runTest {
        val rootDir = tempFolder.newFolder("delete_test")
        File(rootDir, "to_delete.json").writeText("data")
        val transport = LocalFileTransport(rootDir)
        transport.delete("to_delete.json")
        assertFalse(File(rootDir, "to_delete.json").exists())
    }

    @Test
    fun `exists returns true for existing file`() = runTest {
        val rootDir = tempFolder.newFolder("exists_test")
        File(rootDir, "exists.json").writeText("data")
        val transport = LocalFileTransport(rootDir)
        assertTrue(transport.exists("exists.json"))
    }

    @Test
    fun `exists returns false for non-existing file`() = runTest {
        val rootDir = tempFolder.newFolder("not_exists_test")
        val transport = LocalFileTransport(rootDir)
        assertFalse(transport.exists("not_exists.json"))
    }
}
