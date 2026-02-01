package org.example.memosm.api

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object MemosApiFactory {

    suspend fun create(baseUrl: String, client: OkHttpClient): MemosApi {
        var normalizedBaseUrl = baseUrl.trimEnd('/') + "/"
        if (normalizedBaseUrl.endsWith("/api/v1/")) {
            normalizedBaseUrl = normalizedBaseUrl.removeSuffix("api/v1/")
        }

        val retrofit = Retrofit.Builder().baseUrl(normalizedBaseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()

        // Create the standard V1 implementation
        val v1Api = retrofit.create(MemosApiV0353::class.java)

        // Probe for version
        return try {
            val profile = v1Api.getInstanceProfile()
            val version = profile.version ?: "Unknown"
            Log.i("MemosApiFactory", "Detected Memos server version: $version")

            if (version.startsWith("0.26")) {
                Log.i("MemosApiFactory", "Using v0.26.0 API implementation")
                val v0260Api = retrofit.create(MemosApiV0260::class.java)
                MemosApiV0260Impl(v0260Api)
            } else {
                // Default to V1/0.3.53
                MemosApiImpl(v1Api)
            }
        } catch (e: Exception) {
            Log.w("MemosApiFactory", "Failed to probe version, defaulting to V1 implementation", e)
            MemosApiImpl(v1Api)
        }
    }
}
