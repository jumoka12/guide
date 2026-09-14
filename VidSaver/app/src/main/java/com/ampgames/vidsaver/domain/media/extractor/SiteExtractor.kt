package com.ampgames.vidsaver.domain.media.extractor

import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.SniffedMedia

/**
 * Turns what was observed on a page into the list of downloads to offer.
 *
 * Implementations must only work with media the page already delivered to the
 * WebView for playback — the [sniffed] list and the rendered [html]. An
 * extractor must never sign requests, replay a private API, or otherwise get at
 * media behind a login wall, paywall, age gate or DRM. If the user could not
 * play it in the browser, there is nothing here to extract.
 *
 * See README "Adding a new SiteExtractor".
 */
interface SiteExtractor {

    /** Human-readable name, used in logs and diagnostics. */
    val name: String

    /** Whether this extractor claims [url]. The registry takes the first claim. */
    fun matches(url: String): Boolean

    /**
     * @param preferredTitle the name the page itself gives the playing clip
     *   (its caption), when the injected script found one. Beats the page
     *   title, which on a feed is the site's slogan.
     */
    suspend fun extract(
        pageUrl: String,
        html: String,
        sniffed: List<SniffedMedia>,
        preferredTitle: String? = null,
    ): List<MediaCandidate>
}
