package com.ampgames.vidsaver.ui.browser.web

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ampgames.vidsaver.data.browser.adblock.AdBlocker
import com.ampgames.vidsaver.data.browser.sniffer.MediaSniffer
import com.ampgames.vidsaver.domain.browser.UnsupportedDomains
import java.io.ByteArrayInputStream

/**
 * Wires ad blocking, media sniffing and the unsupported-domain block into the
 * WebView.
 *
 * [shouldInterceptRequest] runs on a WebView network thread, once per
 * subresource, so everything it does is non-blocking: a hash lookup for the
 * blocklist and a hand-off to [MediaSniffer], which does any real work on its
 * own coroutine.
 */
class VidSaverWebViewClient(
    private val adBlocker: AdBlocker,
    private val mediaSniffer: MediaSniffer,
    private val callbacks: Callbacks,
) : WebViewClient() {

    /**
     * Set by the view layer to read the finished page's source. Lives here
     * because only the client knows when a load actually completed, and only the
     * view layer can touch the WebView.
     */
    var onPageLoaded: ((WebView) -> Unit)? = null

    interface Callbacks {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String, title: String?)
        fun onProgressRelevantStateChanged(canGoBack: Boolean, canGoForward: Boolean)
        fun onUnsupportedDomainBlocked(url: String)
        fun isAdBlockEnabled(): Boolean
        fun currentPageUrl(): String
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url.toString()
        if (UnsupportedDomains.isUnsupported(url)) {
            callbacks.onUnsupportedDomainBlocked(url)
            return true // swallow the navigation
        }
        // Hand non-http schemes (mailto:, intent:, tel:) back to the system
        // rather than trying to render them.
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            return true
        }
        return false
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url.toString()

        // Policy block applies to subresources too, not just navigation.
        if (UnsupportedDomains.isUnsupported(url)) return BLOCKED_RESPONSE

        val pageUrl = callbacks.currentPageUrl()

        if (callbacks.isAdBlockEnabled() && adBlocker.shouldBlock(url, pageUrl)) {
            return BLOCKED_RESPONSE
        }

        if (request.isForMainFrame) return null

        mediaSniffer.onResourceRequested(url, request.requestHeaders.orEmpty())
        return null // let the WebView fetch it as normal
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        mediaSniffer.onNavigationStarted(url)
        callbacks.onPageStarted(url)
        callbacks.onProgressRelevantStateChanged(view.canGoBack(), view.canGoForward())
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        WebViewScripts.injectSniffer(view)
        callbacks.onPageFinished(url, view.title)
        callbacks.onProgressRelevantStateChanged(view.canGoBack(), view.canGoForward())
        onPageLoaded?.invoke(view)
    }

    private companion object {
        /**
         * An empty 200 rather than an error: pages handle a zero-length resource
         * far more gracefully than a failed request, which some sites retry in a
         * loop.
         */
        val BLOCKED_RESPONSE: WebResourceResponse
            get() = WebResourceResponse(
                "text/plain",
                "utf-8",
                ByteArrayInputStream(ByteArray(0)),
            )
    }
}
