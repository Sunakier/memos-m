package org.example.memosm.api

import okhttp3.Interceptor
import okhttp3.Response

class GrpcCookieInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())
        val isAuthFailure = originalResponse.code == 401

        // Memos v0.26+ (gRPC-Gateway) might return cookies in this header
        val grpcCookies = originalResponse.headers("grpc-metadata-set-cookie")

        val builder = originalResponse.newBuilder()
        var modified = false

        if (grpcCookies.isNotEmpty()) {
            android.util.Log.d(
                "GrpcCookieInterceptor",
                "Found ${grpcCookies.size} grpc cookies: $grpcCookies"
            )

            grpcCookies.forEach { headerValue ->
                val potentialCookies = splitMergedCookies(headerValue)
                potentialCookies.forEach { cookie ->
                    if (isAuthFailure && isClearingRefreshCookie(cookie)) {
                        android.util.Log.i(
                            "GrpcCookieInterceptor",
                            "Skipping clearing refresh cookie on 401: $cookie"
                        )
                    } else {
                        builder.addHeader("Set-Cookie", cookie)
                        modified = true
                    }
                }
            }
        }

        // Also protect regular Set-Cookie headers from clearing the refresh token on 401.
        // This prevents the CookieJar from losing the refresh token just when we need it for Authenticator.
        if (isAuthFailure) {
            val setCookies = originalResponse.headers("Set-Cookie")
            if (setCookies.any { isClearingRefreshCookie(it) }) {
                builder.removeHeader("Set-Cookie")
                setCookies.forEach { cookie ->
                    if (isClearingRefreshCookie(cookie)) {
                        android.util.Log.i(
                            "GrpcCookieInterceptor",
                            "Filtering out regular Set-Cookie clearing refresh cookie on 401: $cookie"
                        )
                    } else {
                        builder.addHeader("Set-Cookie", cookie)
                    }
                }
                modified = true
            }
        }

        return if (modified) builder.build() else originalResponse
    }

    private fun isClearingRefreshCookie(cookie: String): Boolean {
        val trimmed = cookie.trim()
        if (!trimmed.startsWith("memos_refresh=")) return false

        // Check for common clear-cookie patterns
        // 1. Empty value
        if (trimmed.startsWith("memos_refresh=;") || trimmed.startsWith("memos_refresh= ")) return true

        // 2. Expired date
        if (trimmed.contains("Expires=Thu, 01 Jan 1970")) return true

        // 3. Max-Age=0 or -1
        if (trimmed.contains("Max-Age=0") || trimmed.contains("Max-Age=-1")) return true

        return false
    }

    // Helper to split merged headers (e.g. from gRPC-Gateway or proxy)
    // Tries to split on ", " but ignores commas inside Date strings.
    private fun splitMergedCookies(header: String): List<String> {
        val result = mutableListOf<String>()
        val parts = header.split(", ")
        val buffer = StringBuilder()

        parts.forEach { part ->
            if (buffer.isNotEmpty()) {
                // Heuristic: If this part looks like a new cookie (has '=' and not a known attribute),
                // then start a new cookie string. Otherwise, it's likely a continuation of a date.
                if (isNewCookie(part)) {
                    result.add(buffer.toString())
                    buffer.clear()
                    buffer.append(part)
                } else {
                    buffer.append(", ").append(part)
                }
            } else {
                buffer.append(part)
            }
        }
        if (buffer.isNotEmpty()) result.add(buffer.toString())
        return result
    }

    private fun isNewCookie(part: String): Boolean {
        val eqIndex = part.indexOf('=')
        if (eqIndex == -1) return false

        val name = part.substring(0, eqIndex).trim().lowercase()
        // Common cookie attributes that are NOT new cookies
        val attributes =
            setOf("path", "domain", "expires", "max-age", "secure", "httponly", "samesite")
        return name !in attributes
    }
}
