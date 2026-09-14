package com.ampgames.vidsaver.domain.media.extractor

import com.ampgames.vidsaver.core.net.Urls
import com.ampgames.vidsaver.domain.media.FileNames
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.MediaIdentity
import com.ampgames.vidsaver.domain.media.MediaType
import com.ampgames.vidsaver.domain.media.MediaTypes
import com.ampgames.vidsaver.domain.media.SniffedMedia
import javax.inject.Inject

/**
 * The default path, used for any site without bespoke handling: it offers
 * exactly what the page already fetched or declared, and nothing else.
 *
 * Candidates are de-duplicated by URL, stream segments are dropped (they are
 * parts of a playlist, not downloads), and the best-described observation of a
 * given URL wins when the same URL is seen more than once — for example when
 * the DOM reports a `<video>` whose bytes the network layer also saw.
 */
class GenericExtractor @Inject constructor() : SiteExtractor {

    override val name: String = "generic"

    override fun matches(url: String): Boolean = true

    override suspend fun extract(
        pageUrl: String,
        html: String,
        sniffed: List<SniffedMedia>,
    ): List<MediaCandidate> = buildCandidates(pageUrl, sniffed, pageTitle(html))

    companion object {

        /**
         * Shared by every site extractor that delegates to the generic path, so
         * they all produce consistently shaped candidates.
         */
        fun buildCandidates(
            pageUrl: String,
            sniffed: List<SniffedMedia>,
            fallbackTitle: String? = null,
        ): List<MediaCandidate> {
            // Keyed by content identity, not by URL. A feed re-signs the same
            // asset on every request and slices it into byte ranges, so literal
            // URL comparison reports one video as dozens.
            val byContent = LinkedHashMap<String, SniffedMedia>()
            for (raw in sniffed) {
                if (raw.url.isBlank()) continue
                if (MediaTypes.isSegmentUrl(raw.url)) continue
                if (MediaTypes.typeOf(raw.url, raw.mimeType) == null) continue

                val media = raw.copy(url = MediaIdentity.canonicalUrl(raw.url))
                val key = MediaIdentity.contentKey(media.url)
                val existing = byContent[key]
                byContent[key] = existing?.enrichedWith(media) ?: media
            }

            val cleanFallback = FileNames.cleanTitle(fallbackTitle, pageUrl)

            // A master playlist whose variants are known is a menu, not a file:
            // the variants take its place, one per quality.
            val mastersWithVariants = byContent.values
                .mapNotNull { it.hlsVariantOf?.let { master -> MediaIdentity.contentKey(master) } }
                .toSet()

            return byContent.entries.asSequence().filter { (key, media) ->
                when {
                    media.audioOnly -> false
                    key in mastersWithVariants -> false
                    // A "video" of a few kilobytes is an init segment or a
                    // probe response, never something worth saving.
                    isTinyFile(media) -> false
                    else -> true
                }
            }.map { (_, media) ->
                val type = MediaTypes.typeOf(media.url, media.mimeType)!!
                val extension = MediaTypes.fileExtensionFor(type, media.url, media.mimeType)
                val title = FileNames.cleanTitle(media.title, pageUrl) ?: cleanFallback
                val resolution = media.height?.takeIf { it > 0 }?.let { "${it}p" }
                MediaCandidate(
                    url = media.url,
                    pageUrl = media.pageUrl.ifBlank { pageUrl },
                    type = type,
                    headers = media.headers,
                    mimeType = media.mimeType,
                    width = media.width,
                    height = media.height,
                    sizeBytes = media.contentLength,
                    thumbnailUrl = media.posterUrl,
                    title = title,
                    suggestedFileName = FileNames.forCandidate(
                        title = title,
                        pageUrl = pageUrl,
                        extension = extension,
                        resolution = resolution,
                    ),
                    source = media.source,
                    activityScore = media.activity.score,
                    detectedAt = media.detectedAt,
                    hlsVariantOf = media.hlsVariantOf,
                )
            }.sortedWith(ranking()).distinctBy { qualityKey(it) }.take(MAX_CANDIDATES).toList()
        }

        /**
         * One chip per quality per stream host. A page that fetches its
         * playlist twice under two tokens produced every quality twice; the
         * ranked-first copy is the one kept.
         */
        private fun qualityKey(c: MediaCandidate): Any =
            if (c.type == MediaType.HLS && c.height != null) {
                Triple(Urls.host(c.url), c.height, c.hasAudio)
            } else {
                c.url
            }

        /**
         * Below this a progressive "video" is an init segment, a redirect body
         * or an error page. HLS playlists are exempt: their byte size is the
         * size of a text file and says nothing about the stream.
         */
        const val MIN_VIDEO_BYTES = 100_000L

        private fun isTinyFile(media: SniffedMedia): Boolean {
            val size = media.contentLength ?: return false
            if (MediaTypes.typeOf(media.url, media.mimeType) == MediaType.HLS) return false
            return size in 1 until MIN_VIDEO_BYTES
        }

        /**
         * The order the sheet shows, best guess first:
         *
         * 1. what the page is playing now, or played last;
         * 2. what the page itself named (`<video>`, `og:video`) over what
         *    merely crossed the wire;
         * 3. the most recently requested — in a feed, the clip that just
         *    scrolled into view is the one that just loaded, and the
         *    one requested a minute ago is three posts up;
         * 4. best quality, then larger files, then a stable URL order.
         *
         * Recency is bucketed to two seconds so the tiers of one video, which
         * load together, still fall into the quality order.
         */
        fun ranking(): Comparator<MediaCandidate> =
            compareByDescending<MediaCandidate> { it.activityScore }
                .thenByDescending { it.isPrimary }
                .thenByDescending { it.detectedAt / RECENCY_BUCKET_MS }
                .thenByDescending { it.height ?: 0 }
                .thenByDescending { it.sizeBytes ?: 0L }
                .thenBy { it.url }

        private const val RECENCY_BUCKET_MS = 2_000L

        /**
         * A hard ceiling on what the sheet will offer.
         *
         * A social feed can legitimately hold dozens of distinct videos, and a
         * list that long is not a chooser — it is a wall. The ordering above puts
         * the best candidates first, so the cap trims the tail.
         */
        const val MAX_CANDIDATES = 12

        private val TITLE_REGEX =
            Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        private val OG_TITLE_REGEX = Regex(
            """<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']*)["']""",
            RegexOption.IGNORE_CASE,
        )

        /** Best-effort page title from raw HTML; the DOM script usually beats this. */
        fun pageTitle(html: String): String? {
            if (html.isBlank()) return null
            val og = OG_TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)
            val title = og ?: TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)
            return title?.let { decodeEntities(it) }?.trim()?.takeIf { it.isNotBlank() }
        }

        private fun decodeEntities(raw: String): String = raw
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")

        /** Resolves a possibly relative media URL against the page it came from. */
        fun absolutize(url: String, pageUrl: String): String? {
            val u = url.trim()
            if (u.isEmpty() || u.startsWith("blob:") || u.startsWith("data:")) return null
            if (u.startsWith("http://") || u.startsWith("https://")) return u
            val pageHost = Urls.host(pageUrl) ?: return null
            return when {
                u.startsWith("//") -> "https:$u"
                u.startsWith("/") -> "https://$pageHost$u"
                else -> {
                    val base = pageUrl.substringBefore('?').substringBefore('#')
                    "${base.substringBeforeLast('/', "https://$pageHost")}/$u"
                }
            }
        }
    }
}
