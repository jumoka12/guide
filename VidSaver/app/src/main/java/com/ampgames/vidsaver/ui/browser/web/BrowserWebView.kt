package com.ampgames.vidsaver.ui.browser.web

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.ampgames.vidsaver.data.browser.sniffer.VideoSnifferBridge
import com.ampgames.vidsaver.ui.browser.BrowserCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber

const val BROWSER_WEBVIEW_TEST_TAG = "browser_webview"

/**
 * Hosts the single WebView the browser uses.
 *
 * Tabs share this one instance and swap state through
 * [WebView.saveState]/[WebView.restoreState] rather than each keeping a live
 * WebView: a WebView costs tens of megabytes, and ten background tabs holding
 * ten of them is the fastest way to get a browser killed on a mid-range device.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    commands: Flow<BrowserCommand>,
    client: VidSaverWebViewClient,
    bridge: VideoSnifferBridge,
    desktopMode: Boolean,
    onProgressChanged: (Int) -> Unit,
    onTitleChanged: (String?) -> Unit,
    onNavigationStateChanged: (Boolean, Boolean) -> Unit,
    onUserAgentResolved: (String) -> Unit,
    onPageHtmlCaptured: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val holder = remember { WebViewHolder() }

    // Capture page source once each load finishes, for the extractors.
    LaunchedEffect(client) {
        client.onPageLoaded = { webView ->
            webView.evaluateJavascript(HTML_CAPTURE_JS) { encoded ->
                runCatching { Json.decodeFromString(String.serializer(), encoded) }
                    .onSuccess { html -> if (html.isNotBlank()) onPageHtmlCaptured(html) }
                    .onFailure { Timber.v("Could not decode captured page HTML") }
            }
        }
    }

    AndroidView(
        modifier = modifier.testTag(BROWSER_WEBVIEW_TEST_TAG),
        factory = { context ->
            WebView(context).apply {
                WebViewConfig.apply(this)
                holder.defaultUserAgent = settings.userAgentString
                onUserAgentResolved(settings.userAgentString)

                webViewClient = client
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        onProgressChanged(newProgress)
                        onNavigationStateChanged(view.canGoBack(), view.canGoForward())
                    }

                    override fun onReceivedTitle(view: WebView, title: String?) {
                        onTitleChanged(title)
                    }
                }
                addJavascriptInterface(bridge, VideoSnifferBridge.INTERFACE_NAME)
                WebViewScripts.preload(context.applicationContext)
                holder.webView = this
            }
        },
        update = { webView ->
            holder.webView = webView
            WebViewConfig.setDesktopMode(webView, desktopMode, holder.defaultUserAgent)
        },
        onRelease = { webView ->
            client.onPageLoaded = null
            webView.stopLoading()
            webView.removeJavascriptInterface(VideoSnifferBridge.INTERFACE_NAME)
            webView.destroy()
            holder.webView = null
        },
    )

    LaunchedEffect(commands) {
        commands.collect { command ->
            val webView = holder.webView ?: return@collect
            when (command) {
                is BrowserCommand.LoadUrl -> webView.loadUrl(command.url)
                BrowserCommand.Reload -> webView.reload()
                BrowserCommand.GoBack -> if (webView.canGoBack()) webView.goBack()
                BrowserCommand.GoForward -> if (webView.canGoForward()) webView.goForward()
                BrowserCommand.StopLoading -> webView.stopLoading()

                is BrowserCommand.SetDesktopMode -> WebViewConfig.setDesktopMode(
                    webView,
                    command.enabled,
                    holder.defaultUserAgent,
                )

                is BrowserCommand.SwitchTab -> holder.switchTab(webView, command.tab.id, command.tab.url)
            }
        }
    }
}

/**
 * Mutable WebView state that outlives recomposition: the live WebView, the
 * factory-default User-Agent, and each tab's saved navigation state.
 */
private class WebViewHolder {
    var webView: WebView? = null
    var defaultUserAgent: String = ""

    private val tabStates = mutableMapOf<String, Bundle>()
    private var currentTabId: String? = null

    fun switchTab(webView: WebView, tabId: String, url: String) {
        if (tabId == currentTabId) return

        currentTabId?.let { previous ->
            tabStates[previous] = Bundle().also { webView.saveState(it) }
        }
        currentTabId = tabId

        val saved = tabStates[tabId]
        when {
            saved != null -> webView.restoreState(saved)
            url.isNotBlank() -> webView.loadUrl(url)
            else -> webView.loadUrl(BLANK_PAGE)
        }
    }

    private companion object {
        const val BLANK_PAGE = "about:blank"
    }
}

private const val HTML_CAPTURE_JS =
    "(function(){try{return document.documentElement.outerHTML}catch(e){return ''}})()"
