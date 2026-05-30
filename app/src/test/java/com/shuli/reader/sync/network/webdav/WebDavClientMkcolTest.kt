package com.shuli.reader.sync.network.webdav

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// Part of T-09 WebDavClient MKCOL / HEAD / DELETE
class WebDavClientMkcolTest {

    @Test
    fun `mkcol sends MKCOL method`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(201))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        client.mkcol("/ShuLiReader/books/")
        val recorded = server.takeRequest()
        assertEquals("MKCOL", recorded.method)
        assertEquals("/ShuLiReader/books/", recorded.path)

        server.shutdown()
    }

    @Test
    fun `head returns etag and content length`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("ETag", "\"abc123\"")
                .addHeader("Content-Length", "1024")
        )
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/ShuLiReader/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        val meta = client.head("/ShuLiReader/manifest.json")
        assertNotNull(meta)
        assertEquals("\"abc123\"", meta!!.etag)
        assertEquals(1024L, meta.contentLength)

        server.shutdown()
    }

    @Test
    fun `head on missing file returns null`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        val meta = client.head("/notexist.json")
        assertNull(meta)

        server.shutdown()
    }

    @Test
    fun `delete sends DELETE method`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/ShuLiReader/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        client.delete("/ShuLiReader/state/bookkey123.json")
        assertEquals("DELETE", server.takeRequest().method)

        server.shutdown()
    }
}
