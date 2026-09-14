package com.ampgames.vidsaver.data.browser.sniffer

import com.ampgames.vidsaver.domain.media.extractor.GenericExtractor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One media element reported by the injected page script.
 *
 * This is untrusted input: it is produced by JavaScript running in a page we did
 * not write, so every field is optional, nothing is executed, and the URL is
 * re-resolved and re-validated on this side before it is used.
 */
@Serializable
data class DomMediaReport(
    val url: String = "",
    @SerialName("mime") val mimeType: String? = null,
    val poster: String? = null,
    val title: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration") val durationSeconds: Double? = null,
    /** The element is playing right now. */
    val playing: Boolean = false,
    /** The element most recently started or advanced playback. */
    val active: Boolean = false,
    /** Fraction of the element inside the viewport, 0..1. */
    val visible: Double? = null,
) {
    /**
     * Absolute form of [url] against [pageUrl], or null when the URL is not
     * fetchable — `blob:` and `data:` URLs exist only inside the page.
     */
    fun absoluteUrl(pageUrl: String): String? =
        url.takeIf { it.isNotBlank() }?.let { GenericExtractor.absolutize(it, pageUrl) }
}

@Serializable
data class DomMediaPayload(
    @SerialName("pageUrl") val pageUrl: String = "",
    val title: String? = null,
    val media: List<DomMediaReport> = emptyList(),
)
