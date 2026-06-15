package com.shuli.reader.sync.network.webdav

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class WebDavConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
)

data class WebDavResponse(
    val code: Int,
    val body: String,
) {
    val isSuccessful: Boolean get() = code in 200..299
}

interface WebDavTransport {
    fun execute(
        method: String,
        url: String,
        body: String?,
        headers: Map<String, String>,
    ): WebDavResponse
}

class OkHttpWebDavTransport(
    private val client: OkHttpClient = OkHttpClient(),
) : WebDavTransport {
    override fun execute(
        method: String,
        url: String,
        body: String?,
        headers: Map<String, String>,
    ): WebDavResponse {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }

        val requestBody = body?.toRequestBody("application/xml; charset=utf-8".toMediaType())
        requestBuilder.method(method, requestBody)

        client.newCall(requestBuilder.build()).execute().use { response ->
            return WebDavResponse(
                code = response.code,
                body = response.body?.string().orEmpty(),
            )
        }
    }
}

class WebDavClient(
    private val config: WebDavConfig,
    private val transport: WebDavTransport = OkHttpWebDavTransport(),
) {
    fun testConnection(): Boolean {
        return propfind("", depth = 0).isSuccessful
    }

    fun propfind(path: String, depth: Int = 1): WebDavResponse {
        return request(
            method = "PROPFIND",
            path = path,
            body = PROPFIND_BODY,
            extraHeaders = mapOf("Depth" to depth.toString()),
        )
    }

    fun get(path: String): WebDavResponse {
        return request(method = "GET", path = path, body = null)
    }

    fun put(path: String, body: String): WebDavResponse {
        return request(method = "PUT", path = path, body = body)
    }

    private fun request(
        method: String,
        path: String,
        body: String?,
        extraHeaders: Map<String, String> = emptyMap(),
    ): WebDavResponse {
        require(config.baseUrl.startsWith("http://") || config.baseUrl.startsWith("https://")) {
            "Invalid WebDAV base URL"
        }

        val url = joinUrl(config.baseUrl, path)
        val headers = buildMap {
            put("Authorization", Credentials.basic(config.username, config.password))
            putAll(extraHeaders)
        }
        val response = transport.execute(method, url, body, headers)
        if (!response.isSuccessful) {
            throw IOException("WebDAV request failed: $method $path HTTP ${response.code}")
        }
        return response
    }

    private fun joinUrl(baseUrl: String, path: String): String {
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
  </d:prop>
</d:propfind>"""
    }
}
