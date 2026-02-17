package org.example.memosm

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.example.memosm.data.cache.MemoCacheDatabase
import org.example.memosm.data.cache.MemoCacheRepository

@HiltAndroidApp
class MemosApplication : Application(), SingletonImageLoader.Factory {

    lateinit var memoCacheRepository: MemoCacheRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Room database and cache repository
        val database = MemoCacheDatabase.getInstance(this)
        memoCacheRepository = MemoCacheRepository(database.memoDao())
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val dispatcher = Dispatcher().apply {
            maxRequests = 5
            maxRequestsPerHost = 5
        }

        val okHttpClient = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: MemosApplication
            private set
    }
}
