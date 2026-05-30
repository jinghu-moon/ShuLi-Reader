package com.shuli.reader.sync.network.webdav

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Part of T-10 WebDavClient GET / PUT
class WebDavClientGetPutTest {

    @Test
    fun `get returns byte content`() {
        val server = MockWebServer()
        val body = """{"schemaVersion":2}""".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(body)))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        val result = client.get("/ShuLiReader/manifest.json")
        assertArrayEquals(body, result)

        server.shutdown()
    }

    @Test
    fun `get returns null for 404`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        assertNull(client.get("/notexist.json"))

        server.shutdown()
    }

    @Test
    fun `put sends content with correct content-type`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(201))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        client.put("/ShuLiReader/manifest.json", """{"v":1}""".toByteArray())
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.getHeader("Content-Type")!!.contains("application/json"))

        server.shutdown()
    }

    @Test
    fun `put with ifMatch sends If-Match header`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        client.put("/path.json", data = ByteArray(0), ifMatch = "\"etag123\"")
        assertEquals("\"etag123\"", server.takeRequest().getHeader("If-Match"))

        server.shutdown()
    }

    @Test
    fun `put returns 412 throws WebDavConflictException`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(412))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        try {
            client.put("/path.json", ByteArray(0), ifMatch = "\"stale\"")
            fail("Expected WebDavConflictException")
        } catch (e: WebDavConflictException) {
            // Expected
        }

        server.shutdown()
    }
}
