package com.ampgames.vidsaver.domain.media

import com.ampgames.vidsaver.core.net.Urls

/**
 * Recognises playable media from a MIME type or a URL. Kept pure so the
 * WebView interceptor, the extractors and the download strategies all agree on
 * what counts as video.
 */
object MediaTypes {

    val HLS_MIME_TYPES = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/mpegurl",
        "audio/x-mpegurl",
        "vnd.apple.mpegurl",
    )

    private val PROGRESSIVE_EXTENSIONS = setOf(
        "mp4", "m4v", "webm", "mov", "mkv", "3gp", "avi", "flv", "ogv",
    )

    private val HLS_EXTENSIONS = setOf("m3u8", "m3u")

    /** Extensions that are video *segments*, not standalone downloads. */
    private val SEGMENT_EXTENSIONS = setOf("ts", "m4s", "cmfv", "fmp4")

    private val EXTENSION_TO_MIME = mapOf(
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "webm" to "video/webm",
        "mov" to "video/quicktime",
        "mkv" to "video/x-matroska",
        "3gp" to "video/3gpp",
        "avi" to "video/x-msvideo",
        "flv" to "video/x-flv",
        "ogv" to "video/ogg",
    )

    fun isVideoMime(mimeType: String?): Boolean {
        val mime = normalizeMime(mimeType) ?: return false
        return mime.startsWith("video/") || mime in HLS_MIME_TYPES
    }

    fun isHlsMime(mimeType: String?): Boolean =
        normalizeMime(mimeType)?.let { it in HLS_MIME_TYPES } == true

    /** Strips parameters and whitespace: `video/mp4; codecs="avc1"` -> `video/mp4`. */
    fun normalizeMime(mimeType: String?): String? =
        mimeType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun isHlsUrl(url: String): Boolean = Urls.fileExtension(url) in HLS_EXTENSIONS

    fun isProgressiveUrl(url: String): Boolean = Urls.fileExtension(url) in PROGRESSIVE_EXTENSIONS

    /**
     * A stream segment container, which cannot be played or downloaded alone.
     *
     * Deliberately extension-only. A byte-ranged request for a normal MP4 — how
     * Facebook and Instagram serve progressive video — is *not* this: dropping
     * those would leave a feed with no candidates at all. Those are canonicalised
     * back to the whole file by [MediaIdentity.canonicalUrl] instead.
     */
    fun isSegmentUrl(url: String): Boolean = Urls.fileExtension(url) in SEGMENT_EXTENSIONS

    /**
     * Whether a URL is worth inspecting further. Used by the WebView interceptor
     * as a cheap first pass before confirming the real Content-Type.
     */
    fun looksLikeMediaUrl(url: String): Boolean =
        isHlsUrl(url) || isProgressiveUrl(url)

    fun typeOf(url: String, mimeType: String?): MediaType? = when {
        isHlsMime(mimeType) || isHlsUrl(url) -> MediaType.HLS
        normalizeMime(mimeType)?.startsWith("video/") == true -> MediaType.PROGRESSIVE
        isProgressiveUrl(url) -> MediaType.PROGRESSIVE
        else -> null
    }

    /** Container extension a candidate should be saved with. */
    fun fileExtensionFor(type: MediaType, url: String, mimeType: String?): String {
        if (type == MediaType.HLS) return "mp4" // remuxed after download
        val fromUrl = Urls.fileExtension(url)
        if (fromUrl != null && fromUrl in PROGRESSIVE_EXTENSIONS) return fromUrl
        return when (normalizeMime(mimeType)) {
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "video/x-matroska" -> "mkv"
            "video/3gpp" -> "3gp"
            else -> "mp4"
        }
    }

    fun mimeForExtension(extension: String): String =
        EXTENSION_TO_MIME[extension.lowercase()] ?: "video/mp4"
}
