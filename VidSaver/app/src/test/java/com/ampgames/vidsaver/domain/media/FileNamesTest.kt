package com.ampgames.vidsaver.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    @Test
    fun `path separators and reserved characters are removed`() {
        val name = FileNames.sanitize("""a/b\c:d*e?f"g<h>i|j""")
        listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|').forEach {
            assertFalse("$it should be gone from $name", name.contains(it))
        }
    }

    @Test
    fun `control characters are stripped`() {
        // \u0000 NUL and \u0007 BEL must not reach the file system.
        assertEquals("clipname", FileNames.sanitize("clip\u0000\u0007name"))
        assertEquals("clipname", FileNames.sanitize("clip\u007Fname"))
    }

    @Test
    fun `spaces and hyphens survive because they are legal and readable`() {
        assertEquals("My clip - part 2", FileNames.sanitize("My clip - part 2"))
    }

    @Test
    fun `runs of whitespace collapse and edges are trimmed`() {
        assertEquals("a b", FileNames.sanitize("  a   \t b .. "))
    }

    @Test
    fun `an empty or fully stripped name falls back to a default`() {
        assertEquals("video", FileNames.sanitize(""))
        assertEquals("video", FileNames.sanitize("///"))
        assertEquals("video", FileNames.sanitize("   "))
    }

    @Test
    fun `long names are bounded`() {
        val name = FileNames.sanitize("x".repeat(500))
        assertTrue(name.length <= 60)
    }

    @Test
    fun `a candidate name uses the title and resolution`() {
        assertEquals(
            "My clip 1080p.mp4",
            FileNames.forCandidate("My clip", "https://example.com/p", "mp4", "1080p"),
        )
    }

    @Test
    fun `a missing title falls back to the site host`() {
        assertEquals(
            "example.com.mp4",
            FileNames.forCandidate(null, "https://www.example.com/p", "mp4"),
        )
        assertEquals(
            "example.com.mp4",
            FileNames.forCandidate("   ", "https://example.com/p", "mp4"),
        )
    }

    // --- cleanTitle: a caption is not a file name ------------------------------

    @Test
    fun `hashtags are stripped from a title`() {
        assertEquals(
            "Sunset over the bay",
            FileNames.cleanTitle("Sunset over the bay #fyp #viral #sunset", "https://www.tiktok.com/@a/video/1"),
        )
    }

    @Test
    fun `the site's own name is dropped from the end of a title`() {
        assertEquals("Funny cat", FileNames.cleanTitle("Funny cat | Facebook", "https://m.facebook.com/watch/?v=1"))
        assertEquals("Funny cat", FileNames.cleanTitle("Funny cat - TikTok", "https://www.tiktok.com/@a/video/1"))
        assertEquals("Funny cat", FileNames.cleanTitle("Funny cat on Instagram", "https://www.instagram.com/reel/x/"))
    }

    @Test
    fun `a suffix that is not the site name is kept`() {
        assertEquals(
            "Lecture 3 - Part 2",
            FileNames.cleanTitle("Lecture 3 - Part 2", "https://vimeo.com/123"),
        )
        // The site name only counts at the end.
        assertEquals(
            "Facebook is down again",
            FileNames.cleanTitle("Facebook is down again", "https://m.facebook.com/watch/?v=1"),
        )
    }

    @Test
    fun `a title made only of hashtags is treated as missing`() {
        assertNull(FileNames.cleanTitle("#fyp #viral #trending", "https://www.tiktok.com/@a/video/1"))
        assertNull(FileNames.cleanTitle("   ", "https://example.com/"))
        assertNull(FileNames.cleanTitle(null, "https://example.com/"))
        assertEquals(
            "tiktok.com.mp4",
            FileNames.forCandidate("#fyp #viral", "https://www.tiktok.com/@a/video/1", "mp4"),
        )
    }

    @Test
    fun `a missing title falls back to the site and post id before the host`() {
        assertEquals("tiktok_1789411373000", FileNames.siteIdName("https://www.tiktok.com/@user/video/1789411373000"))
        assertEquals("facebook_4194402202515", FileNames.siteIdName("https://m.facebook.com/watch/?v=4194402202515"))
        assertEquals("instagram_C9xYz_ab", FileNames.siteIdName("https://www.instagram.com/reel/C9xYz_ab/"))
        assertEquals("x_1700000000000000000", FileNames.siteIdName("https://x.com/user/status/1700000000000000000"))
        assertNull(FileNames.siteIdName("https://example.com/about"))
        assertEquals(
            "tiktok_1789411373000.mp4",
            FileNames.forCandidate("#fyp", "https://www.tiktok.com/@user/video/1789411373000", "mp4"),
        )
    }

    @Test
    fun `a feed page's slogan is not a title`() {
        assertNull(FileNames.cleanTitle("TikTok - Make Your Day", "https://www.tiktok.com/"))
        assertNull(FileNames.cleanTitle("Facebook", "https://www.facebook.com/reel/1"))
        assertNull(FileNames.cleanTitle("For You", "https://www.tiktok.com/foryou"))
        // A leading site name is stripped, the rest kept.
        assertEquals("Khan baba lifts a car", FileNames.cleanTitle("TikTok - Khan baba lifts a car", "https://www.tiktok.com/"))
    }

    @Test
    fun `a feed clip with no title and no post id is named by site and moment`() {
        val at = 1_757_884_245_000L
        val name = FileNames.forCandidate("TikTok - Make Your Day", "https://www.tiktok.com/", "mp4", detectedAt = at)
        assertTrue("got $name", Regex("""tiktok_\d{8}_\d{6}\.mp4""").matches(name))
        assertEquals("tiktok.com.mp4", FileNames.forCandidate(null, "https://www.tiktok.com/", "mp4"))
    }

    @Test
    fun `deduplicate appends a counter before the extension`() {
        val taken = setOf("clip.mp4", "clip (2).mp4")
        assertEquals("clip (3).mp4", FileNames.deduplicate("clip.mp4", taken))
        assertEquals("other.mp4", FileNames.deduplicate("other.mp4", taken))
    }

    @Test
    fun `deduplicate handles names without an extension`() {
        assertEquals("clip (2)", FileNames.deduplicate("clip", setOf("clip")))
    }
}
