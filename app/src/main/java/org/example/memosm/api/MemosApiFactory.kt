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
            .addConverterFactory(GsonConverterFactory.create(GsonProvider.gson)).build()

        // Create the standard V1 implementation
        val v1Api = retrofit.create(MemosApiV0353::class.java)
        fun latestApiFallback(reason: String, exception: Exception? = null): MemosApi {
            Log.w("MemosApiFactory", "$reason, falling back to latest API implementation", exception)
            return MemosApiImpl(v1Api)
        }

        // Probe for version
        return try {
            val profile = v1Api.getInstanceProfile()
            val version = profile.version ?: "Unknown"
            Log.i("MemosApiFactory", "Detected Memos server version: $version")

            if (version.startsWith("0.26")) {
                Log.i("MemosApiFactory", "Using v0.26.0 API implementation")
                val v0260Api = retrofit.create(MemosApiV0260::class.java)
                MemosApiV0260Impl(v0260Api)
            } else if (version.startsWith("0.27")) {
                Log.i("MemosApiFactory", "Using v0.27.0 API implementation")
                val v0270Api = retrofit.create(MemosApiV0270::class.java)
                MemosApiV0270Impl(v0270Api)
            } else {
                latestApiFallback("Unsupported or unknown Memos server version: $version")
            }
        } catch (e: Exception) {
            latestApiFallback("Failed to probe Memos server version", e)
        }
    }
}
