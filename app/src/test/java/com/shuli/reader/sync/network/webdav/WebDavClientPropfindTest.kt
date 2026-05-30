package com.shuli.reader.sync.network.webdav

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Part of T-08 WebDavClient PROPFIND
class WebDavClientPropfindTest {

    private val PROPFIND_RESPONSE_XML = """<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>/ShuLiReader/manifest.json</D:href>
    <D:propstat>
      <D:prop>
        <D:getcontentlength>256</D:getcontentlength>
        <D:getlastmodified>Fri, 01 Jan 2026 00:00:00 GMT</D:getlastmodified>
        <D:resourcetype/>
        <D:getetag>"abc123"</D:getetag>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
  <D:response>
    <D:href>/ShuLiReader/books/</D:href>
    <D:propstat>
      <D:prop>
        <D:resourcetype><D:collection/></D:resourcetype>
        <D:getetag>"def456"</D:getetag>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
  <D:response>
    <D:href>/ShuLiReader/books/book1.epub</D:href>
    <D:propstat>
      <D:prop>
        <D:getcontentlength>1024000</D:getcontentlength>
        <D:getlastmodified>Sat, 02 Jan 2026 00:00:00 GMT</D:getlastmodified>
        <D:resourcetype/>
        <D:getetag>"ghi789"</D:getetag>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
</D:multistatus>"""

    @Test
    fun `propfind depth 1 returns list of resources`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(207).setBody(PROPFIND_RESPONSE_XML))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/ShuLiReader/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        val resources = client.propfind("/ShuLiReader/", depth = 1)
        assertEquals(3, resources.size)

        val manifest = resources[0]
        assertEquals("/ShuLiReader/manifest.json", manifest.path)
        assertEquals("\"abc123\"", manifest.etag)
        assertEquals(256L, manifest.contentLength)

        val booksDir = resources[1]
        assertEquals("/ShuLiReader/books/", booksDir.path)
        assertTrue(booksDir.isDirectory)

        val book = resources[2]
        assertEquals("/ShuLiReader/books/book1.epub", book.path)
        assertEquals(1024000L, book.contentLength)

        server.shutdown()
    }

    @Test
    fun `propfind 401 throws WebDavAuthException`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        try {
            client.propfind("/", depth = 0)
            fail("Expected WebDavAuthException")
        } catch (e: WebDavAuthException) {
            // Expected
        }

        server.shutdown()
    }

    @Test
    fun `propfind 404 throws WebDavNotFoundException`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        try {
            client.propfind("/notexist/", depth = 0)
            fail("Expected WebDavNotFoundException")
        } catch (e: WebDavNotFoundException) {
            // Expected
        }

        server.shutdown()
    }
}
