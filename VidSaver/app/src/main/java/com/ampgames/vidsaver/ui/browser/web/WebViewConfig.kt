package com.ampgames.vidsaver.ui.browser.web

import android.annotation.SuppressLint
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

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // First-party cookies keep logins working; third-party cookies are
            // off by default, which also removes a chunk of cross-site tracking.
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
    }

    /** Switches between the WebView's own mobile UA and a desktop UA. */
    fun setDesktopMode(webView: WebView, desktop: Boolean, defaultUserAgent: String) {
        webView.settings.userAgentString = if (desktop) DESKTOP_USER_AGENT else defaultUserAgent
        webView.settings.useWideViewPort = desktop
        webView.settings.loadWithOverviewMode = desktop
    }

    fun setThirdPartyCookiesAllowed(webView: WebView, allowed: Boolean) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, allowed)
    }
}
