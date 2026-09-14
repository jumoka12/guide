package com.ampgames.vidsaver.ui.browser.web

import android.annotation.SuppressLint
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * WebView hardening and configuration.
 *
 * The permissive settings here are the ones a browser genuinely needs —
 * JavaScript and DOM storage, without which most sites render nothing. The
 * restrictive ones close off everything a browser does not need: no file or
 * content access, no automatic file-scheme cookies, and Safe Browsing on.
 */
object WebViewConfig {

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36"

    /**
     * The WebView's own User-Agent minus the `; wv` marker that flags it as an
     * embedded view. Large sites serve a stripped-down or broken page to that
     * marker — Facebook among them — while the same engine identified as
     * mobile Chrome gets the page every phone browser gets.
     */
    fun mobileUserAgent(webView: WebView): String =
        webView.settings.userAgentString
            .replace("; wv", "")
            .replace(Regex("""\s*Version/\d+(\.\d+)*"""), "")

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(webView: WebView) {
        with(webView.settings) {
            // Required for a usable browser.
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            // Social feeds autoplay muted and paint nothing until playback
            // starts; requiring a gesture leaves the player a black box. The
            // sniffer also only sees a video once the page actually loads it.
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = mobileUserAgent(webView)

            // Attack surface we have no use for.
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, true)
        }

        // The app theme is dark, and a dark host theme invites the WebView to
        // recolour pages ("force dark"). Pages pick their own colours; a
        // recoloured Facebook is a black page with black text.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDark(webView.settings, WebSettingsCompat.FORCE_DARK_OFF)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Third-party cookies on: video CDNs (fbcdn.net, dmcdn.net, the
            // TikTok edge hosts) sit on a different site from the page and
            // some refuse a segment request that arrives without the session
            // cookie the page set. The tracking cost is carried by the ad
            // blocker, which blocks the trackers themselves.
            setAcceptThirdPartyCookies(webView, true)
        }

        // Video frames are composited only on a hardware layer; on a software
        // layer the page paints and the <video> stays a black box.
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
    }

    /** Switches between the WebView's own mobile UA and a desktop UA. */
    fun setDesktopMode(webView: WebView, desktop: Boolean, defaultUserAgent: String) {
        val target = if (desktop) DESKTOP_USER_AGENT else defaultUserAgent
        // Only touch the setting when it changes: this runs on every
        // recomposition, and rewriting the UA is not free.
        if (webView.settings.userAgentString != target) {
            webView.settings.userAgentString = target
            webView.settings.useWideViewPort = desktop
            webView.settings.loadWithOverviewMode = desktop
        }
    }

    fun setThirdPartyCookiesAllowed(webView: WebView, allowed: Boolean) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, allowed)
    }
}
