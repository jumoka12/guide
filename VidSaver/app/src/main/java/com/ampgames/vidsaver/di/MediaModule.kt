package com.ampgames.vidsaver.di

import androidx.media3.common.util.UnstableApi
import com.ampgames.vidsaver.data.download.DirectFileDownloadStrategy
import com.ampgames.vidsaver.data.download.DownloadStrategy
import com.ampgames.vidsaver.data.download.HlsDownloadStrategy
import com.ampgames.vidsaver.data.download.Media3Remuxer
import com.ampgames.vidsaver.data.download.Remuxer
import com.ampgames.vidsaver.domain.media.extractor.FacebookExtractor
import com.ampgames.vidsaver.domain.media.extractor.InstagramExtractor
import com.ampgames.vidsaver.domain.media.extractor.PinterestExtractor
import com.ampgames.vidsaver.domain.media.extractor.SiteExtractor
import com.ampgames.vidsaver.domain.media.extractor.TikTokExtractor
import com.ampgames.vidsaver.domain.media.extractor.TwitterExtractor
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Site extractors are contributed as a set, so adding a new one is a single
 * `@Binds @IntoSet` line here plus the class itself — see README, "Adding a new
 * SiteExtractor". [com.ampgames.vidsaver.domain.media.extractor.ExtractorRegistry]
 * orders them and handles the blocker and the generic fallback positionally.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {

    @Binds
    @IntoSet
    abstract fun bindTikTok(extractor: TikTokExtractor): SiteExtractor

    @Binds
    @IntoSet
    abstract fun bindInstagram(extractor: InstagramExtractor): SiteExtractor

    @Binds
    @IntoSet
    abstract fun bindTwitter(extractor: TwitterExtractor): SiteExtractor

    @Binds
    @IntoSet
    abstract fun bindFacebook(extractor: FacebookExtractor): SiteExtractor

    @Binds
    @IntoSet
    abstract fun bindPinterest(extractor: PinterestExtractor): SiteExtractor
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {

    @Binds
    @IntoSet
    abstract fun bindDirect(strategy: DirectFileDownloadStrategy): DownloadStrategy

    @Binds
    @IntoSet
    abstract fun bindHls(strategy: HlsDownloadStrategy): DownloadStrategy

    @Binds
    @Singleton
    @UnstableApi
    abstract fun bindRemuxer(remuxer: Media3Remuxer): Remuxer
}
