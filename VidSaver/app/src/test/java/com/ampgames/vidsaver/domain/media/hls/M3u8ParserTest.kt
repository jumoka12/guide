package com.ampgames.vidsaver.domain.media.hls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3u8ParserTest {

    private val base = "https://cdn.example.com/video/master.m3u8"

    @Test
    fun `master playlist variants are parsed and sorted best first`() {
        val content = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.42c01e,mp4a.40.2"
            360/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            1080/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x720
            720/index.m3u8
        """.trimIndent()

        assertTrue(M3u8Parser.isMasterPlaylist(content))
        val variants = M3u8Parser.parseMaster(content, base)

        assertEquals(3, variants.size)
        assertEquals(listOf(1080, 720, 360), variants.map { it.height })
        assertEquals("https://cdn.example.com/video/1080/index.m3u8", variants[0].url)
        assertEquals(3_000_000L, variants[0].bandwidth)
        assertEquals(1920, variants[0].width)
        assertEquals("avc1.640028,mp4a.40.2", variants[0].codecs)
    }

    @Test
    fun `media playlist segments are parsed with durations and absolute urls`() {
        val content = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.006,
            seg0.ts
            #EXTINF:5.994,
            seg1.ts
            #EXTINF:3.200,
            https://other.example.com/seg2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = M3u8Parser.parseMedia(content, "https://cdn.example.com/video/720/index.m3u8")

        assertEquals(3, playlist.segments.size)
        assertEquals("https://cdn.example.com/video/720/seg0.ts", playlist.segments[0].url)
        assertEquals(6.006, playlist.segments[0].durationSeconds, 0.0001)
        assertEquals("https://other.example.com/seg2.ts", playlist.segments[2].url)
        assertEquals(15.2, playlist.totalDurationSeconds, 0.0001)
        assertFalse(playlist.isLive)
        assertFalse(playlist.isEncrypted)
    }

    @Test
    fun `an EXT-X-KEY with a real method marks the playlist encrypted`() {
        val content = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/k1",IV=0x0123
            #EXTINF:6.0,
            seg0.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = M3u8Parser.parseMedia(content, base)
        assertTrue(playlist.isEncrypted)
        assertEquals("AES-128", playlist.encryptionMethod)
    }

    @Test
    fun `METHOD=NONE is not treated as encryption`() {
        val content = """
            #EXTM3U
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:6.0,
            seg0.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertFalse(M3u8Parser.parseMedia(content, base).isEncrypted)
    }

    @Test
    fun `a playlist without ENDLIST is live`() {
        val content = """
            #EXTM3U
            #EXTINF:6.0,
            seg0.ts
        """.trimIndent()

        assertTrue(M3u8Parser.parseMedia(content, base).isLive)
    }

    @Test
    fun `an EXT-X-MAP init segment is captured`() {
        val content = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            seg0.m4s
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = M3u8Parser.parseMedia(content, base)
        assertEquals("https://cdn.example.com/video/init.mp4", playlist.initSegmentUrl)
    }

    @Test
    fun `attribute parsing respects quoted commas`() {
        val attributes = M3u8Parser.parseAttributes(
            """BANDWIDTH=3000000,CODECS="avc1.640028,mp4a.40.2",RESOLUTION=1920x1080""",
        )
        assertEquals("3000000", attributes["BANDWIDTH"])
        assertEquals("avc1.640028,mp4a.40.2", attributes["CODECS"])
        assertEquals("1920x1080", attributes["RESOLUTION"])
    }

    @Test
    fun `a non-master playlist yields no variants`() {
        val content = "#EXTM3U\n#EXTINF:6.0,\nseg0.ts\n#EXT-X-ENDLIST"
        assertFalse(M3u8Parser.isMasterPlaylist(content))
        assertTrue(M3u8Parser.parseMaster(content, base).isEmpty())
    }

    @Test
    fun `empty input parses to an empty playlist rather than throwing`() {
        val playlist = M3u8Parser.parseMedia("", base)
        assertTrue(playlist.segments.isEmpty())
        assertNull(playlist.initSegmentUrl)
    }
}
