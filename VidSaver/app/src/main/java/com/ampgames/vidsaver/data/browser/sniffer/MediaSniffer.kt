package com.ampgames.vidsaver.data.browser.sniffer

import android.webkit.CookieManager
import com.ampgames.vidsaver.core.net.Urls
import com.ampgames.vidsaver.di.ApplicationScope
import com.ampgames.vidsaver.di.IoDispatcher
import com.ampgames.vidsaver.domain.browser.UnsupportedDomains
import com.ampgames.vidsaver.domain.media.Activity
import com.ampgames.vidsaver.domain.media.MediaIdentity
import com.ampgames.vidsaver.domain.media.MediaTypes
import com.ampgames.vidsaver.domain.media.SniffSource
import com.ampgames.vidsaver.domain.media.SniffedMedia
import com.ampgames.vidsaver.domain.media.hls.M3u8Parser
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
    private val mediaProbeCount = AtomicInteger(0)

    /**
     * Re-emits the current list unchanged, for a collector that wants to
     * recompute over it — the caption changed, say, and every candidate's
     * name with it. A copy is a new object, so StateFlow does not swallow it.
     */
    fun touch() {
        _media.update { current -> current.map { it.copy() } }
    }

    /** One line per sighting, for the debug log. */
    fun describe(): String = _media.value.joinToString("\n") { m ->
        val where = Urls.host(m.url).orEmpty() + m.url.substringAfter(Urls.host(m.url).orEmpty(), "")
            .substringBefore('?').takeLast(40)
        buildString {
            append(m.source.name.padEnd(7)).append(' ')
            append(where)
            m.mimeType?.let { append("  mime=").append(it) }
            m.contentLength?.let { append("  size=").append(it) }
            m.height?.let { append("  h=").append(it) }
            if (m.activity != Activity.NONE) {
                append("  playing=").append(m.activity.isPlaying)
                append(" active=").append(m.activity.isActive)
                append(" visible=").append(m.activity.visibleFraction)
                m.activity.debug?.let { append("  ").append(it) }
            }
            m.hlsVariantOf?.let { append("  variantOf=").append(Urls.host(it)) }
            if (m.audioOnly) append("  audioOnly")
        }
    }.ifBlank { "(nothing sniffed on this page)" }

    /** Clears state for a new page. Called when navigation commits. */
    fun onNavigationStarted(url: String) {
        pageUrl = url
        seen.clear()
        probeCount.set(0)
        mediaProbeCount.set(0)
        _media.value = emptyList()
    }

    /**
     * Called from the WebView's network thread for every subresource. Must be
     * fast and must not block: the actual inspection happens on a coroutine.
     */
    fun onResourceRequested(url: String, requestHeaders: Map<String, String>, method: String = "GET") {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) return
        if (UnsupportedDomains.isUnsupported(url)) return
        if (MediaTypes.isSegmentUrl(url)) return
        // Video is fetched with GET; a POST is an API call, never a file.
        if (!method.equals("GET", ignoreCase = true)) return
        if (isApiCall(url, requestHeaders)) return
        // The browser says what it is fetching. An image, script, style sheet
        // or document is never a video, and must not be re-requested by the
        // probe: a second hit on a signed image URL is a way to make the
        // page's own copy fail.
        val destination = header(requestHeaders, "Sec-Fetch-Dest")?.lowercase()
        if (destination != null && destination in NON_MEDIA_DESTINATIONS) return
        val accept = header(requestHeaders, "Accept")?.lowercase()
        if (accept != null && (accept.startsWith("image/") || accept.startsWith("text/"))) return

        // Keyed by content identity: a feed requests the same video dozens of
        // times in byte ranges, and probing each one would mean dozens of extra
        // requests for a single clip.
        val canonical = MediaIdentity.canonicalUrl(url)
        val hasRange = requestHeaders.keys.any { it.equals("Range", ignoreCase = true) }
        if (!seen.add(MediaIdentity.contentKey(canonical))) {
            // Known already. A further byte-range request means it is still
            // streaming: on a site whose <video> hides behind a blob: URL,
            // "still streaming" is the only sign of which clip is on screen.
            if (hasRange) bumpRecency(canonical)
            return
        }

        val headers = buildHeaders(canonical, requestHeaders)
        // A byte-range request is a media request whatever the URL looks like:
        // players fetch video in ranges and nothing else does. Sites that
        // serve video from extension-less URLs (TikTok, Instagram) are only
        // ever caught this way.
        val looksLikeMedia = MediaTypes.looksLikeMediaUrl(canonical) || hasRange ||
            destination == "video" || destination == "audio" ||
            hintsAtVideo(canonical)

        if (looksLikeMedia) {
            // Offer it straight away; the probe below only adds the size.
            record(
                SniffedMedia(
                    url = canonical,
                    pageUrl = pageUrl,
                    mimeType = null, // inferred from the URL by MediaTypes
                    headers = headers,
                    source = SniffSource.NETWORK,
                    detectedAt = System.currentTimeMillis(),
                ),
            )
        }

        if (shouldProbe(canonical, looksLikeMedia)) {
            scope.launch { probeContentType(canonical, headers, alreadyRecorded = looksLikeMedia) }
        }
    }

    /** Marks a known file as just requested again, without adding anything else. */
    private fun bumpRecency(canonicalUrl: String) {
        val key = MediaIdentity.contentKey(canonicalUrl)
        val now = System.currentTimeMillis()
        _media.update { current ->
            val index = current.indexOfFirst {
                it.source == SniffSource.NETWORK && MediaIdentity.contentKey(it.url) == key
            }
            if (index < 0 || now - current[index].detectedAt < RECENCY_BUMP_MS) return@update current
            current.toMutableList().also { list -> list[index] = list[index].copy(detectedAt = now) }
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

            val canonical = MediaIdentity.canonicalUrl(absolute)
            val headers = buildHeaders(canonical, buildMap { userAgent?.let { put("User-Agent", it) } })
            record(
                SniffedMedia(
                    url = canonical,
                    pageUrl = currentPage,
                    mimeType = report.mimeType,
                    headers = headers,
                    width = report.width,
                    height = report.height,
                    posterUrl = report.poster,
                    title = report.title,
                    source = SniffSource.DOM,
                    detectedAt = System.currentTimeMillis(),
                    activity = Activity(
                        isPlaying = report.playing,
                        isActive = report.active,
                        visibleFraction = report.visible?.coerceIn(0.0, 1.0) ?: 0.0,
                        debug = report.state?.take(120),
                    ),
                ),
            )

            // A <video src> the network layer never saw (set before our client
            // attached, or served from cache) still needs its size.
            if (seen.add(MediaIdentity.contentKey(canonical)) && shouldProbe(canonical, looksLikeMedia = true)) {
                scope.launch { probeContentType(canonical, headers, alreadyRecorded = true) }
            }
        }
    }

    /**
     * A one-byte ranged GET to learn what a URL really is. For an ambiguous URL
     * this decides whether it is media at all; for one that already looked like
     * media it fills in the size, which is what lets a person tell a 720p file
     * from a 1080p one when the server names neither.
     */
    private suspend fun probeContentType(
        url: String,
        headers: Map<String, String>,
        alreadyRecorded: Boolean,
    ) {
        (if (alreadyRecorded) mediaProbeCount else probeCount).incrementAndGet()
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
                    val isPlaylist = MediaTypes.isHlsMime(contentType) || MediaTypes.isHlsUrl(url)
                    // A playlist's byte size is the size of a text file; it
                    // says nothing about the stream and must not be shown.
                    val length = if (isPlaylist) {
                        null
                    } else {
                        totalLengthFrom(response.header("Content-Range"))
                            ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 1 }
                    }
                    val isVideo = MediaTypes.isVideoMime(contentType) || isPlaylist
                    if (!isVideo && !alreadyRecorded) return@use

                    record(
                        SniffedMedia(
                            url = url,
                            pageUrl = pageUrl,
                            mimeType = contentType.takeIf { isVideo },
                            headers = headers,
                            contentLength = length,
                            source = SniffSource.NETWORK,
                            detectedAt = System.currentTimeMillis(),
                        ),
                    )

                    if (isPlaylist) expandMasterPlaylist(url, headers)
                }
            }.onFailure { Timber.v(it, "Probe failed for %s", url) }
        }
    }

    /**
     * Reads a playlist and, when it is a master, records each variant as its
     * own sighting with the quality the master declares for it. The sheet then
     * shows "1080P / 720P / 480P" instead of six identical "HLS" chips, and the
     * master itself drops out as a duplicate menu.
     *
     * Playlists are small text files; the read is capped all the same.
     */
    private fun expandMasterPlaylist(url: String, headers: Map<String, String>) {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        val content = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            response.body?.source()?.use { source ->
                source.request(MAX_PLAYLIST_BYTES)
                source.buffer.readUtf8(minOf(source.buffer.size, MAX_PLAYLIST_BYTES))
            } ?: return
        }
        if (!M3u8Parser.isMasterPlaylist(content)) return

        val now = System.currentTimeMillis()
        M3u8Parser.parseMaster(content, url).forEach { variant ->
            record(
                SniffedMedia(
                    url = MediaIdentity.canonicalUrl(variant.url),
                    pageUrl = pageUrl,
                    mimeType = "application/vnd.apple.mpegurl",
                    headers = headers,
                    width = variant.width,
                    height = variant.height,
                    source = SniffSource.NETWORK,
                    detectedAt = now,
                    hlsVariantOf = url,
                    audioOnly = variant.height == null && isAudioCodecOnly(variant.codecs),
                ),
            )
        }
    }

    /**
     * Requests that are plainly a site's API, not a file: they used to spend
     * the probe budget before the page's first video request arrived, which
     * is why a feed sometimes showed nothing to download.
     */
    private fun header(headers: Map<String, String>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun isApiCall(url: String, requestHeaders: Map<String, String>): Boolean {
        val accept = header(requestHeaders, "Accept")
        if (accept != null && accept.startsWith("application/json", ignoreCase = true)) return true
        val path = "/" + url.substringAfter("://").substringAfter('/', "").substringBefore('?').lowercase()
        return API_PATH_MARKERS.any { path.contains(it) }
    }

    /** Extension-less URLs that a site still labels as video in the query or path. */
    private fun hintsAtVideo(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("mime_type=video") || lower.contains("/video/tos/") ||
            lower.contains("/video/") && lower.contains("&br=")
    }

    /** True when a CODECS attribute names no video codec at all. */
    private fun isAudioCodecOnly(codecs: String?): Boolean {
        val c = codecs?.lowercase() ?: return false
        return VIDEO_CODEC_PREFIXES.none { c.contains(it) }
    }

    private fun record(media: SniffedMedia) {
        if (media.pageUrl.isNotEmpty() && media.pageUrl != pageUrl) return // page moved on

        val key = MediaIdentity.contentKey(media.url)
        _media.update { current ->
            val index = current.indexOfFirst {
                MediaIdentity.contentKey(it.url) == key && it.source == media.source
            }
            when {
                // Seen before: keep the row, absorb whatever this sighting adds.
                index >= 0 -> current.toMutableList().also { list ->
                    list[index] = list[index].enrichedWith(media)
                }
                // A page that keeps loading video must not grow this list without
                // bound; the extractor caps what is shown, but the sniffer holds
                // the raw observations.
                current.size >= MAX_TRACKED_MEDIA -> current
                else -> {
                    Timber.d("Media found (%s): %s", media.source, media.url)
                    current + media
                }
            }
        }
    }

    /**
     * Only probe things that could plausibly be media. Static assets are skipped
     * by extension, and the count is capped so a page with hundreds of XHRs
     * cannot turn into hundreds of extra requests.
     */
    private fun shouldProbe(url: String, looksLikeMedia: Boolean): Boolean {
        // Files that already look like media have their own budget: a social
        // page fires hundreds of API calls before its first video, and those
        // must not spend the budget the video's size needs.
        if (looksLikeMedia) return mediaProbeCount.get() < MAX_MEDIA_PROBES_PER_PAGE
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
        const val MAX_PROBES_PER_PAGE = 40
        const val MAX_MEDIA_PROBES_PER_PAGE = 40
        const val MAX_PLAYLIST_BYTES = 512L * 1024

        /** Bump at most this often per file; a player asks for ranges constantly. */
        const val RECENCY_BUMP_MS = 1_500L

        val NON_MEDIA_DESTINATIONS = setOf(
            "image", "script", "style", "font", "document", "iframe", "frame", "manifest",
            "report", "worker", "sharedworker", "serviceworker", "paintworklet", "audioworklet",
            "track", "xslt", "embed", "object",
        )

        val API_PATH_MARKERS = listOf(
            "/api/", "/graphql", "/gql", "/ajax/", "/log/", "/logs/", "/collect", "/report",
            "/analytics", "/track", "/pixel", "/beacon", "/ping", "/telemetry", "/metrics",
            "/monitor", "/stats", "/tr/", "/rtb/", "/ads/", "/sdk/", "/webmssdk", "/service/",
        )

        val VIDEO_CODEC_PREFIXES = listOf("avc1", "avc3", "hev1", "hvc1", "vp09", "vp8", "vp9", "av01", "dvh1", "dvhe")
        const val MAX_TRACKED_MEDIA = 60

        val FORWARDED_HEADERS = setOf("referer", "user-agent", "origin", "cookie")

        val NON_MEDIA_EXTENSIONS = setOf(
            "css", "js", "mjs", "json", "png", "jpg", "jpeg", "gif", "webp", "svg",
            "ico", "woff", "woff2", "ttf", "otf", "eot", "html", "htm", "xml", "txt",
            "map", "wasm", "pdf",
        )
    }
}
