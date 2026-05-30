package com.shuli.reader.sync.network.webdav

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Part of T-11 ETag and If-Match optimistic locking
class WebDavETagTest {

    @Test
    fun `put without ifMatch does not send If-Match header`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(201))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        client.put("/path.json", ByteArray(0))
        assertNull(server.takeRequest().getHeader("If-Match"))

        server.shutdown()
    }

    @Test
    fun `put with ifNoneMatch sends If-None-Match header`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(201))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        client.put("/path.json", ByteArray(0), ifNoneMatch = "*")
        assertEquals("*", server.takeRequest().getHeader("If-None-Match"))

        server.shutdown()
    }
}
