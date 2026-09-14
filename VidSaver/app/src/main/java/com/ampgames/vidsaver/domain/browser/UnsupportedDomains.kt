package com.ampgames.vidsaver.domain.browser

import com.ampgames.vidsaver.core.net.Urls

/**
 * Google-owned video domains that this app must never support, per Google Play
 * policy.
 *
 * This list lives in code, not in `app_config.json`, on purpose: config is a
 * remote-shaped knob and must not be able to turn a policy block off. The
 * config's `browser.blocked_domains` is additive only — it can block more, never
 * less.
 */
object UnsupportedDomains {

    /**
     * Matched as the domain itself or any subdomain. Covers the four domains the
     * spec names plus the privacy-enhanced embed domain and the media CDN that
     * actually serves YouTube streams, so nothing slips through the extractor.
     */
    val YOUTUBE: List<String> = listOf(
        "youtube.com",
        "youtu.be",
        "m.youtube.com",
        "music.youtube.com",
        "youtube-nocookie.com",
        "googlevideo.com",
        "ytimg.com",
    )

    /** True when [url] points at a domain this app refuses to handle. */
    fun isUnsupported(url: String): Boolean {
        val host = Urls.host(url) ?: return false
        return isUnsupportedHost(host)
    }

    fun isUnsupportedHost(host: String): Boolean =
        YOUTUBE.any { Urls.hostMatchesDomain(host, it) }
}
