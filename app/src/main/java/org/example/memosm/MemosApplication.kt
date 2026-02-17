package org.example.memosm

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.example.memosm.data.cache.MemoCacheDatabase
import org.example.memosm.data.cache.MemoCacheRepository
import org.example.memosm.di.appModule
import org.example.memosm.di.networkModule
import org.example.memosm.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MemosApplication : Application(), SingletonImageLoader.Factory {

    lateinit var memoCacheRepository: MemoCacheRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        startKoin {
            androidLogger()
            androidContext(this@MemosApplication)
            modules(appModule, networkModule, viewModelModule)
        }

        // Initialize Room database and cache repository
        // Note: In KMP/Koin, we might want to inject this repository where needed instead of holding it in Application
        // But keeping it for now to minimize changes outside of DI migration
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
