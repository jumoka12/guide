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
    /**
     * Playback state of the `<video>` element, from the page script. Only DOM
     * sightings carry it; for the network the page is a black box.
     */
    val activity: Activity = Activity.NONE,
) {
    /**
     * Combines two sightings of the same content, keeping whatever either one
     * knew. The DOM source wins because it is the page's own description of
     * the video; the network can only see bytes. Activity is a live state, not
     * a fact, so the newer sighting's wins outright.
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
        detectedAt = maxOf(detectedAt, other.detectedAt),
        activity = when {
            other.source != SniffSource.DOM -> activity
            source != SniffSource.DOM -> other.activity
            other.detectedAt >= detectedAt -> other.activity
            else -> activity
        },
    )
}

/**
 * What the page's own `<video>` element is doing, which is the strongest
 * signal of which clip the person means. A feed keeps a dozen preloaded
 * `<video>` elements around; only one of them is playing in the viewport.
 */
data class Activity(
    val isPlaying: Boolean = false,
    /** Most recently started or advanced playback, even if paused since. */
    val isActive: Boolean = false,
    /** Fraction of the element inside the viewport, 0..1. */
    val visibleFraction: Double = 0.0,
) {
    /**
     * Higher is more likely to be "the video on screen". Playing beats
     * everything; then the one that played last; then whatever is most on
     * screen. A clip that is entirely off screen scores nothing even if it is
     * quietly buffering.
     */
    val score: Double
        get() = (if (isPlaying) 4.0 else 0.0) +
            (if (isActive) 2.0 else 0.0) +
            visibleFraction

    companion object {
        val NONE = Activity()
    }
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
    /**
     * Whether the file carries an audio track. Null when nobody knows, which is
     * the usual case; false when a site extractor recognised a video-only
     * stream, so the sheet can warn before someone saves a silent clip.
     */
    val hasAudio: Boolean? = null,
    /** See [Activity.score]; zero for anything the page did not show playing. */
    val activityScore: Double = 0.0,
    /** When the sniffer last saw this file requested or reported. */
    val detectedAt: Long = 0L,
) {
    /** Stable identity for de-duplication and list keys. */
    val id: String get() = url

    /** The page is playing this one, or played it last. */
    val isOnScreen: Boolean get() = activityScore >= 2.0

    /** The page named this video; not just a file the page happened to fetch. */
    val isPrimary: Boolean get() = source == SniffSource.DOM

    val resolutionLabel: String?
        get() = when {
            height != null && height > 0 -> "${height}p"
            else -> null
        }
}
