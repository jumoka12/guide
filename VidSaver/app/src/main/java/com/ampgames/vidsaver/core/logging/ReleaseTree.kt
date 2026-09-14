package com.ampgames.vidsaver.core.logging

import android.util.Log
import timber.log.Timber

/**
 * Release logging tree: drops verbose/debug/info entirely and keeps warnings and
 * errors only. Crashlytics forwarding is wired in Phase 7 — the hook lives here
 * so no other code needs to change.
 */
class ReleaseTree : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!isLoggable(tag, priority)) return
        Log.println(priority, tag ?: DEFAULT_TAG, message)
        // Phase 7: FirebaseCrashlytics.getInstance().log(message) / recordException(t)
    }

    private companion object {
        const val DEFAULT_TAG = "VidSaver"
    }
}
