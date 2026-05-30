package com.shuli.reader.sync.transport

import com.shuli.reader.sync.network.webdav.ResourceInfo
import com.shuli.reader.sync.network.webdav.ResourceMetadata
import com.shuli.reader.sync.network.webdav.SyncWebDavClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

// Part of T-15 WebDavTransport implementation
class WebDavTransportTest {

    @Test
    fun `read delegates to WebDavClient get`() = runTest {
        val expected = """{"v":1}""".toByteArray()
        val mockClient = mockk<SyncWebDavClient>()
        every { mockClient.get("/ShuLiReader/manifest.json") } returns expected
        val transport = WebDavTransport(mockClient, rootPath = "/ShuLiReader/")
        val result = transport.read("manifest.json")
        assertArrayEquals(expected, result)
    }

    @Test
    fun `write delegates to WebDavClient put`() = runTest {
        val mockClient = mockk<SyncWebDavClient>(relaxed = true)
        val transport = WebDavTransport(mockClient, rootPath = "/ShuLiReader/")
        transport.write("state/book1.json", """{"progress":0.5}""".toByteArray())
        verify { mockClient.put("/ShuLiReader/state/book1.json", any<ByteArray>(), any(), any()) }
    }

    @Test
    fun `ensureDirectories creates required dirs`() = runTest {
        val mockClient = mockk<SyncWebDavClient>(relaxed = true)
        val transport = WebDavTransport(mockClient, rootPath = "/ShuLiReader/")
        transport.ensureDirectories()
        verify { mockClient.mkcol("/ShuLiReader/books/") }
        verify { mockClient.mkcol("/ShuLiReader/state/") }
        verify { mockClient.mkcol("/ShuLiReader/bookmarks/") }
        verify { mockClient.mkcol("/ShuLiReader/notes/") }
        verify { mockClient.mkcol("/ShuLiReader/config/") }
        verify { mockClient.mkcol("/ShuLiReader/device/") }
    }

    @Test
    fun `list delegates to WebDavClient propfind`() = runTest {
        val mockClient = mockk<SyncWebDavClient> {
            every { propfind("/ShuLiReader/books/", 1) } returns listOf(
                ResourceInfo(path = "/ShuLiReader/books/book1.epub", etag = "\"abc\"", contentLength = 1000),
                ResourceInfo(path = "/ShuLiReader/books/book2.epub", etag = "\"def\"", contentLength = 2000),
            )
        }
        val transport = WebDavTransport(mockClient, rootPath = "/ShuLiReader/")
        val result = transport.list("books/")
        assertEquals(2, result.size)
        assertEquals("books/book1.epub", result[0].path)
    }

    @Test
    fun `exists returns true when head returns metadata`() = runTest {
        val mockClient = mockk<SyncWebDavClient> {
            every { head("/ShuLiReader/manifest.json") } returns ResourceMetadata(etag = "\"abc\"")
        }
        val transport = WebDavTransport(mockClient, rootPath = "/ShuLiReader/")
        assertEquals(true, transport.exists("manifest.json"))
    }

    @Test
    fun `exists returns false when head returns null`() = runTest {
        val mockClient = mockk<SyncWebDavClient> {
            every { head("/ShuLiReader/notexist.json") } returns null
        }
        val transport = WebDavTransport(mockClient, rootPath = "/ShuLiReader/")
        assertEquals(false, transport.exists("notexist.json"))
    }
}
