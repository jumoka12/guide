package com.ampgames.vidsaver.data.browser.sniffer

import android.webkit.CookieManager
import com.ampgames.vidsaver.core.net.Urls
import com.ampgames.vidsaver.di.ApplicationScope
import com.ampgames.vidsaver.di.IoDispatcher
import com.ampgames.vidsaver.domain.browser.UnsupportedDomains
import com.ampgames.vidsaver.domain.media.MediaTypes
import com.ampgames.vidsaver.domain.media.SniffSource
import com.ampgames.vidsaver.domain.media.SniffedMedia
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Collects the media a page delivers, from two sources that complement each
 * other:
 *
 * 1. **Network** — every subresource the page requests passes through
 *    `shouldInterceptRequest`. A URL that already looks like media is recorded
 *    straight away; anything ambiguous gets a cheap one-byte ranged GET on a
 *    background coroutine to read its real `Content-Type`, because
 *    `shouldInterceptRequest` sees the request but never the response headers.
 * 2. **DOM** — the injected page script reports `<video>`, `<source>` and
 *    `og:video` it can see, which catches media that was already loaded or that
 *    the page describes without fetching.
 *
 * Nothing here requests anything the page did not itself request; the probe
 * replays the page's own request with the page's own headers.
 */
@Singleton
class MediaSniffer @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _media = MutableStateFlow<List<SniffedMedia>>(emptyList())
    val media: StateFlow<List<SniffedMedia>> = _media.asStateFlow()

    @Volatile
    private var pageUrl: String = ""

    /** The WebView's User-Agent, set once the WebView exists. */
    @Volatile
    var defaultUserAgent: String? = null

    /** URLs already handled for the current page, so each is probed at most once. */
    private val seen = ConcurrentHashMap.newKeySet<String>()
    private val probeCount = AtomicInteger(0)

    /** Clears state for a new page. Called when navigation commits. */
    fun onNavigationStarted(url: String) {
        pageUrl = url
        seen.clear()
        probeCount.set(0)
        _media.value = emptyList()
    }

    /**
     * Called from the WebView's network thread for every subresource. Must be
     * fast and must not block: the actual inspection happens on a coroutine.
     */
    fun onResourceRequested(url: String, requestHeaders: Map<String, String>) {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) return
        if (UnsupportedDomains.isUnsupported(url)) return
        if (MediaTypes.isSegmentUrl(url)) return
        if (!seen.add(url)) return

        val headers = buildHeaders(url, requestHeaders)

        if (MediaTypes.looksLikeMediaUrl(url)) {
            record(
                SniffedMedia(
                    url = url,
                    pageUrl = pageUrl,
                    mimeType = null, // inferred from the URL by MediaTypes
                    headers = headers,
                    source = SniffSource.NETWORK,
                    detectedAt = System.currentTimeMillis(),
                ),
            )
            return
        }

        if (shouldProbe(url)) {
            scope.launch { probeContentType(url, headers) }
        }
    }

    /** Media reported by the injected page script. */
    fun onDomMediaFound(reports: List<DomMediaReport>) {
        val currentPage = pageUrl
        val userAgent = defaultUserAgent
        reports.forEach { report ->
            val absolute = report.absoluteUrl(currentPage) ?: return@forEach
            if (UnsupportedDomains.isUnsupported(absolute)) return@forEach
            if (MediaTypes.typeOf(absolute, report.mimeType) == null) return@forEach

            record(
                SniffedMedia(
                    url = absolute,
                    pageUrl = currentPage,
                    mimeType = report.mimeType,
                    headers = buildHeaders(absolute, buildMap { userAgent?.let { put("User-Agent", it) } }),
                    width = report.width,
                    height = report.height,
                    posterUrl = report.poster,
                    title = report.title,
                    source = SniffSource.DOM,
                    detectedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun probeContentType(url: String, headers: Map<String, String>) {
        probeCount.incrementAndGet()
        withContext(ioDispatcher) {
            runCatching {
                // One byte is enough to read the headers, and servers that reject
                // HEAD generally honour a ranged GET.
                val request = Request.Builder()
                    .url(url)
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .header("Range", "bytes=0-0")
                    .build()

                client.newCall(request).execute().use { response ->
                    val contentType = response.header("Content-Type")
                    if (!MediaTypes.isVideoMime(contentType)) return@use

                    record(
                        SniffedMedia(
                            url = url,
                            pageUrl = pageUrl,
                            mimeType = contentType,
                            headers = headers,
                            contentLength = totalLengthFrom(response.header("Content-Range")),
                            source = SniffSource.NETWORK,
                            detectedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }.onFailure { Timber.v(it, "Probe failed for %s", url) }
        }
    }

    private fun record(media: SniffedMedia) {
        if (media.pageUrl.isNotEmpty() && media.pageUrl != pageUrl) return // page moved on
        _media.update { current ->
            if (current.any { it.url == media.url && it.source == media.source }) {
                current
            } else {
                current + media
            }
        }
    }

    /**
     * Only probe things that could plausibly be media. Static assets are skipped
     * by extension, and the count is capped so a page with hundreds of XHRs
     * cannot turn into hundreds of extra requests.
     */
    private fun shouldProbe(url: String): Boolean {
        if (probeCount.get() >= MAX_PROBES_PER_PAGE) return false
        val extension = Urls.fileExtension(url)
        if (extension != null && extension in NON_MEDIA_EXTENSIONS) return false
        return true
    }

    private fun buildHeaders(url: String, requestHeaders: Map<String, String>): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        requestHeaders.forEach { (key, value) ->
            if (key.lowercase() in FORWARDED_HEADERS && value.isNotBlank()) headers[key] = value
        }
        if (headers.keys.none { it.equals("Referer", ignoreCase = true) } && pageUrl.isNotBlank()) {
            headers["Referer"] = pageUrl
        }
        // WebView strips Cookie from requestHeaders; re-read what it would send.
        runCatching { CookieManager.getInstance().getCookie(url) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { headers["Cookie"] = it }
        return headers
    }

    /** Total size from a `Content-Range: bytes 0-0/12345` header. */
    private fun totalLengthFrom(contentRange: String?): Long? =
        contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()?.takeIf { it > 0 }

    private companion object {
        const val MAX_PROBES_PER_PAGE = 24

        val FORWARDED_HEADERS = setOf("referer", "user-agent", "origin", "cookie")

        val NON_MEDIA_EXTENSIONS = setOf(
            "css", "js", "mjs", "json", "png", "jpg", "jpeg", "gif", "webp", "svg",
            "ico", "woff", "woff2", "ttf", "otf", "eot", "html", "htm", "xml", "txt",
            "map", "wasm", "pdf",
        )
    }
}
