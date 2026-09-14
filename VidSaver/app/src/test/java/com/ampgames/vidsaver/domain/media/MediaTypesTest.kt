package com.ampgames.vidsaver.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTypesTest {

    @Test
    fun `video and hls mime types are recognised`() {
        assertTrue(MediaTypes.isVideoMime("video/mp4"))
        assertTrue(MediaTypes.isVideoMime("VIDEO/MP4"))
        assertTrue(MediaTypes.isVideoMime("video/mp4; codecs=\"avc1.42E01E\""))
        assertTrue(MediaTypes.isVideoMime("application/vnd.apple.mpegurl"))
        assertTrue(MediaTypes.isVideoMime("application/x-mpegURL"))

        assertFalse(MediaTypes.isVideoMime("text/html"))
        assertFalse(MediaTypes.isVideoMime("image/jpeg"))
        assertFalse(MediaTypes.isVideoMime(null))
    }

    @Test
    fun `hls is distinguished from progressive`() {
        assertTrue(MediaTypes.isHlsMime("application/x-mpegurl"))
        assertFalse(MediaTypes.isHlsMime("video/mp4"))
        assertTrue(MediaTypes.isHlsUrl("https://x.com/master.m3u8"))
        assertFalse(MediaTypes.isHlsUrl("https://x.com/movie.mp4"))
    }

    @Test
    fun `segments are identified so they are never offered as downloads`() {
        assertTrue(MediaTypes.isSegmentUrl("https://x.com/seg1.ts"))
        assertTrue(MediaTypes.isSegmentUrl("https://x.com/seg1.m4s"))
        assertFalse(MediaTypes.isSegmentUrl("https://x.com/movie.mp4"))
    }

    @Test
    fun `typeOf prefers the mime type and falls back to the url`() {
        assertEquals(MediaType.HLS, MediaTypes.typeOf("https://x.com/a", "application/x-mpegurl"))
        assertEquals(MediaType.HLS, MediaTypes.typeOf("https://x.com/a.m3u8", null))
        assertEquals(MediaType.PROGRESSIVE, MediaTypes.typeOf("https://x.com/a", "video/webm"))
        assertEquals(MediaType.PROGRESSIVE, MediaTypes.typeOf("https://x.com/a.mp4", null))
        assertNull(MediaTypes.typeOf("https://x.com/a.css", "text/css"))
        assertNull(MediaTypes.typeOf("https://x.com/a", null))
    }

    @Test
    fun `hls always saves as mp4 because it is remuxed`() {
        assertEquals("mp4", MediaTypes.fileExtensionFor(MediaType.HLS, "https://x.com/a.m3u8", null))
    }

    @Test
    fun `progressive keeps a known container and otherwise falls back to mp4`() {
        assertEquals(
            "webm",
            MediaTypes.fileExtensionFor(MediaType.PROGRESSIVE, "https://x.com/a.webm", null),
        )
        assertEquals(
            "webm",
            MediaTypes.fileExtensionFor(MediaType.PROGRESSIVE, "https://x.com/stream", "video/webm"),
        )
        assertEquals(
            "mp4",
            MediaTypes.fileExtensionFor(MediaType.PROGRESSIVE, "https://x.com/stream", null),
        )
    }
}
