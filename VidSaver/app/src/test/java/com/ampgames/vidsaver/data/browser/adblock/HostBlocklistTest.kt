package com.ampgames.vidsaver.data.browser.adblock

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostBlocklistTest {

    private fun blocklist(vararg lines: String) = HostBlocklist.parse(lines.asSequence())

    @Test
    fun `hosts-file syntax is parsed`() {
        val list = blocklist(
            "# a comment",
            "",
            "0.0.0.0 ads.example.com",
            "127.0.0.1\ttracker.example.net",
            "bare.example.org",
            "0.0.0.0 trailing.example.com # inline comment",
        )
        assertEquals(4, list.size)
        assertTrue(list.blocksHost("ads.example.com"))
        assertTrue(list.blocksHost("tracker.example.net"))
        assertTrue(list.blocksHost("bare.example.org"))
        assertTrue(list.blocksHost("trailing.example.com"))
    }

    @Test
    fun `local and non-domain entries are skipped`() {
        val list = blocklist(
            "127.0.0.1 localhost",
            "255.255.255.255 broadcasthost",
            "::1 ip6-localhost",
            "0.0.0.0 0.0.0.0",
            "nodots",
        )
        assertEquals(0, list.size)
    }

    @Test
    fun `subdomains of a blocked host are blocked`() {
        val list = blocklist("0.0.0.0 ads.example.com")
        assertTrue(list.blocksHost("ads.example.com"))
        assertTrue(list.blocksHost("a.ads.example.com"))
        assertTrue(list.blocksHost("a.b.ads.example.com"))
    }

    @Test
    fun `parent domains of a blocked host are not blocked`() {
        val list = blocklist("0.0.0.0 ads.example.com")
        assertFalse(list.blocksHost("example.com"))
        assertFalse(list.blocksHost("www.example.com"))
        assertFalse(list.blocksHost("notads.example.com"))
    }

    @Test
    fun `matching is case insensitive and ignores a trailing dot`() {
        val list = blocklist("0.0.0.0 Ads.Example.COM")
        assertTrue(list.blocksHost("ADS.EXAMPLE.COM"))
        assertTrue(list.blocksHost("ads.example.com."))
    }

    @Test
    fun `blocksUrl resolves the host first`() {
        val list = blocklist("0.0.0.0 ads.example.com")
        assertTrue(list.blocksUrl("https://ads.example.com/pixel.gif?x=1"))
        assertFalse(list.blocksUrl("https://example.com/index.html"))
        assertFalse(list.blocksUrl("about:blank"))
    }

    @Test
    fun `an empty blocklist blocks nothing`() {
        assertFalse(HostBlocklist.EMPTY.blocksHost("ads.example.com"))
        assertFalse(HostBlocklist.EMPTY.blocksHost(null))
    }

    /** Guards the shipped asset: a malformed list silently disables ad blocking. */
    @Test
    fun `the shipped asset parses into a usable blocklist`() {
        val file = listOf(
            File("src/main/assets/hosts_blocklist.txt"),
            File("app/src/main/assets/hosts_blocklist.txt"),
        ).firstOrNull { it.exists() } ?: error("hosts_blocklist.txt not found")

        val list = file.bufferedReader().use { HostBlocklist.parse(it.lineSequence()) }

        assertTrue("asset should contain entries", list.size > 10)
        assertTrue(list.blocksHost("doubleclick.net"))
        assertTrue(list.blocksHost("ad.doubleclick.net"))
        assertFalse("comments must not become hosts", list.blocksHost("vidsaver"))
    }
}
