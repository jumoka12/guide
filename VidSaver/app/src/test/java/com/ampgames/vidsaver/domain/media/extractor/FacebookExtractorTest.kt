package com.ampgames.vidsaver.domain.media.extractor

import com.ampgames.vidsaver.domain.media.SniffedMedia
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookExtractorTest {

    private val extractor = FacebookExtractor()
    private val pageUrl = "https://www.facebook.com/watch/?v=123"

    /** Encodes an `efg` parameter the way Facebook's CDN does. */
    private fun efg(tag: String): String {
        val json = """{"vencode_tag":"$tag","xpv_asset_id":42}"""
        return java.util.Base64.getEncoder().encodeToString(json.toByteArray())
    }

    private fun fbUrl(file: String, tag: String, size: Long? = null) = SniffedMedia(
        url = "https://video-lhr8-1.xx.fbcdn.net/v/t42.1790-2/$file?_nc_cat=1&efg=${efg(tag)}&oh=abc&oe=123",
        pageUrl = pageUrl,
        contentLength = size,
    )

    @Test
    fun `the encoding tag is read from efg`() {
        assertEquals("dash_h264-basic-gen2_720p", FacebookExtractor.encodingTag(fbUrl("a.mp4", "dash_h264-basic-gen2_720p").url))
        assertNull(FacebookExtractor.encodingTag("https://cdn.example.com/a.mp4"))
    }

    @Test
    fun `tracks are classified by their tag`() {
        with(FacebookExtractor.describe(fbUrl("a.mp4", "dash_ln_heaac_vbr3_audio").url)) {
            assertEquals(FacebookExtractor.Kind.AUDIO, kind)
        }
        with(FacebookExtractor.describe(fbUrl("a.mp4", "dash_vp9-basic-gen2_1080p").url)) {
            assertEquals(FacebookExtractor.Kind.VIDEO_ONLY, kind)
            assertEquals(1080, height)
        }
        with(FacebookExtractor.describe(fbUrl("a.mp4", "sve_hd").url)) {
            assertEquals(FacebookExtractor.Kind.MUXED, kind)
            assertEquals(720, height)
        }
        with(FacebookExtractor.describe("https://cdn.example.com/a.mp4")) {
            assertEquals(FacebookExtractor.Kind.UNKNOWN, kind)
        }
    }

    @Test
    fun `audio-only tracks are never offered`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                fbUrl("v.mp4", "dash_h264-basic-gen2_720p"),
                fbUrl("a1.mp4", "dash_ln_heaac_vbr3_audio"),
                fbUrl("a2.mp4", "dash_ln_heaac_64_frag_2_audio"),
            ),
        )
        assertEquals(1, result.size)
        assertEquals(720, result.single().height)
    }

    @Test
    fun `a muxed file wins and the silent tiers disappear`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                fbUrl("v720.mp4", "dash_h264-basic-gen2_720p"),
                fbUrl("v360.mp4", "dash_h264-basic-gen2_360p"),
                fbUrl("full.mp4", "sve_sd", size = 9_000_000L),
            ),
        )
        assertEquals(1, result.size)
        assertTrue(result.single().url.contains("full.mp4"))
        assertEquals(true, result.single().hasAudio)
    }

    @Test
    fun `without a muxed file the silent tiers are offered and marked`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                fbUrl("v360.mp4", "dash_h264-basic-gen2_360p", size = 1_000_000L),
                fbUrl("v720.mp4", "dash_h264-basic-gen2_720p", size = 3_000_000L),
                fbUrl("v1080.mp4", "dash_av1-basic-gen2_1080p", size = 5_000_000L),
            ),
        )
        assertEquals(listOf(1080, 720, 360), result.map { it.height })
        assertTrue(result.all { it.hasAudio == false })
        assertFalse(result.any { it.hasAudio == true })
    }

    @Test
    fun `the same tier seen twice is one chip`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(
                fbUrl("v720a.mp4", "dash_h264-basic-gen2_720p", size = 3_000_000L),
                fbUrl("v720b.mp4", "dash_vp9-basic-gen2_720p", size = 2_500_000L),
            ),
        )
        assertEquals(1, result.size)
        assertEquals(3_000_000L, result.single().sizeBytes)
    }

    @Test
    fun `files without an efg tag pass through untouched`() = runTest {
        val result = extractor.extract(
            pageUrl,
            "",
            listOf(SniffedMedia("https://video.xx.fbcdn.net/plain.mp4?oh=1&oe=2", pageUrl)),
        )
        assertEquals(1, result.size)
        assertNull(result.single().hasAudio)
    }
}
