package com.shuli.reader.sync.network.throttle

import com.shuli.reader.sync.network.webdav.WebDavRateLimitException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Part of T-13 rate limiting and exponential backoff
class RateLimitHandlerTest {

    @Test
    fun `RateLimitHandler extracts Retry-After from 429 response`() {
        val fakeException = WebDavRateLimitException(retryAfterSeconds = 300)
        val handler = RateLimitHandler()
        val delay = handler.computeWaitMs(fakeException, attempt = 1)
        assertEquals(300_000L, delay)
    }

    @Test
    fun `without Retry-After header uses exponential backoff`() {
        val handler = RateLimitHandler()
        val d1 = handler.computeWaitMs(WebDavRateLimitException(), attempt = 1)
        val d2 = handler.computeWaitMs(WebDavRateLimitException(), attempt = 2)
        val d3 = handler.computeWaitMs(WebDavRateLimitException(), attempt = 3)
        assertTrue("d2 should be greater than d1", d2 > d1)
        assertTrue("d3 should be greater than d2", d3 > d2)
        assertTrue("d3 should be less than 1 hour", d3 < 3_600_000L)
    }

    @Test
    fun `RequestThrottler enforces 600 requests per 30 min limit`() {
        val throttler = RequestThrottler(maxRequests = 600, windowMs = 30 * 60 * 1000L)
        repeat(600) { throttler.recordRequest() }
        assertTrue(throttler.isThrottled())
    }
}
