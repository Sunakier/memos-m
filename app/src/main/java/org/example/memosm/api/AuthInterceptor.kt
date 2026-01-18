package org.example.memosm.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private var token: String) : Interceptor {
    
    fun updateToken(newToken: String) {
        token = newToken
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        
        if (token.isNotEmpty()) {
            builder.header("Authorization", "Bearer $token")
        }
        
        val request = builder.build()
        Log.d("MemosApi", "--> ${request.method} ${request.url}")
        if (token.isNotEmpty()) {
            Log.d("MemosApi", "Authorization: Bearer ${token.take(10)}...")
        }

        val startTime = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e("MemosApi", "<-- HTTP FAILED: $e")
            throw e
        }
        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1e6

        Log.d("MemosApi", "<-- ${response.code} ${request.url} (${durationMs.toInt()}ms)")
        
        return response
    }
}
