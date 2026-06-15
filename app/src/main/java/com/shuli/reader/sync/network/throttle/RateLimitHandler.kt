package com.shuli.reader.sync.network.throttle

import com.shuli.reader.sync.network.webdav.WebDavRateLimitException
import kotlin.math.min
import kotlin.math.pow

/**
 * 限流处理器（T-13）
 *
 * 计算重试等待时间，支持 Retry-After 头和指数退避。
 */
class RateLimitHandler {

    companion object {
        private const val BASE_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 3_600_000L // 1 hour
    }

    /**
     * 计算等待时间（毫秒）
     * @param exception 限流异常
     * @param attempt 重试次数（从 1 开始）
     */
    fun computeWaitMs(exception: WebDavRateLimitException, attempt: Int): Long {
        // 优先使用 Retry-After 头
        val retryAfter = exception.retryAfterSeconds
        if (retryAfter != null && retryAfter > 0) {
            return retryAfter * 1000
        }

        // 指数退避：base * 2^(attempt-1)，上限 1 小时
        val delay = BASE_DELAY_MS * 2.0.pow(attempt - 1).toLong()
        return min(delay, MAX_DELAY_MS)
    }
}
