package com.shuli.reader.sync.network.webdav.auth

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Basic Auth 拦截器（T-07）
 *
 * 为每个请求添加 Basic Authorization 头。
 */
class BasicAuthInterceptor(
    private val username: String,
    private val password: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", Credentials.basic(username, password))
            .build()
        return chain.proceed(request)
    }
}
