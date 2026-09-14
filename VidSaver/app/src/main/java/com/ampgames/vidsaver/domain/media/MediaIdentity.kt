package com.ampgames.vidsaver.domain.media

import com.ampgames.vidsaver.core.net.Urls

/**
 * Decides when two observed URLs are the same video.
 *
 * Without this, a Facebook or Instagram feed produces dozens of "downloads" for
 * a handful of actual clips. Those sites stream progressive MP4 in byte ranges —
 * `…/video.mp4?bytestart=0&byteend=261771` — so every chunk looks like its own
 * file to anything that only checks the extension. They also re-sign the same
 * asset on every request, so two URLs for one video rarely match literally.
 *
 * Two jobs, both pure and both tested:
 * - [canonicalUrl]: strip the range so one whole-file request replaces the chunks.
 * - [contentKey]: a stable identity for de-duplication, ignoring the volatile
 *   signature and cache-busting parameters CDNs attach.
 */
object MediaIdentity {

    /**
     * Query parameters that carve a file into pieces. A URL carrying any of
     * these is a slice of something bigger, not a download in its own right.
     */
    val RANGE_PARAMS = setOf("bytestart", "byteend", "range", "rn", "rbuf", "sq", "segment")

    /**
     * Parameters that change on every request without changing the bytes:
     * expiring signatures, cache-busters and CDN routing hints. Two URLs that
     * differ only in these point at the same video.
     */
    private val VOLATILE_PARAMS = setOf(
        // Meta / Facebook / Instagram CDN
        "_nc_cat", "_nc_sid", "_nc_ohc", "_nc_ht", "_nc_gid", "_nc_hash", "_nc_rid",
        "ccb", "efg", "oh", "oe", "_nc_log", "_nc_zt",
        // Generic signing and expiry
        "token", "expires", "expire", "signature", "sig", "hmac", "key", "policy",
        "st", "et", "ei", "ip", "ipbits", "mt", "mv", "ms", "mm", "nonce",
        // Cache busting
        "cb", "cachebust", "_", "t", "ts", "v", "rand",
    )

    /** True when [url] is one slice of a larger file rather than the whole thing. */
    fun isRangedRequest(url: String): Boolean = Urls.hasQueryParam(url, RANGE_PARAMS)

    /**
     * The URL to actually download.
     *
     * Range parameters are dropped: requesting the same asset without them
     * normally returns the complete file, and [com.ampgames.vidsaver.data.download.DirectFileDownloadStrategy]
     * adds its own `Range` header when resuming. Signing parameters are kept —
     * strip those and the CDN returns 403.
     */
    fun canonicalUrl(url: String): String = Urls.withoutQueryParams(url, RANGE_PARAMS)

    /**
     * Identity for de-duplication: host, path, and only the query parameters
     * that actually select content.
     *
     * Not used as a URL — only as a map key — so it can be lossy in ways a real
     * request could not be.
     */
    fun contentKey(url: String): String {
        val host = Urls.host(url).orEmpty()
        val path = Urls.path(url)

        val stableQuery = Urls.queryParams(url)
            .filterNot { (key, _) -> key in RANGE_PARAMS || key in VOLATILE_PARAMS }
            .sortedBy { it.first }
            .joinToString("&") { (key, value) -> "$key=$value" }

        return if (stableQuery.isEmpty()) "$host$path" else "$host$path?$stableQuery"
    }
}
