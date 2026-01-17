package org.example.memosm.ui.component.item.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@OptIn(UnstableApi::class)
object MediaCache {
    private val aspectRatios = ConcurrentHashMap<String, Float>()
    private var simpleCache: SimpleCache? = null
    
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .build()
    }

    fun getAspectRatio(key: String?): Float? {
        if (key.isNullOrBlank()) return null
        return aspectRatios[key]
    }

    fun setAspectRatio(key: String?, ratio: Float) {
        if (key.isNullOrBlank()) return
        aspectRatios[key] = ratio
    }

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "media_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(250 * 1024 * 1024) // 250MB
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return simpleCache!!
    }

    fun createDataSourceFactory(context: Context, token: String?): DataSource.Factory {
        val httpDataSourceFactory: HttpDataSource.Factory = if (token != null) {
            OkHttpDataSource.Factory(okHttpClient)
                .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        } else {
            OkHttpDataSource.Factory(okHttpClient)
        }

        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setCacheWriteDataSinkFactory(null) // Disables writing to cache if needed, but we want it enabled
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
