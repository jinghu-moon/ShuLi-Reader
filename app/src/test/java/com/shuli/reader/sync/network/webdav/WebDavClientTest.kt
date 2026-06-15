package com.shuli.reader.sync.network.webdav

import okhttp3.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WebDavClientTest {

    @Test
    fun testConnection_sendsPropfindToRootPath() {
        val transport = FakeWebDavTransport()
        val client = WebDavClient(config(), transport)

        assertTrue(client.testConnection())

        assertEquals("PROPFIND", transport.lastMethod)
        assertEquals("https://example.com/dav", transport.lastUrl)
        assertEquals("0", transport.lastHeaders["Depth"])
        assertEquals(Credentials.basic("user", "pass"), transport.lastHeaders["Authorization"])
    }

    @Test
    fun get_joinsStandardPath() {
        val transport = FakeWebDavTransport(body = "payload")
        val client = WebDavClient(config(baseUrl = "https://example.com/dav/"), transport)

        val response = client.get("/books/progress.json")

        assertEquals("GET", transport.lastMethod)
        assertEquals("https://example.com/dav/books/progress.json", transport.lastUrl)
        assertEquals("payload", response.body)
    }

    @Test
    fun put_sendsBody() {
        val transport = FakeWebDavTransport()
        val client = WebDavClient(config(), transport)

        client.put("progress/book.json", """{"pos":12}""")

        assertEquals("PUT", transport.lastMethod)
        assertEquals("""{"pos":12}""", transport.lastBody)
    }

    @Test(expected = IOException::class)
    fun failedStatusCode_throwsIOException() {
        val transport = FakeWebDavTransport(code = 500)
        val client = WebDavClient(config(), transport)

        client.get("progress/book.json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBaseUrl_throwsException() {
        WebDavClient(config(baseUrl = "file:///tmp"), FakeWebDavTransport())
            .testConnection()
    }

    private fun config(baseUrl: String = "https://example.com/dav"): WebDavConfig {
        return WebDavConfig(baseUrl = baseUrl, username = "user", password = "pass")
    }

    private class FakeWebDavTransport(
        private val code: Int = 207,
        private val body: String = "",
    ) : WebDavTransport {
        var lastMethod = ""
        var lastUrl = ""
        var lastBody: String? = null
        var lastHeaders: Map<String, String> = emptyMap()

        override fun execute(
            method: String,
            url: String,
            body: String?,
            headers: Map<String, String>,
        ): WebDavResponse {
            lastMethod = method
            lastUrl = url
            lastBody = body
            lastHeaders = headers
            return WebDavResponse(code = code, body = this.body)
        }
    }
}
