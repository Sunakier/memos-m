package org.example.memosm.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that copies the Cookie header to Grpc-Metadata-Cookie for gRPC-Gateway compatibility.
 * 
 * Memos v0.26+ uses gRPC-Gateway to expose REST API endpoints at /api/v1/.
 * The server reads cookies from gRPC metadata (via metadata.FromIncomingContext()),
 * not from the HTTP Cookie header directly.
 * 
 * gRPC-Gateway converts headers with the "Grpc-Metadata-" prefix into gRPC metadata.
 * For example, "Grpc-Metadata-Cookie: memos_refresh=xxx" becomes metadata with key "cookie".
 * 
 * This interceptor ensures that cookies are properly forwarded to the server for
 * authentication methods that rely on them (e.g., RefreshToken endpoint).
 */
class GrpcMetadataCookieInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Check if this is a request to an API endpoint that might need cookies in gRPC metadata
        val url = originalRequest.url.toString()
        if (!url.contains("/api/v1/")) {
            return chain.proceed(originalRequest)
        }
        
        // Get the Cookie header (if present)
        val cookieHeader = originalRequest.header("Cookie")
        
        if (cookieHeader.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }
        
        Log.d("GrpcMetadataCookie", "Adding Grpc-Metadata-Cookie header for: ${originalRequest.url}")
        
        // Add the Grpc-Metadata-Cookie header so gRPC-Gateway forwards it to gRPC metadata
        val modifiedRequest = originalRequest.newBuilder()
            .addHeader("Grpc-Metadata-Cookie", cookieHeader)
            .build()
        
        return chain.proceed(modifiedRequest)
    }
}
