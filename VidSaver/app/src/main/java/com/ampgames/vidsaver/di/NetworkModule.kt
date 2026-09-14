package com.ampgames.vidsaver.di

import android.content.Context
import com.ampgames.vidsaver.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Cache
import okhttp3.OkHttpClient
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CACHE_BYTES = 16L * 1024 * 1024

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // Generous read timeout: media segments on slow connections are slow,
            // not stuck.
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cache(Cache(File(context.cacheDir, "http_cache"), CACHE_BYTES))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor { chain ->
                        val request = chain.request()
                        Timber.v("%s %s", request.method, request.url)
                        chain.proceed(request)
                    }
                }
            }
            .build()
}
