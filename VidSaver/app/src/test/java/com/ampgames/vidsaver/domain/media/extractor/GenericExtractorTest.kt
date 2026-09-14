package com.ampgames.vidsaver.domain.media.extractor

import com.ampgames.vidsaver.domain.media.MediaType
import com.ampgames.vidsaver.domain.media.SniffSource
import com.ampgames.vidsaver.domain.media.SniffedMedia
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericExtractorTest {

    private val extractor = GenericExtractor()
    private val pageUrl = "https://example.com/watch/1"

    @Test
    fun `stream segments are never offered as downloads`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia("https://cdn.example.com/seg1.ts", pageUrl),
                SniffedMedia("https://cdn.example.com/seg2.m4s", pageUrl),
                SniffedMedia("https://cdn.example.com/movie.mp4", pageUrl),
            ),
        )
        assertEquals(listOf("https://cdn.example.com/movie.mp4"), result.map { it.url })
    }

    @Test
    fun `non-media urls are dropped`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia("https://cdn.example.com/style.css", pageUrl),
                SniffedMedia("https://cdn.example.com/app.js", pageUrl),
                SniffedMedia("https://cdn.example.com/photo.jpg", pageUrl),
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `m3u8 is detected as HLS and saved as mp4`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(SniffedMedia("https://cdn.example.com/master.m3u8", pageUrl)),
        )
        assertEquals(MediaType.HLS, result.single().type)
        assertTrue(result.single().suggestedFileName.endsWith(".mp4"))
    }

    @Test
    fun `a video mime type wins over a missing extension`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia(
                    url = "https://cdn.example.com/stream?id=7",
                    pageUrl = pageUrl,
                    mimeType = "video/webm; codecs=\"vp9\"",
                ),
            ),
        )
        assertEquals(MediaType.PROGRESSIVE, result.single().type)
        assertTrue(result.single().suggestedFileName.endsWith(".webm"))
    }

    @Test
    fun `duplicate urls are merged keeping the richest information`() = runTest {
        val url = "https://cdn.example.com/movie.mp4"
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia(url, pageUrl, source = SniffSource.NETWORK, contentLength = 5_000L),
                SniffedMedia(
                    url = url,
                    pageUrl = pageUrl,
                    source = SniffSource.DOM,
                    height = 720,
                    posterUrl = "https://cdn.example.com/poster.jpg",
                    title = "The clip",
                ),
            ),
        )

        assertEquals(1, result.size)
        val candidate = result.single()
        assertEquals(5_000L, candidate.sizeBytes)
        assertEquals(720, candidate.height)
        assertEquals("https://cdn.example.com/poster.jpg", candidate.thumbnailUrl)
        assertEquals("The clip 720p.mp4", candidate.suggestedFileName)
    }

    @Test
    fun `candidates are sorted by quality then size`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia("https://c.example.com/a.mp4", pageUrl, height = 480),
                SniffedMedia("https://c.example.com/b.mp4", pageUrl, height = 1080),
                SniffedMedia("https://c.example.com/c.mp4", pageUrl, height = 720),
            ),
        )
        assertEquals(listOf(1080, 720, 480), result.map { it.height })
    }

    @Test
    fun `the page title is read from og_title first then title`() {
        assertEquals(
            "OG wins",
            GenericExtractor.pageTitle(
                """<meta property="og:title" content="OG wins"><title>Fallback</title>""",
            ),
        )
        assertEquals("Fallback", GenericExtractor.pageTitle("<title>Fallback</title>"))
        assertEquals("A & B", GenericExtractor.pageTitle("<title>A &amp; B</title>"))
        assertNull(GenericExtractor.pageTitle(""))
    }

    @Test
    fun `relative media urls are resolved against the page`() {
        assertEquals(
            "https://example.com/a/v.mp4",
            GenericExtractor.absolutize("v.mp4", "https://example.com/a/index.html"),
        )
        assertEquals(
            "https://example.com/v.mp4",
            GenericExtractor.absolutize("/v.mp4", "https://example.com/a/index.html"),
        )
        assertEquals(
            "https://cdn.example.com/v.mp4",
            GenericExtractor.absolutize("//cdn.example.com/v.mp4", "https://example.com/a/"),
        )
        assertEquals(
            "https://cdn.example.com/v.mp4",
            GenericExtractor.absolutize("https://cdn.example.com/v.mp4", "https://example.com/"),
        )
    }

    @Test
    fun `blob and data urls are rejected because they cannot be re-fetched`() {
        assertNull(GenericExtractor.absolutize("blob:https://example.com/abc", "https://example.com/"))
        assertNull(GenericExtractor.absolutize("data:video/mp4;base64,AAA", "https://example.com/"))
        assertNull(GenericExtractor.absolutize("", "https://example.com/"))
    }
}
