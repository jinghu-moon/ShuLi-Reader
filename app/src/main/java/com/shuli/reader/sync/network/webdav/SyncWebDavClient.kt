package com.shuli.reader.sync.network.webdav

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 同步用 WebDAV 客户端（T-08）
 *
 * 提供 PROPFIND、MKCOL、HEAD、GET、PUT、DELETE 操作。
 */
class SyncWebDavClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {

    /**
     * PROPFIND 操作：列出资源
     */
    fun propfind(path: String, depth: Int = 1): List<ResourceInfo> {
        val url = buildUrl(path)
        val body = PROPFIND_BODY.toRequestBody(null)

        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body)
            .header("Authorization", Credentials.basic(username, password))
            .header("Depth", depth.toString())
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()

        httpClient.newCall(request).execute().use { response ->
            when (response.code) {
                207 -> {
                    val xml = response.body?.string() ?: ""
                    return WebDavXmlParser.parsePropfindResponse(xml)
                }
                401, 403 -> throw WebDavAuthException("PROPFIND failed: HTTP ${response.code}")
                404 -> throw WebDavNotFoundException("PROPFIND failed: $path not found")
                409, 412 -> throw WebDavConflictException("PROPFIND failed: HTTP ${response.code}")
                423 -> throw WebDavLockedException("PROPFIND failed: resource locked")
                429, 503 -> throw WebDavRateLimitException("PROPFIND failed: rate limited")
                else -> throw WebDavException("PROPFIND failed: HTTP ${response.code}")
            }
        }
    }

    /**
     * MKCOL 操作：创建目录
     */
    fun mkcol(path: String) {
        val url = buildUrl(path)

        val request = Request.Builder()
            .url(url)
            .method("MKCOL", null)
            .header("Authorization", Credentials.basic(username, password))
            .build()

        httpClient.newCall(request).execute().use { response ->
            when (response.code) {
                201, 200, 405 -> Unit // 201 Created, 200 OK, 405 Already exists
                401, 403 -> throw WebDavAuthException("MKCOL failed: HTTP ${response.code}")
                409 -> throw WebDavConflictException("MKCOL failed: conflict")
                else -> throw WebDavException("MKCOL failed: HTTP ${response.code}")
            }
        }
    }

    /**
     * HEAD 操作：获取资源元数据
     * @return ResourceMetadata 或 null（404 时）
     */
    fun head(path: String): ResourceMetadata? {
        val url = buildUrl(path)

        val request = Request.Builder()
            .url(url)
            .head()
            .header("Authorization", Credentials.basic(username, password))
            .build()

        httpClient.newCall(request).execute().use { response ->
            return when (response.code) {
                200 -> ResourceMetadata(
                    etag = response.header("ETag"),
                    contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L,
                    lastModified = response.header("Last-Modified"),
                )
                404 -> null
                401, 403 -> throw WebDavAuthException("HEAD failed: HTTP ${response.code}")
                else -> throw WebDavException("HEAD failed: HTTP ${response.code}")
            }
        }
    }

    /**
     * DELETE 操作：删除资源
     */
    fun delete(path: String) {
        val url = buildUrl(path)

        val request = Request.Builder()
            .url(url)
            .delete()
            .header("Authorization", Credentials.basic(username, password))
            .build()

        httpClient.newCall(request).execute().use { response ->
            when (response.code) {
                200, 204 -> Unit // Success
                404 -> Unit // Already deleted
                401, 403 -> throw WebDavAuthException("DELETE failed: HTTP ${response.code}")
                else -> throw WebDavException("DELETE failed: HTTP ${response.code}")
            }
        }
    }

    /**
     * GET 操作：获取资源内容
     * @return ByteArray 或 null（404 时）
     */
    fun get(path: String): ByteArray? {
        val url = buildUrl(path)

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", Credentials.basic(username, password))
            .build()

        httpClient.newCall(request).execute().use { response ->
            return when (response.code) {
                200 -> response.body?.bytes()
                404 -> null
                401, 403 -> throw WebDavAuthException("GET failed: HTTP ${response.code}")
                409, 412 -> throw WebDavConflictException("GET failed: HTTP ${response.code}")
                423 -> throw WebDavLockedException("GET failed: resource locked")
                429, 503 -> throw WebDavRateLimitException("GET failed: rate limited")
                else -> throw WebDavException("GET failed: HTTP ${response.code}")
            }
        }
    }

    /**
     * PUT 操作：上传资源内容
     */
    fun put(
        path: String,
        data: ByteArray,
        ifMatch: String? = null,
        ifNoneMatch: String? = null,
        contentType: String = "application/json",
    ) {
        val url = buildUrl(path)
        val body = data.toRequestBody(contentType.toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .put(body)
            .header("Authorization", Credentials.basic(username, password))

        if (ifMatch != null) {
            requestBuilder.header("If-Match", ifMatch)
        }
        if (ifNoneMatch != null) {
            requestBuilder.header("If-None-Match", ifNoneMatch)
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            when (response.code) {
                200, 201, 204 -> Unit // Success
                401, 403 -> throw WebDavAuthException("PUT failed: HTTP ${response.code}")
                404 -> throw WebDavNotFoundException("PUT failed: $path not found")
                409, 412 -> throw WebDavConflictException("PUT failed: conflict HTTP ${response.code}")
                429, 503 -> throw WebDavRateLimitException("PUT failed: rate limited")
                else -> throw WebDavException("PUT failed: HTTP ${response.code}")
            }
        }
    }

    private fun buildUrl(path: String): String {
        val normalizedBase = baseUrl.trimEnd('/')
        val normalizedPath = path.trimStart('/')
        return if (normalizedPath.isEmpty()) normalizedBase else "$normalizedBase/$normalizedPath"
    }

    private companion object {
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:getetag/>
  </d:prop>
</d:propfind>"""
    }
}
