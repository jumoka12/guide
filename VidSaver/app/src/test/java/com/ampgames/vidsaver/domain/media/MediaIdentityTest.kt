package com.ampgames.vidsaver.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for the "one Facebook link offered 56 downloads" bug.
 *
 * URLs here are shaped like the real thing: a Meta CDN path plus signing
 * parameters that are re-issued on every request, plus the byte range that
 * slices one video into many requests.
 */
class MediaIdentityTest {

    private fun fbChunk(file: String, start: Long, end: Long, signature: String) =
        "https://video-lhr8-1.xx.fbcdn.net/v/t42.1790-2/$file" +
            "?_nc_cat=103&ccb=1-7&_nc_sid=5f0432&efg=eyJ2ZW5jb2RlX3RhZyI6InN2ZV9" +
            "&_nc_ohc=$signature&_nc_ht=video-lhr8-1.xx&oh=00_AY$signature&oe=6512ABCD" +
            "&bytestart=$start&byteend=$end"

    // ------------------------------------------------------------- range detection

    @Test
    fun `a byte-ranged request is recognised as a slice`() {
        assertTrue(MediaIdentity.isRangedRequest(fbChunk("clip.mp4", 0, 261771, "aaa")))
        assertTrue(MediaIdentity.isRangedRequest("https://cdn.example.com/v.mp4?range=0-100"))
    }

    @Test
    fun `a plain url is not a slice`() {
        assertFalse(MediaIdentity.isRangedRequest("https://cdn.example.com/v.mp4"))
        assertFalse(MediaIdentity.isRangedRequest("https://cdn.example.com/v.mp4?quality=hd"))
    }

    // ------------------------------------------------------------ canonicalisation

    @Test
    fun `canonicalising drops the range but keeps the signature`() {
        val canonical = MediaIdentity.canonicalUrl(fbChunk("clip.mp4", 0, 261771, "aaa"))

        assertFalse("range must go", canonical.contains("bytestart"))
        assertFalse("range must go", canonical.contains("byteend"))
        // Strip the signing parameters and the CDN answers 403.
        assertTrue("signature must stay", canonical.contains("_nc_ohc=aaa"))
        assertTrue("signature must stay", canonical.contains("oe=6512ABCD"))
    }

    @Test
    fun `every chunk of one video canonicalises to the same url`() {
        val chunks = listOf(
            fbChunk("clip.mp4", 0, 261771, "aaa"),
            fbChunk("clip.mp4", 261772, 523543, "aaa"),
            fbChunk("clip.mp4", 523544, 785315, "aaa"),
        )
        assertEquals(1, chunks.map { MediaIdentity.canonicalUrl(it) }.toSet().size)
    }

    @Test
    fun `a url without a query survives canonicalisation unchanged`() {
        val url = "https://cdn.example.com/video.mp4"
        assertEquals(url, MediaIdentity.canonicalUrl(url))
    }

    // ------------------------------------------------------------------- identity

    /** The core of the bug: the same clip, re-signed, must count once. */
    @Test
    fun `the same video re-signed has one identity`() {
        val first = fbChunk("clip.mp4", 0, 261771, "signatureONE")
        val later = fbChunk("clip.mp4", 900000, 1100000, "signatureTWO")

        assertEquals(MediaIdentity.contentKey(first), MediaIdentity.contentKey(later))
    }

    @Test
    fun `different videos keep different identities`() {
        val a = fbChunk("clipA.mp4", 0, 100, "aaa")
        val b = fbChunk("clipB.mp4", 0, 100, "aaa")

        assertNotEquals(MediaIdentity.contentKey(a), MediaIdentity.contentKey(b))
    }

    @Test
    fun `a parameter that selects content still separates videos`() {
        val sd = "https://cdn.example.com/play?id=42&quality=sd"
        val hd = "https://cdn.example.com/play?id=42&quality=hd"
        val other = "https://cdn.example.com/play?id=43&quality=sd"

        assertNotEquals(MediaIdentity.contentKey(sd), MediaIdentity.contentKey(hd))
        assertNotEquals(MediaIdentity.contentKey(sd), MediaIdentity.contentKey(other))
    }

    @Test
    fun `identity ignores parameter order`() {
        val a = "https://cdn.example.com/v.mp4?id=1&quality=hd"
        val b = "https://cdn.example.com/v.mp4?quality=hd&id=1"

        assertEquals(MediaIdentity.contentKey(a), MediaIdentity.contentKey(b))
    }

    @Test
    fun `identity ignores the scheme but not the host`() {
        assertEquals(
            MediaIdentity.contentKey("https://cdn.example.com/v.mp4"),
            MediaIdentity.contentKey("http://cdn.example.com/v.mp4"),
        )
        assertNotEquals(
            MediaIdentity.contentKey("https://a.example.com/v.mp4"),
            MediaIdentity.contentKey("https://b.example.com/v.mp4"),
        )
    }

    @Test
    fun `a malformed url does not throw`() {
        MediaIdentity.contentKey("")
        MediaIdentity.contentKey("not a url")
        MediaIdentity.canonicalUrl("???")
    }
}
