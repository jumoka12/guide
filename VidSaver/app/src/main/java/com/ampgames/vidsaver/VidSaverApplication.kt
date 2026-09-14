package com.ampgames.vidsaver

import android.app.Application
import com.ampgames.vidsaver.core.logging.ReleaseTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application entry point.
 *
 * Startup is deliberately crash-safe: every initialisation step is isolated so a
 * failure in one subsystem (config, logging, and later the ad/IAP SDKs) cannot
 * prevent the app from opening.
 */
@HiltAndroidApp
class VidSaverApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initLogging()
        installUncaughtExceptionLogger()
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
     * Logs crashes before handing them to the platform handler, so the stack
     * trace is captured even before Crashlytics is wired up in Phase 7. The
     * previous handler is always invoked — we never swallow a crash.
     */
    private fun installUncaughtExceptionLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { Timber.e(throwable, "Uncaught exception on %s", thread.name) }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
