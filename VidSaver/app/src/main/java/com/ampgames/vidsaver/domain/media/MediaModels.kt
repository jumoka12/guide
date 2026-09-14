package com.ampgames.vidsaver.domain.media

/** How the media is delivered, which decides the download strategy. */
enum class MediaType {
    /** A single file fetched over HTTP: mp4, webm, mov… */
    PROGRESSIVE,

    /** An HLS playlist that has to be assembled from segments. */
    HLS,
}

/** Where a piece of media was noticed. */
enum class SniffSource {
    /** Seen as a subresource request made by the page. */
    NETWORK,

    /** Reported by the injected page script from the DOM. */
    DOM,
}

/**
 * Raw media spotted while a page was being displayed.
 *
 * [headers] carries only what is needed to re-fetch exactly what the WebView
 * already fetched — Referer, User-Agent and, when the page sent them, Cookies.
 * Nothing here defeats access control: if the page could not play it, we never
 * saw it.
 */
data class SniffedMedia(
    val url: String,
    val pageUrl: String,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val contentLength: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val posterUrl: String? = null,
    val title: String? = null,
    val source: SniffSource = SniffSource.NETWORK,
    val detectedAt: Long = 0L,
) {
    /**
     * Combines two sightings of the same content, keeping whatever either one
     * knew. The DOM source wins because it is the page's own description of
     * the video; the network can only see bytes.
     */
    fun enrichedWith(other: SniffedMedia): SniffedMedia = copy(
        mimeType = mimeType ?: other.mimeType,
        headers = if (headers.size >= other.headers.size) headers else other.headers,
        contentLength = contentLength ?: other.contentLength,
        width = width ?: other.width,
        height = height ?: other.height,
        posterUrl = posterUrl ?: other.posterUrl,
        title = title ?: other.title,
        source = if (source == SniffSource.DOM || other.source == SniffSource.DOM) {
            SniffSource.DOM
        } else {
            SniffSource.NETWORK
        },
    )
}

/** A downloadable option shown to the user in the candidates sheet. */
data class MediaCandidate(
    val url: String,
    val pageUrl: String,
    val type: MediaType,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null,
    val title: String? = null,
    val suggestedFileName: String,
    /**
     * Where the page itself pointed at this video (DOM) versus what merely
     * crossed the wire (NETWORK). The sheet leads with the former: a
     * `<video>` or `og:video` is the page saying "this is the video", while
     * the network view of one clip is a dozen quality tiers and audio tracks.
     */
    val source: SniffSource = SniffSource.NETWORK,
) {
    /** Stable identity for de-duplication and list keys. */
    val id: String get() = url

    /** The page named this video; not just a file the page happened to fetch. */
    val isPrimary: Boolean get() = source == SniffSource.DOM

    val resolutionLabel: String?
        get() = when {
            height != null && height > 0 -> "${height}p"
            else -> null
        }
}
