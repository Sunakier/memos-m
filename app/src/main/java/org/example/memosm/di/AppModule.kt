package org.example.memosm.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.example.memosm.data.DataStoreManager
import org.example.memosm.data.DraftManager
import org.example.memosm.data.cache.MemoCacheDatabase
import org.example.memosm.data.cache.MemoCacheRepository
import org.example.memosm.data.cache.MemoDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStoreManager(@ApplicationContext context: Context): DataStoreManager {
        return DataStoreManager(context)
    }

    @Provides
    @Singleton
    fun provideDraftManager(@ApplicationContext context: Context): DraftManager {
        return DraftManager(context)
    }

    @Provides
    @Singleton
    fun provideMemoCacheDatabase(@ApplicationContext context: Context): MemoCacheDatabase {
        return MemoCacheDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMemoDao(database: MemoCacheDatabase): MemoDao {
        return database.memoDao()
    }

    @Provides
    @Singleton
    fun provideMemoCacheRepository(memoDao: MemoDao): MemoCacheRepository {
        return MemoCacheRepository(memoDao)
    }
}
