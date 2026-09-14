package com.ampgames.vidsaver.di

import com.ampgames.vidsaver.BuildConfig
import com.ampgames.vidsaver.data.billing.NoBillingPremiumRepository
import com.ampgames.vidsaver.data.billing.PremiumRepository
import com.ampgames.vidsaver.data.billing.RevenueCatPremiumRepository
import com.ampgames.vidsaver.data.config.AppConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    /**
     * The real repository only when a key is present. A key is a placeholder
     * from `local.properties`; a build without one runs as free and says so on
     * the paywall instead of crashing in the SDK.
     */
    @Provides
    @Singleton
    fun providePremiumRepository(
        appConfig: AppConfig,
        @ApplicationScope scope: CoroutineScope,
    ): PremiumRepository =
        if (BuildConfig.REVENUECAT_KEY.isBlank()) {
            NoBillingPremiumRepository()
        } else {
            RevenueCatPremiumRepository(appConfig, scope)
        }
}
