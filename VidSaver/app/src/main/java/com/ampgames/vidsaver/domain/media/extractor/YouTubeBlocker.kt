package com.ampgames.vidsaver.domain.media.extractor

import com.ampgames.vidsaver.domain.browser.UnsupportedDomains
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.SniffedMedia
import javax.inject.Inject

/**
 * Policy gate, registered ahead of every other extractor: YouTube and other
 * Google-owned video domains are never extracted from. It always returns an
 * empty list, and [ExtractorRegistry.isUnsupported] drives the "not supported"
 * message in the UI.
 *
 * This is a hard requirement of Google Play policy, so it is enforced in code
 * and cannot be switched off through `app_config.json`.
 */
class YouTubeBlocker @Inject constructor() : SiteExtractor {

    override val name: String = "youtube-blocker"

    override fun matches(url: String): Boolean = UnsupportedDomains.isUnsupported(url)

    override suspend fun extract(
        pageUrl: String,
        html: String,
        sniffed: List<SniffedMedia>,
        preferredTitle: String?,
    ): List<MediaCandidate> = emptyList()
}
