package com.ampgames.vidsaver.domain.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun `all five engines from the spec are available`() {
        assertEquals(
            listOf("google", "bing", "duckduckgo", "yandex", "baidu"),
            SearchEngine.entries.map { it.id },
        )
    }

    @Test
    fun `queries are url encoded`() {
        val url = SearchEngine.GOOGLE.searchUrl("cat videos & dogs")
        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(url.endsWith("cat+videos+%26+dogs"))
    }

    @Test
    fun `non-ascii queries are encoded as utf-8`() {
        // "قطط" (Arabic for "cats") — RTL input must round-trip correctly.
        val url = SearchEngine.DUCKDUCKGO.searchUrl("قطط")
        assertEquals("https://duckduckgo.com/?q=%D9%82%D8%B7%D8%B7", url)
    }

    @Test
    fun `unknown or missing ids fall back to the default`() {
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromId(null))
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromId(""))
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromId("altavista"))
    }

    @Test
    fun `id lookup is case insensitive`() {
        assertEquals(SearchEngine.BING, SearchEngine.fromId("BING"))
        assertEquals(SearchEngine.YANDEX, SearchEngine.fromId("Yandex"))
    }
}
