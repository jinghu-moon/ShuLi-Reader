package com.shuli.reader.sync.network.webdav.auth

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-07 WebDavClient auth interceptors
class WebDavClientAuthTest {

    @Test
    fun `BasicAuthInterceptor adds Authorization header`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("OK"))
        server.start()

        val interceptor = BasicAuthInterceptor("user", "pass")
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val request = Request.Builder()
            .url(server.url("/"))
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)
        response.close()

        val recordedRequest = server.takeRequest()
        val authHeader = recordedRequest.getHeader("Authorization")
        assertNotNull(authHeader)
        assertTrue(authHeader!!.startsWith("Basic "))

        server.shutdown()
    }

    @Test
    fun `AdaptiveAuthInterceptor uses Basic by default`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("OK"))
        server.start()

        val interceptor = AdaptiveAuthInterceptor("user", "pass")
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val request = Request.Builder()
            .url(server.url("/"))
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)
        response.close()

        val recordedRequest = server.takeRequest()
        val authHeader = recordedRequest.getHeader("Authorization")
        assertNotNull(authHeader)
        assertTrue(authHeader!!.startsWith("Basic "))

        server.shutdown()
    }

    @Test
    fun `AdaptiveAuthInterceptor retries with Digest on 401`() {
        val server = MockWebServer()
        // First request returns 401 with Digest challenge
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("WWW-Authenticate", """Digest realm="test", nonce="abc123", qop="auth"""")
        )
        // Second request succeeds
        server.enqueue(MockResponse().setBody("OK"))
        server.start()

        val interceptor = AdaptiveAuthInterceptor("user", "pass")
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val request = Request.Builder()
            .url(server.url("/test"))
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)
        response.close()

        // First request should have Basic auth
        val firstRequest = server.takeRequest()
        assertTrue(firstRequest.getHeader("Authorization")!!.startsWith("Basic "))

        // Second request should have Digest auth
        val secondRequest = server.takeRequest()
        assertTrue(secondRequest.getHeader("Authorization")!!.startsWith("Digest "))

        server.shutdown()
    }
}
