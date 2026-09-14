package com.ampgames.vidsaver.domain.media.extractor

import com.ampgames.vidsaver.domain.media.Activity
import com.ampgames.vidsaver.domain.media.MediaType
import com.ampgames.vidsaver.domain.media.SniffSource
import com.ampgames.vidsaver.domain.media.SniffedMedia
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                SniffedMedia(url, pageUrl, source = SniffSource.NETWORK, contentLength = 5_000_000L),
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
        assertEquals(5_000_000L, candidate.sizeBytes)
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

    // --- Regression: a Facebook feed offered 56 downloads for a few clips ------

    private fun fbChunk(file: String, start: Long, end: Long, signature: String) =
        "https://video-lhr8-1.xx.fbcdn.net/v/t42.1790-2/$file" +
            "?_nc_cat=103&ccb=1-7&_nc_ohc=$signature&oh=00_AY$signature&oe=6512ABCD" +
            "&bytestart=$start&byteend=$end"

    @Test
    fun `byte-ranged chunks of one video collapse into a single candidate`() = runTest {
        // One clip, requested in twenty ranges the way a feed actually does it.
        val chunks = (0 until 20).map { i ->
            SniffedMedia(
                url = fbChunk("clip.mp4", i * 100_000L, (i + 1) * 100_000L - 1, "sigA"),
                pageUrl = pageUrl,
            )
        }

        val result = extractor.extract(pageUrl, "", chunks)

        assertEquals(1, result.size)
        assertFalse("the offered url must not be a slice", result.single().url.contains("bytestart"))
    }

    @Test
    fun `a feed of several videos yields one candidate each`() = runTest {
        val sniffed = listOf("a", "b", "c").flatMap { name ->
            (0 until 8).map { i ->
                SniffedMedia(
                    url = fbChunk("$name.mp4", i * 50_000L, (i + 1) * 50_000L - 1, "sig$name"),
                    pageUrl = pageUrl,
                )
            }
        }

        val result = extractor.extract(pageUrl, "", sniffed)

        assertEquals(3, result.size)
    }

    @Test
    fun `the same video re-signed between requests counts once`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia(fbChunk("clip.mp4", 0, 99_999, "firstSignature"), pageUrl),
                SniffedMedia(fbChunk("clip.mp4", 100_000, 199_999, "secondSignature"), pageUrl),
            ),
        )

        assertEquals(1, result.size)
    }

    // --- Regression: twelve rows for one clip, none of them the page's own -------

    /**
     * A feed streams one clip as many quality tiers and audio tracks, each a
     * different file. The page's own `<video>`/`og:video` is the one row that
     * should lead; the tiers are alternatives, not peers.
     */
    @Test
    fun `what the page itself named comes first and is marked primary`() = runTest {
        val tiers = (1..11).map { i ->
            SniffedMedia(fbChunk("tier$i.mp4", 0, 99_999, "sig$i"), pageUrl, source = SniffSource.NETWORK)
        }
        val declared = SniffedMedia(
            url = "https://video.xx.fbcdn.net/v/t42/declared.mp4?oh=abc&oe=123",
            pageUrl = pageUrl,
            posterUrl = "https://scontent.xx.fbcdn.net/poster.jpg",
            title = "Funny cat | Facebook",
            source = SniffSource.DOM,
        )

        val result = extractor.extract(pageUrl, "", tiers + declared)

        assertEquals(12, result.size)
        assertTrue(result.first().isPrimary)
        assertTrue(result.first().url.contains("declared.mp4"))
        assertEquals(1, result.count { it.isPrimary })
    }

    @Test
    fun `a network sighting merged into a DOM sighting stays primary`() = runTest {
        val url = "https://cdn.example.com/movie.mp4"
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia(url, pageUrl, source = SniffSource.NETWORK, contentLength = 9_000_000L),
                SniffedMedia(url, pageUrl, source = SniffSource.DOM, title = "Movie"),
            ),
        )

        assertEquals(1, result.size)
        assertTrue(result.single().isPrimary)
        assertEquals(9_000_000L, result.single().sizeBytes)
    }

    @Test
    fun `titles are cleaned of hashtags and site branding`() = runTest {
        val page = "https://m.facebook.com/watch/?v=1"
        val result = extractor.extract(
            page,
            "<title>Sunset #fyp #reels | Facebook</title>",
            listOf(SniffedMedia("https://cdn.example.com/a.mp4", page)),
        )

        assertEquals("Sunset", result.single().title)
        assertEquals("Sunset.mp4", result.single().suggestedFileName)
    }

    // --- Regression: the sheet led with a preloaded clip, not the one on screen --

    @Test
    fun `the video the page is playing comes first whatever its quality`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia("https://c.example.com/next.mp4", pageUrl, height = 1080, source = SniffSource.DOM),
                SniffedMedia(
                    "https://c.example.com/current.mp4",
                    pageUrl,
                    height = 540,
                    source = SniffSource.DOM,
                    activity = Activity(isPlaying = true, isActive = true, visibleFraction = 1.0),
                ),
                SniffedMedia("https://c.example.com/ad.mp4", pageUrl, height = 1080, source = SniffSource.DOM),
            ),
        )

        assertTrue(result.first().url.endsWith("current.mp4"))
        assertTrue(result.first().isOnScreen)
        assertFalse(result[1].isOnScreen)
    }

    @Test
    fun `a paused clip that played last still beats one that never played`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia("https://c.example.com/never.mp4", pageUrl, source = SniffSource.DOM, activity = Activity(visibleFraction = 0.9)),
                SniffedMedia("https://c.example.com/paused.mp4", pageUrl, source = SniffSource.DOM, activity = Activity(isActive = true, visibleFraction = 0.4)),
            ),
        )
        assertTrue(result.first().url.endsWith("paused.mp4"))
    }

    @Test
    fun `among files the page never showed playing the newest request leads`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia("https://c.example.com/old.mp4", pageUrl, height = 1080, detectedAt = 10_000L),
                SniffedMedia("https://c.example.com/new.mp4", pageUrl, height = 480, detectedAt = 60_000L),
            ),
        )
        assertTrue(result.first().url.endsWith("new.mp4"))
    }

    @Test
    fun `tiers loaded together still sort by quality`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia("https://c.example.com/480.mp4", pageUrl, height = 480, detectedAt = 60_100L),
                SniffedMedia("https://c.example.com/1080.mp4", pageUrl, height = 1080, detectedAt = 60_000L),
            ),
        )
        assertEquals(listOf(1080, 480), result.map { it.height })
    }

    @Test
    fun `a later sighting updates the playback state of a known file`() {
        val first = SniffedMedia("u", pageUrl, source = SniffSource.DOM, detectedAt = 1L, activity = Activity(isPlaying = true))
        val later = SniffedMedia("u", pageUrl, source = SniffSource.DOM, detectedAt = 2L, activity = Activity(isPlaying = false))
        assertFalse(first.enrichedWith(later).activity.isPlaying)
        // A network sighting knows nothing about playback and must not erase it.
        val network = SniffedMedia("u", pageUrl, source = SniffSource.NETWORK, detectedAt = 3L, contentLength = 5L)
        assertTrue(first.enrichedWith(network).activity.isPlaying)
    }

    // --- Regression: Dailymotion offered six "HLS 1.6 KB" chips and a 1.4 KB mp4 --

    @Test
    fun `a master playlist gives way to its variants`() = runTest {
        val master = "https://cdn.example.com/video/abc.m3u8?sec=1"
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia(master, pageUrl, mimeType = "application/vnd.apple.mpegurl"),
                SniffedMedia("https://cdn.example.com/video/abc/1080.m3u8", pageUrl, height = 1080, hlsVariantOf = master),
                SniffedMedia("https://cdn.example.com/video/abc/480.m3u8", pageUrl, height = 480, hlsVariantOf = master),
                SniffedMedia("https://cdn.example.com/video/abc/audio.m3u8", pageUrl, hlsVariantOf = master, audioOnly = true),
            ),
        )

        assertEquals(listOf(1080, 480), result.map { it.height })
        assertFalse("the master is a menu, not a download", result.any { it.url == master })
        assertTrue(result.all { it.hlsVariantOf == master })
    }

    @Test
    fun `the same quality from two copies of a playlist is one chip`() = runTest {
        val masterA = "https://cdn.example.com/video/abc.m3u8?sec=1"
        val masterB = "https://cdn.example.com/video/abc.m3u8?sec=2"
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia("https://cdn.example.com/video/abc/1080.m3u8?sec=1", pageUrl, height = 1080, hlsVariantOf = masterA),
                SniffedMedia("https://cdn.example.com/video/abc/480.m3u8?sec=1", pageUrl, height = 480, hlsVariantOf = masterA),
                SniffedMedia("https://cdn.example.com/video/abc/1080.m3u8?sec=2", pageUrl, height = 1080, hlsVariantOf = masterB),
                SniffedMedia("https://cdn.example.com/video/abc/480.m3u8?sec=2", pageUrl, height = 480, hlsVariantOf = masterB),
            ),
        )
        assertEquals(listOf(1080, 480), result.map { it.height })
    }

    @Test
    fun `a playlist never shows a byte size and a tiny mp4 is not a video`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                SniffedMedia("https://cdn.example.com/init.mp4", pageUrl, contentLength = 1_400L),
                SniffedMedia("https://cdn.example.com/full.mp4", pageUrl, contentLength = 9_000_000L),
                SniffedMedia("https://cdn.example.com/list.m3u8", pageUrl, contentLength = null),
            ),
        )

        assertEquals(2, result.size)
        assertFalse(result.any { it.url.endsWith("init.mp4") })
        assertNull(result.first { it.type == MediaType.HLS }.sizeBytes)
    }

    /** A wall of options is not a chooser. */
    @Test
    fun `the candidate list is capped`() = runTest {
        val many = (0 until 50).map { i ->
            SniffedMedia("https://cdn.example.com/video$i.mp4", pageUrl, height = i)
        }

        val result = extractor.extract(pageUrl, "", many)

        assertEquals(GenericExtractor.MAX_CANDIDATES, result.size)
        // The cap trims the tail, so the best quality survives.
        assertEquals(49, result.first().height)
    }
}
