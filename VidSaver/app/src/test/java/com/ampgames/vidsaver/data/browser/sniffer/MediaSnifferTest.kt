package com.ampgames.vidsaver.data.browser.sniffer

import com.ampgames.vidsaver.domain.media.SniffSource
import com.ampgames.vidsaver.domain.media.SniffedMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The sniffer against a real HTTP server: what a page's requests turn into.
 * Runs on the JVM with a stubbed CookieManager; everything else is real.
 */
class MediaSnifferTest {

    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sniffer: MediaSniffer
    private lateinit var pageUrl: String

    @Before
    fun setUp() {
        server.start()
        pageUrl = server.url("/watch/1").toString()
        sniffer = MediaSniffer(OkHttpClient(), Dispatchers.IO, scope)
        sniffer.onNavigationStarted(pageUrl)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private suspend fun await(predicate: (List<SniffedMedia>) -> Boolean): List<SniffedMedia> =
        withTimeout(5_000) { sniffer.media.first(predicate) }

    @Test
    fun `an ambiguous url that turns out to be video is recorded with its size`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 0-0/12345678")
                .setBody("x"),
        )
        val url = server.url("/stream?id=7").toString()

        sniffer.onResourceRequested(url, emptyMap())

        val found = await { it.isNotEmpty() }.single()
        assertEquals(url, found.url)
        assertEquals("video/mp4", found.mimeType)
        assertEquals(12_345_678L, found.contentLength)
        assertEquals(SniffSource.NETWORK, found.source)
    }

    @Test
    fun `a master playlist is expanded into its variants and hidden behind them`() = runBlocking {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            1080.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480,CODECS="avc1.4d401f,mp4a.40.2"
            480.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=96000,CODECS="mp4a.40.2"
            audio.m3u8
        """.trimIndent()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val playlist = MockResponse().setHeader("Content-Type", "application/vnd.apple.mpegurl")
                return if (request.getHeader("Range") != null) {
                    playlist.setResponseCode(206).setBody("#")
                } else {
                    playlist.setBody(master)
                }
            }
        }
        val masterUrl = server.url("/video/abc.m3u8").toString()

        sniffer.onResourceRequested(masterUrl, emptyMap())

        val media = await { list -> list.count { it.hlsVariantOf != null } == 3 }
        val masterEntry = media.first { it.url == masterUrl }
        assertNull("a playlist's byte size is not the stream's size", masterEntry.contentLength)

        val variants = media.filter { it.hlsVariantOf == masterUrl }
        assertEquals(listOf(1080, 480, null), variants.map { it.height })
        assertTrue(variants.first { it.url.endsWith("audio.m3u8") }.audioOnly)
        assertTrue(variants.first { it.url.endsWith("1080.m3u8") }.url.startsWith(server.url("/video/").toString()))
    }

    @Test
    fun `a video the page declares is recorded with its playback state and probed for size`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 0-0/777777")
                .setBody("x"),
        )
        val url = server.url("/clip.mp4").toString()

        sniffer.onDomMediaFound(
            listOf(DomMediaReport(url = url, playing = true, active = true, visible = 0.9, poster = "p.jpg")),
        )

        val dom = await { it.isNotEmpty() }.first { it.source == SniffSource.DOM }
        assertTrue(dom.activity.isPlaying)
        assertEquals(0.9, dom.activity.visibleFraction, 0.0001)

        // The probe lands as a network sighting; the extractor merges the two.
        val network = await { list -> list.any { it.source == SniffSource.NETWORK } }
            .first { it.source == SniffSource.NETWORK }
        assertEquals(777_777L, network.contentLength)
    }

    @Test
    fun `an extension-less url fetched in byte ranges is treated as media`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 0-0/9999999")
                .setBody("x"),
        )
        // The shape TikTok uses: no extension, a signed query, fetched in ranges.
        val url = server.url("/video/tos/useast2a/abc123/?a=1&mime_type=video_mp4").toString()

        sniffer.onResourceRequested(url, mapOf("Range" to "bytes=0-1048575"), "GET")

        // Offered at once without a size, then the probe fills the size in.
        val found = await { list -> list.any { it.contentLength != null } }.single()
        assertEquals(9_999_999L, found.contentLength)
        assertEquals("video/mp4", found.mimeType)
    }

    @Test
    fun `api calls are never probed and never spend the budget`() = runBlocking {
        sniffer.onResourceRequested(server.url("/api/recommend/item_list/?count=8").toString(), emptyMap(), "GET")
        sniffer.onResourceRequested(server.url("/graphql/query").toString(), emptyMap(), "POST")
        sniffer.onResourceRequested(server.url("/things?id=1").toString(), mapOf("Accept" to "application/json"), "GET")
        sniffer.onResourceRequested(server.url("/upload").toString(), emptyMap(), "POST")

        Thread.sleep(300)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `static assets are never probed`() = runBlocking {
        sniffer.onResourceRequested(server.url("/app.js").toString(), emptyMap())
        sniffer.onResourceRequested(server.url("/style.css").toString(), emptyMap())
        sniffer.onResourceRequested(server.url("/photo.jpg").toString(), emptyMap())

        // Give any wrongly launched probe time to arrive.
        Thread.sleep(300)
        assertEquals(0, server.requestCount)
        assertTrue(sniffer.media.value.isEmpty())
    }
}
