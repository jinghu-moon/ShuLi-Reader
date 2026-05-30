package com.shuli.reader.sync.throttle

/**
 * 请求限流器（T-13）
 *
 * 滑动窗口限流，用于坚果云等 WebDAV 服务的请求频率控制。
 */
class RequestThrottler(
    private val maxRequests: Int,
    private val windowMs: Long,
) {
    private val timestamps = mutableListOf<Long>()

    /**
     * 记录一次请求
     */
    @Synchronized
    fun recordRequest() {
        val now = System.currentTimeMillis()
        timestamps.add(now)
        // 清理窗口外的记录
        timestamps.removeAll { it < now - windowMs }
    }

    /**
     * 检查是否被限流
     */
    @Synchronized
    fun isThrottled(): Boolean {
        val now = System.currentTimeMillis()
        timestamps.removeAll { it < now - windowMs }
        return timestamps.size >= maxRequests
    }

    /**
     * 获取当前窗口内的请求数
     */
    @Synchronized
    fun currentCount(): Int {
        val now = System.currentTimeMillis()
        timestamps.removeAll { it < now - windowMs }
        return timestamps.size
    }
}
