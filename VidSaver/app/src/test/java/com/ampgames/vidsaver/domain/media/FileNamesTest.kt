package com.ampgames.vidsaver.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
