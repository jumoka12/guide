package com.ampgames.vidsaver.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlsTest {

    @Test
    fun `host is extracted lowercased without port or userinfo`() {
        assertEquals("example.com", Urls.host("https://example.com/path"))
        assertEquals("example.com", Urls.host("HTTPS://Example.COM/path"))
        assertEquals("example.com", Urls.host("https://example.com:8443/path"))
        assertEquals("example.com", Urls.host("https://user:pw@example.com/path"))
        assertEquals("example.com", Urls.host("https://example.com./path"))
        assertEquals("example.com", Urls.host("//example.com/path"))
        assertEquals("example.com", Urls.host("example.com/path"))
    }

    @Test
    fun `host of a url without an authority is null`() {
        assertNull(Urls.host("data:video/mp4;base64,AAAA"))
        assertNull(Urls.host("about:blank"))
        assertNull(Urls.host("mailto:a@b.com"))
        assertNull(Urls.host(""))
        assertNull(Urls.host("   "))
    }

    @Test
    fun `ipv6 literals keep their brackets and lose the port`() {
        assertEquals("[::1]", Urls.host("http://[::1]:8080/x"))
        assertEquals(listOf("[::1]"), Urls.hostAndParents("[::1]"))
    }

    @Test
    fun `path excludes query and fragment`() {
        assertEquals("/a/b", Urls.path("https://x.com/a/b?q=1#frag"))
        assertEquals("/", Urls.path("https://x.com"))
        assertEquals("/", Urls.path("https://x.com/"))
    }

    @Test
    fun `file extension comes from the path not the query`() {
        assertEquals("mp4", Urls.fileExtension("https://x.com/a/video.mp4"))
        assertEquals("m3u8", Urls.fileExtension("https://x.com/a/master.m3u8?token=abc.mp4"))
        assertEquals("mp4", Urls.fileExtension("https://x.com/a/VIDEO.MP4"))
        assertNull(Urls.fileExtension("https://x.com/a/video"))
    }

    @Test
    fun `hostAndParents walks up the domain`() {
        assertEquals(
            listOf("a.b.example.com", "b.example.com", "example.com", "com"),
            Urls.hostAndParents("a.b.example.com"),
        )
        assertEquals(listOf("localhost"), Urls.hostAndParents("localhost"))
    }

    @Test
    fun `hostMatchesDomain matches the domain and its subdomains only`() {
        assertTrue(Urls.hostMatchesDomain("example.com", "example.com"))
        assertTrue(Urls.hostMatchesDomain("m.example.com", "example.com"))
        assertTrue(Urls.hostMatchesDomain("a.b.example.com", "example.com"))
        assertFalse(Urls.hostMatchesDomain("notexample.com", "example.com"))
        assertFalse(Urls.hostMatchesDomain("example.com.evil.net", "example.com"))
        assertFalse(Urls.hostMatchesDomain("example.com", "m.example.com"))
    }

    @Test
    fun `looksLikeUrl distinguishes addresses from search terms`() {
        assertTrue(Urls.looksLikeUrl("example.com"))
        assertTrue(Urls.looksLikeUrl("https://example.com"))
        assertTrue(Urls.looksLikeUrl("example.com/path?q=1"))
        assertTrue(Urls.looksLikeUrl("localhost:8080"))

        assertFalse(Urls.looksLikeUrl("cat videos"))
        assertFalse(Urls.looksLikeUrl("how to tie a tie"))
        assertFalse(Urls.looksLikeUrl("example"))
        assertFalse(Urls.looksLikeUrl(""))
        // A trailing numeric "TLD" is a version number, not a domain.
        assertFalse(Urls.looksLikeUrl("version.2"))
    }

    @Test
    fun `withScheme only adds a scheme when one is missing`() {
        assertEquals("https://example.com", Urls.withScheme("example.com"))
        assertEquals("http://example.com", Urls.withScheme("http://example.com"))
        assertEquals("https://example.com", Urls.withScheme("https://example.com"))
    }
}
