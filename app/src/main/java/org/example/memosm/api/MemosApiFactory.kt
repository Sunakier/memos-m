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

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        // Create the standard V1 implementation
        val v1Api = retrofit.create(MemosApiV0353::class.java)

        // Probe for version
        return try {
            val profile = v1Api.getInstanceProfile()
            val version = profile.version ?: "Unknown"
            Log.i("MemosApiFactory", "Detected Memos server version: $version")
            
            // In the future, we can switch on version here.
            // For now, we only have one implementation.
            MemosApiImpl(v1Api)
        } catch (e: Exception) {
            Log.w("MemosApiFactory", "Failed to probe version, defaulting to V1 implementation", e)
            // Fallback to V1 impl even if probe failed (maybe network error, but let caller handle it when they make actual calls)
            // Or should we fail here? If probe fails, likely subsequent calls fail too.
            // But strict failure might block offline usage if we aggressively probe.
            // Ideally, we shouldn't probe if offline. 
            // However, this factory is usually called when switching accounts (which implies online) or app startup.
            MemosApiImpl(v1Api)
        }
    }
}
