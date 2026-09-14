package com.ampgames.vidsaver.domain.media.extractor

import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.SniffedMedia
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Picks the extractor for a page and runs it.
 *
 * Order matters and is enforced here rather than left to DI set iteration:
 * [YouTubeBlocker] is always consulted first so no site extractor can claim a
 * blocked domain, then the site-specific extractors, and [GenericExtractor]
 * last as the catch-all.
 */
@Singleton
class ExtractorRegistry @Inject constructor(
    private val youTubeBlocker: YouTubeBlocker,
    siteExtractors: Set<@JvmSuppressWildcards SiteExtractor>,
    private val genericExtractor: GenericExtractor,
) {

    /**
     * Site extractors in a stable order, with the blocker and the catch-all
     * excluded — those two are positional and handled explicitly.
     */
    private val ordered: List<SiteExtractor> = siteExtractors
        .filterNot { it is YouTubeBlocker || it is GenericExtractor }
        .sortedBy { it.name }

    /** True when the app refuses to handle this page at all. */
    fun isUnsupported(pageUrl: String): Boolean = youTubeBlocker.matches(pageUrl)

    fun extractorFor(pageUrl: String): SiteExtractor = when {
        youTubeBlocker.matches(pageUrl) -> youTubeBlocker
        else -> ordered.firstOrNull { it.matches(pageUrl) } ?: genericExtractor
    }

    /**
     * Runs the matching extractor. A failing extractor falls back to the generic
     * path rather than leaving the user with no candidates — except on a blocked
     * domain, which always yields nothing.
     */
    suspend fun extract(
        pageUrl: String,
        html: String,
        sniffed: List<SniffedMedia>,
    ): List<MediaCandidate> {
        if (isUnsupported(pageUrl)) return emptyList()

        val extractor = extractorFor(pageUrl)
        val candidates = runCatching { extractor.extract(pageUrl, html, sniffed) }
            .getOrElse { error ->
                Timber.e(error, "Extractor %s failed, falling back to generic", extractor.name)
                runCatching { genericExtractor.extract(pageUrl, html, sniffed) }.getOrDefault(emptyList())
            }

        // Defence in depth: never surface media from a blocked domain, even if a
        // site extractor somehow produced it.
        return candidates.filterNot { youTubeBlocker.matches(it.url) }
    }
}
