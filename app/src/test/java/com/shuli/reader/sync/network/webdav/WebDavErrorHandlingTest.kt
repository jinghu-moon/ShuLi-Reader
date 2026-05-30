package com.shuli.reader.sync.network.webdav

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

// Part of T-12 HTTP error classification
@RunWith(Parameterized::class)
class WebDavErrorHandlingTest(private val httpCode: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = listOf(401, 403, 404, 409, 412, 423, 429, 503)
    }

    @Test
    fun `each HTTP error maps to correct exception type`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(httpCode))
        server.start()

        val client = SyncWebDavClient(
            baseUrl = server.url("/").toString().removeSuffix("/"),
            username = "user",
            password = "pass",
            httpClient = OkHttpClient(),
        )

        try {
            // 使用 propfind 测试错误映射（get 对 404 返回 null）
            client.propfind("/any.json", depth = 0)
            fail("Expected WebDavException for HTTP $httpCode")
        } catch (e: WebDavException) {
            when (httpCode) {
                401, 403 -> assertTrue("Expected WebDavAuthException for $httpCode", e is WebDavAuthException)
                404 -> assertTrue("Expected WebDavNotFoundException for $httpCode", e is WebDavNotFoundException)
                409, 412 -> assertTrue("Expected WebDavConflictException for $httpCode", e is WebDavConflictException)
                423 -> assertTrue("Expected WebDavLockedException for $httpCode", e is WebDavLockedException)
                429, 503 -> assertTrue("Expected WebDavRateLimitException for $httpCode", e is WebDavRateLimitException)
            }
        }

        server.shutdown()
    }
}
