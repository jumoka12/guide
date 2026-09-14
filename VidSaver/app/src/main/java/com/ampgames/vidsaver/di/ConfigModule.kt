package com.ampgames.vidsaver.di

import android.content.Context
import com.ampgames.vidsaver.data.config.AppConfig
import com.ampgames.vidsaver.data.config.AppConfigLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConfigModule {

    @Provides
    @Singleton
    fun provideAppConfigLoader(@ApplicationContext context: Context): AppConfigLoader =
        AppConfigLoader(context)

    @Provides
    @Singleton
    fun provideAppConfig(loader: AppConfigLoader): AppConfig = loader.load()
}
