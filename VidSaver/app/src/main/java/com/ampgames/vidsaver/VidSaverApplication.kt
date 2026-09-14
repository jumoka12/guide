package com.ampgames.vidsaver

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.ampgames.vidsaver.core.logging.CrashRecord
import com.ampgames.vidsaver.data.billing.PaywallCoordinator
import com.ampgames.vidsaver.data.billing.PremiumRepository
import com.ampgames.vidsaver.data.billing.RevenueCatPremiumRepository
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.ampgames.vidsaver.core.logging.ReleaseTree
import com.ampgames.vidsaver.data.download.DownloadNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider
import timber.log.Timber

/**
 * Application entry point.
 *
 * Startup is deliberately crash-safe: every initialisation step is isolated so a
 * failure in one subsystem (config, logging, and later the ad/IAP SDKs) cannot
 * prevent the app from opening.
 */
@HiltAndroidApp
class VidSaverApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    /**
     * Provider, not a direct injection: WorkManager is only needed when a
     * download retry is scheduled, and building the factory eagerly would drag
     * the whole graph into cold start.
     */
    @Inject
    lateinit var workerFactory: Provider<HiltWorkerFactory>

    @Inject
    lateinit var notifications: Provider<DownloadNotifications>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory.get())
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN)
            .build()

    /**
     * Coil does not register the video decoder by default, and gallery
     * thumbnails are frames pulled out of the saved files.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()

    @Inject
    lateinit var premiumRepository: Provider<PremiumRepository>

    @Inject
    lateinit var paywallCoordinator: Provider<PaywallCoordinator>

    override fun onCreate() {
        super.onCreate()
        initLogging()
        installUncaughtExceptionLogger()
        initNotificationChannels()
        initBilling()
    }

    /**
     * Configures RevenueCat when a key is present and starts watching the
     * process lifecycle for paywall triggers. Isolated like every other step:
     * a store SDK that fails to configure leaves the app free, not dead.
     */
    private fun initBilling() {
        runCatching {
            val key = BuildConfig.REVENUECAT_KEY
            if (key.isNotBlank()) {
                Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
                Purchases.configure(PurchasesConfiguration.Builder(this, key).build())
                (premiumRepository.get() as? RevenueCatPremiumRepository)?.start()
            } else {
                Timber.i("REVENUECAT_KEY is empty; running as free with no store")
            }
            paywallCoordinator.get().start()
        }.onFailure { Timber.e(it, "Billing could not be initialised") }
    }

    private fun initLogging() {
        runCatching {
            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
            } else {
                Timber.plant(ReleaseTree())
            }
        }
    }

    /**
     * Channels must exist before any notification is posted, and creating them
     * is cheap and idempotent, so it happens at startup rather than at the first
     * download.
     */
    private fun initNotificationChannels() {
        runCatching { notifications.get().ensureChannels() }
            .onFailure { Timber.e(it, "Could not create notification channels") }
    }

    /**
     * Logs crashes before handing them to the platform handler, so the stack
     * trace is captured even before Crashlytics is wired up in Phase 7. The
     * previous handler is always invoked — we never swallow a crash.
     */
    private fun installUncaughtExceptionLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { Timber.e(throwable, "Uncaught exception on %s", thread.name) }
            // The process dies with its log; the file survives for the next
            // debug-log share.
            runCatching { CrashRecord.write(this, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
