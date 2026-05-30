package com.shuli.reader.sync.network.webdav

/**
 * WebDAV 异常层级（T-08）
 */
open class WebDavException(message: String, cause: Throwable? = null) : Exception(message, cause)

class WebDavAuthException(message: String = "WebDAV authentication failed") : WebDavException(message)

class WebDavNotFoundException(message: String = "WebDAV resource not found") : WebDavException(message)

class WebDavConflictException(message: String = "WebDAV conflict") : WebDavException(message)

class WebDavRateLimitException(
    message: String = "WebDAV rate limited",
    val retryAfterSeconds: Long? = null,
) : WebDavException(message)

class WebDavLockedException(message: String = "WebDAV resource locked") : WebDavException(message)
