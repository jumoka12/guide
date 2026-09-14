package com.ampgames.vidsaver.domain.media.extractor

import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.MediaType
import com.ampgames.vidsaver.domain.media.SniffedMedia
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorRegistryTest {

    private val generic = GenericExtractor()
    private val blocker = YouTubeBlocker()

    private fun registry(vararg extractors: SiteExtractor) =
        ExtractorRegistry(blocker, extractors.toSet(), generic)

    private fun sniffed(url: String, height: Int? = null) = SniffedMedia(
        url = url,
        pageUrl = "https://example.com/watch",
        mimeType = null,
        height = height,
    )

    @Test
    fun `a site extractor claims its own domain`() {
        val registry = registry(TikTokExtractor(), InstagramExtractor())
        assertEquals("tiktok", registry.extractorFor("https://www.tiktok.com/@u/video/1").name)
        assertEquals("instagram", registry.extractorFor("https://instagram.com/reel/x").name)
    }

    @Test
    fun `an unclaimed domain falls through to the generic extractor`() {
        val registry = registry(TikTokExtractor(), InstagramExtractor())
        assertEquals("generic", registry.extractorFor("https://example.com/video").name)
        assertEquals("generic", registry.extractorFor("https://vimeo.com/12345").name)
    }

    @Test
    fun `the youtube blocker wins over every site extractor`() {
        // A site extractor that greedily claims everything must still not get
        // a blocked domain.
        val greedy = object : SiteExtractor {
            override val name = "greedy"
            override fun matches(url: String) = true
            override suspend fun extract(
                pageUrl: String,
                html: String,
                sniffed: List<SniffedMedia>,
                preferredTitle: String?,
            ) = listOf(candidate("https://evil.example/leak.mp4"))
        }
        val registry = registry(greedy)

        assertEquals("youtube-blocker", registry.extractorFor("https://youtube.com/watch?v=1").name)
        assertTrue(registry.isUnsupported("https://youtu.be/abc"))
        assertFalse(registry.isUnsupported("https://tiktok.com/@u/video/1"))
    }

    @Test
    fun `extraction on a blocked domain yields nothing`() = runTest {
        val registry = registry(TikTokExtractor())
        val result = registry.extract(
            pageUrl = "https://www.youtube.com/watch?v=abc",
            html = "<title>Something</title>",
            sniffed = listOf(sniffed("https://cdn.example.com/video.mp4")),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `candidates pointing at a blocked domain are filtered out`() = runTest {
        val leaky = object : SiteExtractor {
            override val name = "leaky"
            override fun matches(url: String) = url.contains("example.com")
            override suspend fun extract(
                pageUrl: String,
                html: String,
                sniffed: List<SniffedMedia>,
                preferredTitle: String?,
            ) = listOf(
                candidate("https://r1.googlevideo.com/videoplayback"),
                candidate("https://cdn.example.com/ok.mp4"),
            )
        }
        val result = registry(leaky).extract("https://example.com/p", "", emptyList())

        assertEquals(1, result.size)
        assertEquals("https://cdn.example.com/ok.mp4", result.single().url)
    }

    @Test
    fun `a throwing extractor falls back to the generic path`() = runTest {
        val broken = object : SiteExtractor {
            override val name = "broken"
            override fun matches(url: String) = true
            override suspend fun extract(
                pageUrl: String,
                html: String,
                sniffed: List<SniffedMedia>,
                preferredTitle: String?,
            ): List<MediaCandidate> = error("boom")
        }
        val result = registry(broken).extract(
            pageUrl = "https://example.com/p",
            html = "<title>Clip</title>",
            sniffed = listOf(sniffed("https://cdn.example.com/video.mp4")),
        )

        assertEquals(1, result.size)
        assertEquals("https://cdn.example.com/video.mp4", result.single().url)
    }

    @Test
    fun `site extractors delegate to the generic path and produce candidates`() = runTest {
        val result = registry(TikTokExtractor()).extract(
            pageUrl = "https://www.tiktok.com/@u/video/1",
            html = "<title>A clip</title>",
            sniffed = listOf(sniffed("https://cdn.tiktok.example/v.mp4", height = 1080)),
        )

        assertEquals(1, result.size)
        assertEquals(MediaType.PROGRESSIVE, result.single().type)
        assertEquals("A clip 1080p.mp4", result.single().suggestedFileName)
    }

    @Test
    fun `extractor order is stable regardless of set iteration order`() {
        val a = registry(TikTokExtractor(), InstagramExtractor(), TwitterExtractor())
        val b = registry(TwitterExtractor(), TikTokExtractor(), InstagramExtractor())
        val url = "https://x.com/u/status/1"
        assertEquals(a.extractorFor(url).name, b.extractorFor(url).name)
    }

    private fun candidate(url: String) = MediaCandidate(
        url = url,
        pageUrl = "https://example.com/p",
        type = MediaType.PROGRESSIVE,
        suggestedFileName = "x.mp4",
    )
}
