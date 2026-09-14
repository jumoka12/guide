package com.ampgames.vidsaver.domain.media.extractor

import com.ampgames.vidsaver.core.net.Urls
import com.ampgames.vidsaver.domain.media.FileNames
import com.ampgames.vidsaver.domain.media.MediaCandidate
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
            val byUrl = LinkedHashMap<String, SniffedMedia>()
            for (media in sniffed) {
                if (media.url.isBlank()) continue
                if (MediaTypes.isSegmentUrl(media.url)) continue
                if (MediaTypes.typeOf(media.url, media.mimeType) == null) continue
                val existing = byUrl[media.url]
                byUrl[media.url] = if (existing == null) media else merge(existing, media)
            }

            return byUrl.values.map { media ->
                val type = MediaTypes.typeOf(media.url, media.mimeType)!!
                val extension = MediaTypes.fileExtensionFor(type, media.url, media.mimeType)
                val title = media.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
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
                )
            }.sortedWith(
                // Best quality first, then larger files, then a stable URL order.
                compareByDescending<MediaCandidate> { it.height ?: 0 }
                    .thenByDescending { it.sizeBytes ?: 0L }
                    .thenBy { it.url },
            )
        }

        /** Keeps the richest information across two sightings of the same URL. */
        private fun merge(a: SniffedMedia, b: SniffedMedia): SniffedMedia = a.copy(
            mimeType = a.mimeType ?: b.mimeType,
            headers = if (a.headers.size >= b.headers.size) a.headers else b.headers,
            contentLength = a.contentLength ?: b.contentLength,
            width = a.width ?: b.width,
            height = a.height ?: b.height,
            posterUrl = a.posterUrl ?: b.posterUrl,
            title = a.title ?: b.title,
        )

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
