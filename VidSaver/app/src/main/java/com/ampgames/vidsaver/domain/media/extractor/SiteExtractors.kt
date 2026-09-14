package com.ampgames.vidsaver.domain.media.extractor

import com.ampgames.vidsaver.core.net.Urls
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.SniffedMedia
import javax.inject.Inject

/**
 * Base for a site-specific extractor that currently has no bespoke parsing.
 *
 * Each subclass is already wired into [ExtractorRegistry] and claims its
 * domains, so adding real parsing later means overriding [extract] only — no
 * registry or DI change. Until then they delegate to the generic path, which
 * offers whatever the page itself fetched.
 */
abstract class DelegatingSiteExtractor(
    override val name: String,
    private val domains: List<String>,
) : SiteExtractor {

    override fun matches(url: String): Boolean {
        val host = Urls.host(url) ?: return false
        return domains.any { Urls.hostMatchesDomain(host, it) }
    }

    override suspend fun extract(
        pageUrl: String,
        html: String,
        sniffed: List<SniffedMedia>,
    ): List<MediaCandidate> = GenericExtractor.buildCandidates(
        pageUrl = pageUrl,
        sniffed = sniffed,
        fallbackTitle = GenericExtractor.pageTitle(html),
    )
}

class TikTokExtractor @Inject constructor() :
    DelegatingSiteExtractor("tiktok", listOf("tiktok.com", "vm.tiktok.com"))

class InstagramExtractor @Inject constructor() :
    DelegatingSiteExtractor("instagram", listOf("instagram.com", "cdninstagram.com"))

class TwitterExtractor @Inject constructor() :
    DelegatingSiteExtractor("twitter", listOf("x.com", "twitter.com", "t.co", "twimg.com"))

class FacebookExtractor @Inject constructor() :
    DelegatingSiteExtractor("facebook", listOf("facebook.com", "fb.watch", "fbcdn.net"))

class PinterestExtractor @Inject constructor() :
    DelegatingSiteExtractor("pinterest", listOf("pinterest.com", "pin.it", "pinimg.com"))
