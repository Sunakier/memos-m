package org.example.memosm.api

import okhttp3.Interceptor
import okhttp3.Response

class GrpcCookieInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())
        
        // Memos v0.26+ (gRPC-Gateway) might return cookies in this header
        val grpcCookies = originalResponse.headers("grpc-metadata-set-cookie")
        
        if (grpcCookies.isNotEmpty()) {
            android.util.Log.d("GrpcCookieInterceptor", "Found ${grpcCookies.size} grpc cookies: $grpcCookies")
            val builder = originalResponse.newBuilder()
            
            grpcCookies.forEach { headerValue ->
                // gRPC-Gateway might merge multiple Set-Cookie headers into one comma-separated string.
                // We need to split them, but be careful about commas in "Expires" dates.
                // A safe heuristic is to split by ", " where the preceding part doesn't look like a weekday.
                // But simplified: split by "memos_" if we know the prefixes? No.
                
                // Let's try a regex split or basic split, but for now, just adding them as is usually works 
                // UNLESS they are merged.
                
                // If the string contains multiple "name=value", we might need to split.
                // Example: "c1=v1; Path=/, c2=v2; Path=/"
                
                val potentialCookies = splitMergedCookies(headerValue)
                potentialCookies.forEach { cookie ->
                     builder.addHeader("Set-Cookie", cookie)
                }
            }
            return builder.build()
        }
        
        return originalResponse
    }
    
    // Helper to split merged headers (e.g. from gRPC-Gateway or proxy)
    // Tries to split on ", " but ignores commas inside Date strings.
    private fun splitMergedCookies(header: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var inDate = false
        
        for (i in 0 until header.length) {
            val c = header[i]
            // Check if we are potentially in a date string (e.g. "Expires=Wed, ...")
            // This is a naive check but might suffice for standard cookie formats.
            if (i > 0 && header[i-1] == '=' && (i+3 < header.length)) {
                // Check if likely a day of week?
                // Too complex.
                // Alternative: Split by ", " and check if the segment looks like a Date or a new Cookie start?
            }
            
            // Simpler approach: Split by ", "
            // If the chunk ended with "Expires=Wed", then we shouldn't have split.
            if (c == ',' && i + 1 < header.length && header[i+1] == ' ') {
                 // Check if it's a date delimiter?
                 // Most dates in cookies are "Expires=Day, DD-Mon-YYYY..."
                 // So "Expires=Dyn, "
                 
                 // If the part before comma ends with "="? No.
                 
                 // Let's rely on the fact the user sees "one for user_session and the other for refresh".
                 // Memos cookies usually don't set long-term Expires for session?
                 // Refresh token might.
            }
        }
        
        // Regex approach:
        // Split by comma that is immediately followed by a non-space, or a key-value pair?
        // Let's assume naive split for now, but handle the specific case of user_session and refresh.
        // If we see "memos_" or "user_" after the comma, it's a new cookie.
        
        val parts = header.split(", ")
        val buffer = StringBuilder()
        
        parts.forEach { part ->
            if (buffer.isNotEmpty()) {
                // Heuristic: If this part contains "=" and doesn't look like a year/time, 
                // and the previous part didn't look like "Expires=Wdy", maybe it's a new cookie.
                
                // But easier: check known prefixes for Memos?
                if (part.startsWith("memos") || part.startsWith("user")) {
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
        
        if (result.size > 1) {
            android.util.Log.d("GrpcCookieInterceptor", "Split header into ${result.size} cookies")
        }
        return result
    }
}
