package com.ampgames.vidsaver.di

import android.content.Context
import androidx.room.Room
import com.ampgames.vidsaver.data.browser.db.AllowlistDao
import com.ampgames.vidsaver.data.browser.db.BookmarkDao
import com.ampgames.vidsaver.data.browser.db.HistoryDao
import com.ampgames.vidsaver.data.browser.db.VidSaverDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VidSaverDatabase =
        Room.databaseBuilder(context, VidSaverDatabase::class.java, VidSaverDatabase.NAME)
            .build()

    @Provides
    fun provideBookmarkDao(database: VidSaverDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideHistoryDao(database: VidSaverDatabase): HistoryDao = database.historyDao()

    @Provides
    fun provideAllowlistDao(database: VidSaverDatabase): AllowlistDao = database.allowlistDao()
}
