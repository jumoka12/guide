package com.ampgames.vidsaver.domain.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Google Play policy guard. If any of these start failing, the app is no longer
 * compliant — fix the code, not the test.
 */
class UnsupportedDomainsTest {

    @Test
    fun `the four required domains are blocked`() {
        listOf(
            "https://youtube.com/watch?v=abc",
            "https://youtu.be/abc",
            "https://m.youtube.com/watch?v=abc",
            "https://music.youtube.com/watch?v=abc",
        ).forEach { url ->
            assertTrue("$url must be blocked", UnsupportedDomains.isUnsupported(url))
        }
    }

    @Test
    fun `subdomains and the media cdn are blocked too`() {
        listOf(
            "https://www.youtube.com/watch?v=abc",
            "https://gaming.youtube.com/",
            "https://www.youtube-nocookie.com/embed/abc",
            "https://r5---sn-abc.googlevideo.com/videoplayback?x=1",
            "https://i.ytimg.com/vi/abc/hq.jpg",
        ).forEach { url ->
            assertTrue("$url must be blocked", UnsupportedDomains.isUnsupported(url))
        }
    }

    @Test
    fun `lookalike domains are not blocked`() {
        listOf(
            "https://notyoutube.com/",
            "https://youtube.com.evil.example/",
            "https://myyoutu.be.example.com/",
            "https://tiktok.com/@user/video/1",
            "https://vimeo.com/12345",
        ).forEach { url ->
            assertFalse("$url must not be blocked", UnsupportedDomains.isUnsupported(url))
        }
    }

    @Test
    fun `a url with no host is not treated as blocked`() {
        assertFalse(UnsupportedDomains.isUnsupported("about:blank"))
        assertFalse(UnsupportedDomains.isUnsupported(""))
    }
}
