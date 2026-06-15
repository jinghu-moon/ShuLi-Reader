package com.shuli.reader.sync.network.transport

import com.shuli.reader.sync.crypto.SyncCryptoManager
import com.shuli.reader.sync.network.webdav.SyncWebDavClient

/**
 * WebDAV 传输实现（T-15）
 *
 * 将 SyncTransport 接口方法委托给 SyncWebDavClient。
 * 自动拼接 rootPath 前缀。
 * E2EE 模式下透明加密/解密（manifest.json 除外）。
 */
class WebDavTransport(
    private val client: SyncWebDavClient,
    private val rootPath: String,
    private val cryptoManager: SyncCryptoManager? = null,
) : SyncTransport {

    private val normalizedRoot = rootPath.trimEnd('/')

    override suspend fun read(path: String): ByteArray? {
        val raw = client.get(buildFullPath(path)) ?: return null
        return if (cryptoManager != null && !isPlaintextPath(path)) {
            cryptoManager.decrypt(raw)
        } else {
            raw
        }
    }

    override suspend fun write(path: String, data: ByteArray, etag: String?) {
        val payload = if (cryptoManager != null && !isPlaintextPath(path)) {
            cryptoManager.encrypt(data)
        } else {
            data
        }
        client.put(buildFullPath(path), payload, ifMatch = etag)
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
    override fun ensureDirectories() {
        val dirs = listOf("books/", "state/", "bookmarks/", "notes/", "config/", "device/")
        for (dir in dirs) {
            client.mkcol("$normalizedRoot/$dir")
        }
    }

    private fun buildFullPath(relativePath: String): String {
        val normalizedPath = relativePath.trimStart('/')
        return "$normalizedRoot/$normalizedPath"
    }

    /** manifest.json 保持明文以便无密钥时发现远端状态 */
    private fun isPlaintextPath(path: String): Boolean {
        return path == "manifest.json" || path.endsWith("/manifest.json")
    }
}
