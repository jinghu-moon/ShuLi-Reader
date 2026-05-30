package com.shuli.reader.sync.network.webdav.auth

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * Digest Auth 拦截器（T-07）
 *
 * 实现 RFC 2617 Digest 认证。
 */
class DigestAuthInterceptor(
    private val username: String,
    private val password: String,
) : Interceptor {

    private var realm: String = ""
    private var nonce: String = ""
    private var qop: String = ""
    private val nc = AtomicInteger(1)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 401) {
            val wwwAuth = response.header("WWW-Authenticate") ?: return response
            if (wwwAuth.startsWith("Digest ")) {
                parseChallenge(wwwAuth)
                response.close()

                val digestHeader = buildDigestHeader(request.method, request.url.encodedPath)
                val newRequest = request.newBuilder()
                    .header("Authorization", digestHeader)
                    .build()
                return chain.proceed(newRequest)
            }
        }

        return response
    }

    private fun parseChallenge(wwwAuth: String) {
        val params = wwwAuth.removePrefix("Digest ")
            .split(",")
            .map { it.trim().split("=", limit = 2) }
            .filter { it.size == 2 }
            .associate { it[0].trim() to it[1].trim().removeSurrounding("\"") }

        realm = params["realm"] ?: ""
        nonce = params["nonce"] ?: ""
        qop = params["qop"] ?: ""
    }

    private fun buildDigestHeader(method: String, uri: String): String {
        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$uri")
        val nonceCount = String.format("%08x", nc.getAndIncrement())
        val cnonce = generateCnonce()

        val response = if (qop.isNotEmpty()) {
            md5("$ha1:$nonce:$nonceCount:$cnonce:$qop:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        return buildString {
            append("Digest ")
            append("""username="$username"""")
            append(""", realm="$realm"""")
            append(""", nonce="$nonce"""")
            append(""", uri="$uri"""")
            append(""", response="$response"""")
            if (qop.isNotEmpty()) {
                append(""", qop=$qop""")
                append(""", nc=$nonceCount""")
                append(""", cnonce="$cnonce"""")
            }
        }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun generateCnonce(): String {
        val bytes = ByteArray(8)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
