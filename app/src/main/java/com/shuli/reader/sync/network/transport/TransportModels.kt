package com.shuli.reader.sync.network.transport

/**
 * 传输层资源信息（T-14）
 */
data class TransportResourceInfo(
    val path: String,
    val isDirectory: Boolean = false,
    val contentLength: Long = 0L,
    val etag: String? = null,
)

/**
 * 传输层资源元数据（T-14）
 */
data class TransportResourceMetadata(
    val etag: String? = null,
    val contentLength: Long = 0L,
    val lastModified: String? = null,
)
