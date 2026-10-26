package com.example.core.network

import com.example.core.datastore.SessionPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class DynamicBaseUrlInterceptor(
    private val sessionPreferences: SessionPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val customUrlStr = sessionPreferences.getServerUrlSync()
        val targetBaseUrl = customUrlStr.toHttpUrlOrNull()

        if (targetBaseUrl != null && isValidTargetBaseUrl(targetBaseUrl)) {
            val oldUrl = request.url
            val newUrlBuilder = oldUrl.newBuilder()
                .scheme(targetBaseUrl.scheme)
                .host(targetBaseUrl.host)
                .port(targetBaseUrl.port)

            request = request.newBuilder().url(newUrlBuilder.build()).build()
        }

        return chain.proceed(request)
    }

    companion object {
        fun isValidTargetBaseUrl(url: okhttp3.HttpUrl): Boolean {
            // Enforce HTTPS for non-loopback domains to protect JWTs and patient telemetry
            if (url.scheme == "https") return true
            if (url.scheme == "http") {
                val host = url.host
                return host == "10.0.2.2" || host == "localhost" || host == "127.0.0.1" ||
                        host.startsWith("192.168.") || host.startsWith("10.")
            }
            return false
        }
    }
}
