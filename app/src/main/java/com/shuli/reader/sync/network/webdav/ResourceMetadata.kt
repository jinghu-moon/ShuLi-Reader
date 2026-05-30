package com.shuli.reader.sync.network.webdav

/**
 * WebDAV 资源元数据（T-09）
 *
 * HEAD 请求返回的资源信息。
 */
data class ResourceMetadata(
    val etag: String? = null,
    val contentLength: Long = 0L,
    val lastModified: String? = null,
)
