package com.shuli.reader.sync.transport

import com.shuli.reader.sync.network.webdav.SyncWebDavClient

/**
 * WebDAV 传输实现（T-15）
 *
 * 将 SyncTransport 接口方法委托给 SyncWebDavClient。
 * 自动拼接 rootPath 前缀。
 */
class WebDavTransport(
    private val client: SyncWebDavClient,
    private val rootPath: String,
) : SyncTransport {

    private val normalizedRoot = rootPath.trimEnd('/')

    override suspend fun read(path: String): ByteArray? {
        return client.get(buildFullPath(path))
    }

    override suspend fun write(path: String, data: ByteArray, etag: String?) {
        client.put(buildFullPath(path), data, ifMatch = etag)
    }

    override suspend fun delete(path: String) {
        client.delete(buildFullPath(path))
    }

    override suspend fun list(path: String): List<TransportResourceInfo> {
        val resources = client.propfind(buildFullPath(path), depth = 1)
        return resources.map { info ->
            TransportResourceInfo(
                path = info.path.removePrefix("$normalizedRoot/"),
                isDirectory = info.isDirectory,
                contentLength = info.contentLength,
                etag = info.etag,
            )
        }
    }

    override suspend fun exists(path: String): Boolean {
        return client.head(buildFullPath(path)) != null
    }

    override suspend fun getMetadata(path: String): TransportResourceMetadata? {
        val meta = client.head(buildFullPath(path)) ?: return null
        return TransportResourceMetadata(
            etag = meta.etag,
            contentLength = meta.contentLength,
            lastModified = meta.lastModified,
        )
    }

    /**
     * 确保所有必需的目录存在
     */
    fun ensureDirectories() {
        val dirs = listOf("books/", "state/", "bookmarks/", "notes/", "config/", "device/")
        for (dir in dirs) {
            client.mkcol("$normalizedRoot/$dir")
        }
    }

    private fun buildFullPath(relativePath: String): String {
        val normalizedPath = relativePath.trimStart('/')
        return "$normalizedRoot/$normalizedPath"
    }
}
