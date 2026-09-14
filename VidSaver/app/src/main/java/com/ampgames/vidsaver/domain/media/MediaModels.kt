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
)

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
) {
    /** Stable identity for de-duplication and list keys. */
    val id: String get() = url

    val resolutionLabel: String?
        get() = when {
            height != null && height > 0 -> "${height}p"
            else -> null
        }
}
