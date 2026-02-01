package org.example.memosm.api

import okhttp3.Interceptor
import okhttp3.Response

class GrpcCookieInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())
        
        // Memos v0.26+ (gRPC-Gateway) might return cookies in this header
        val grpcCookies = originalResponse.headers("grpc-metadata-set-cookie")
        
        if (grpcCookies.isNotEmpty()) {
            android.util.Log.d("GrpcCookieInterceptor", "Found ${grpcCookies.size} grpc cookies. Mapping to Set-Cookie.")
            val builder = originalResponse.newBuilder()
            grpcCookies.forEach { cookie ->
                builder.addHeader("Set-Cookie", cookie)
            }
            return builder.build()
        }
        
        return originalResponse
    }
}
