package com.ampgames.vidsaver.domain.media.extractor

import com.ampgames.vidsaver.core.net.Urls
import com.ampgames.vidsaver.core.text.Base64Lite
import com.ampgames.vidsaver.domain.media.FileNames
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.SniffedMedia
import java.net.URLDecoder
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

class PinterestExtractor @Inject constructor() :
    DelegatingSiteExtractor("pinterest", listOf("pinterest.com", "pin.it", "pinimg.com"))

/**
 * Facebook streams one video as a bundle of files: several video-only DASH
 * tiers, one or two audio-only tracks, and sometimes a muxed progressive file.
 * Offered raw, that is a dozen identical-looking "MP4" chips of which most are
 * silent or sound-only.
 *
 * Every one of those URLs carries an `efg` parameter, Base64 JSON whose
 * `vencode_tag` names the encoding: `dash_h264-basic-gen2_720p`,
 * `dash_ln_heaac_vbr3_audio`, `sve_hd`. That is enough to drop the audio
 * tracks, label each tier with its height, and prefer files that have sound.
 * Nothing here calls Facebook; it only reads what the page already fetched.
 */
class FacebookExtractor @Inject constructor() :
    DelegatingSiteExtractor("facebook", listOf("facebook.com", "fb.watch", "fbcdn.net")) {

    override suspend fun extract(
        pageUrl: String,
        html: String,
        sniffed: List<SniffedMedia>,
    ): List<MediaCandidate> {
        val raw = super.extract(pageUrl, html, sniffed)
        val described = raw.map { it to describe(it.url) }

        val withSound = described.filter { (_, track) -> track.kind == Kind.MUXED }
        val kept = described
            .filter { (_, track) -> track.kind != Kind.AUDIO }
            // Silent DASH tiers are a fallback, not a peer, once a muxed file exists.
            .filter { (_, track) -> track.kind != Kind.VIDEO_ONLY || withSound.isEmpty() }
            .map { (candidate, track) ->
                val height = candidate.height ?: track.height
                candidate.copy(
                    height = height,
                    hasAudio = when (track.kind) {
                        Kind.MUXED -> true
                        Kind.VIDEO_ONLY -> false
                        else -> candidate.hasAudio
                    },
                    // The generic name was built before the height was known.
                    suggestedFileName = if (height != null && candidate.height == null) {
                        FileNames.forCandidate(
                            title = candidate.title,
                            pageUrl = pageUrl,
                            extension = candidate.suggestedFileName.substringAfterLast('.', "mp4"),
                            resolution = "${height}p",
                        )
                    } else {
                        candidate.suggestedFileName
                    },
                )
            }

        // One chip per quality: the same tier seen twice is the same download.
        val byQuality = LinkedHashMap<Pair<Int?, Boolean?>, MediaCandidate>()
        for (candidate in kept) {
            val key = candidate.height to candidate.hasAudio
            val existing = byQuality[key]
            if (existing == null || (candidate.sizeBytes ?: 0L) > (existing.sizeBytes ?: 0L)) {
                byQuality[key] = candidate
            }
        }

        return byQuality.values.sortedWith(
            compareByDescending<MediaCandidate> { it.isPrimary }
                .thenByDescending { it.hasAudio == true }
                .thenByDescending { it.height ?: 0 }
                .thenByDescending { it.sizeBytes ?: 0L }
                .thenBy { it.url },
        ).take(GenericExtractor.MAX_CANDIDATES)
    }

    internal enum class Kind { MUXED, VIDEO_ONLY, AUDIO, UNKNOWN }

    internal data class Track(val kind: Kind, val height: Int?)

    companion object {
        private val VENCODE_TAG = Regex(""""vencode_tag"\s*:\s*"([^"]*)"""")
        private val HEIGHT = Regex("""(\d{3,4})p""")

        /** Reads the encoding tag out of a Facebook CDN URL's `efg` parameter. */
        internal fun encodingTag(url: String): String? {
            val encoded = Urls.queryParams(url).firstOrNull { (k, _) -> k == "efg" }?.second ?: return null
            val decodedParam = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
            val json = Base64Lite.decodeToString(decodedParam) ?: return null
            return VENCODE_TAG.find(json)?.groupValues?.getOrNull(1)?.lowercase()
        }

        internal fun describe(url: String): Track {
            val tag = encodingTag(url) ?: return Track(Kind.UNKNOWN, null)
            val height = HEIGHT.find(tag)?.groupValues?.get(1)?.toIntOrNull()
                ?: when {
                    tag.contains("hd") -> 720
                    tag.contains("sd") -> 360
                    else -> null
                }
            return when {
                tag.contains("audio") -> Track(Kind.AUDIO, null)
                tag.startsWith("dash") -> Track(Kind.VIDEO_ONLY, height)
                else -> Track(Kind.MUXED, height)
            }
        }
    }
}
