package com.shuli.reader.sync.network.webdav

/**
 * WebDAV 资源信息（T-08）
 */
data class ResourceInfo(
    val path: String,
    val etag: String? = null,
    val contentLength: Long = 0L,
    val lastModified: String? = null,
    val isDirectory: Boolean = false,
)
