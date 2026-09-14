package com.ampgames.vidsaver.ui.browser.web

import android.content.Context
import android.webkit.WebView
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/** Loads and injects `assets/js/video_sniffer.js`. */
object WebViewScripts {

    private const val ASSET_PATH = "js/video_sniffer.js"
    private val cached = AtomicReference<String?>(null)

    /** Reads the script once and keeps it; called off the main thread at startup. */
    fun preload(context: Context) {
        if (cached.get() != null) return
        val script = runCatching {
            context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrElse { error ->
            Timber.e(error, "Could not read %s; DOM sniffing disabled", ASSET_PATH)
            null
        }
        cached.compareAndSet(null, script)
    }

    /**
     * Runs the sniffer in [webView]. Safe to call repeatedly — the script
     * installs itself once and subsequent injections just trigger a rescan.
     */
    fun injectSniffer(webView: WebView) {
        val script = cached.get() ?: run {
            preload(webView.context.applicationContext)
            cached.get()
        } ?: return
        webView.evaluateJavascript(script, null)
    }
}
