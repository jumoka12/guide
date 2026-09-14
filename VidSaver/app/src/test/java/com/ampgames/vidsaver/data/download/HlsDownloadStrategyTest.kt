package com.ampgames.vidsaver.data.download

import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.MediaType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.Dispatcher
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HlsDownloadStrategyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var strategy: HlsDownloadStrategy
    private val progress = mutableListOf<DownloadProgress>()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        strategy = HlsDownloadStrategy(
            client = OkHttpClient(),
            remuxer = PassthroughRemuxer(),
            ioDispatcher = Dispatchers.IO,
        )
        progress.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun candidate(path: String, height: Int? = null) = MediaCandidate(
        url = server.url(path).toString(),
        pageUrl = "https://example.com/watch",
        type = MediaType.HLS,
        headers = mapOf("Referer" to "https://example.com/watch"),
        height = height,
        suggestedFileName = "clip.mp4",
    )

    private fun target() = File(tempFolder.root, "clip.mp4")

    /** Serves a fixed map of path -> response body. */
    private fun serve(routes: Map<String, String>, binary: Map<String, ByteArray> = emptyMap()) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                binary[path]?.let { bytes ->
                    return MockResponse().setBody(Buffer().write(bytes))
                }
                routes[path]?.let { return MockResponse().setBody(it) }
                return MockResponse().setResponseCode(404)
            }
        }
    }

    @Test
    fun `segments are downloaded in order and concatenated`() = runTest {
        serve(
            routes = mapOf(
                "/index.m3u8" to """
                    #EXTM3U
                    #EXTINF:2.0,
                    a.ts
                    #EXTINF:2.0,
                    b.ts
                    #EXTINF:2.0,
                    c.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
            ),
            binary = mapOf(
                "/a.ts" to "AAA".toByteArray(),
                "/b.ts" to "BBB".toByteArray(),
                "/c.ts" to "CCC".toByteArray(),
            ),
        )

        val result = strategy.download(candidate("/index.m3u8"), target()) { progress += it }

        val file = result.getOrThrow()
        assertEquals("AAABBBCCC", file.readText())
        assertEquals(9L, progress.last().bytesDownloaded)
    }

    @Test
    fun `a master playlist is followed to the best variant by default`() = runTest {
        serve(
            routes = mapOf(
                "/master.m3u8" to """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
                    low.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080
                    high.m3u8
                """.trimIndent(),
                "/high.m3u8" to "#EXTM3U\n#EXTINF:2.0,\nhigh0.ts\n#EXT-X-ENDLIST",
                "/low.m3u8" to "#EXTM3U\n#EXTINF:2.0,\nlow0.ts\n#EXT-X-ENDLIST",
            ),
            binary = mapOf(
                "/high0.ts" to "HIGH".toByteArray(),
                "/low0.ts" to "LOW".toByteArray(),
            ),
        )

        val file = strategy.download(candidate("/master.m3u8"), target()) {}.getOrThrow()
        assertEquals("HIGH", file.readText())
    }

    @Test
    fun `a requested resolution selects the closest variant`() = runTest {
        serve(
            routes = mapOf(
                "/master.m3u8" to """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
                    low.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080
                    high.m3u8
                """.trimIndent(),
                "/high.m3u8" to "#EXTM3U\n#EXTINF:2.0,\nhigh0.ts\n#EXT-X-ENDLIST",
                "/low.m3u8" to "#EXTM3U\n#EXTINF:2.0,\nlow0.ts\n#EXT-X-ENDLIST",
            ),
            binary = mapOf(
                "/high0.ts" to "HIGH".toByteArray(),
                "/low0.ts" to "LOW".toByteArray(),
            ),
        )

        val file = strategy.download(candidate("/master.m3u8", height = 360), target()) {}.getOrThrow()
        assertEquals("LOW", file.readText())
    }

    @Test
    fun `an encrypted playlist is refused, not decrypted`() = runTest {
        serve(
            routes = mapOf(
                "/index.m3u8" to """
                    #EXTM3U
                    #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/k"
                    #EXTINF:2.0,
                    a.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
            ),
        )

        val error = strategy.download(candidate("/index.m3u8"), target()) {}.exceptionOrNull()

        assertTrue(error is DownloadError.Encrypted)
        assertEquals("AES-128", (error as DownloadError.Encrypted).method)
        // Nothing was fetched beyond the playlist itself.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a live playlist is refused`() = runTest {
        serve(routes = mapOf("/index.m3u8" to "#EXTM3U\n#EXTINF:2.0,\na.ts"))

        val error = strategy.download(candidate("/index.m3u8"), target()) {}.exceptionOrNull()
        assertTrue(error is DownloadError.LiveStream)
    }

    @Test
    fun `a failing segment surfaces an http error`() = runTest {
        serve(
            routes = mapOf(
                "/index.m3u8" to "#EXTM3U\n#EXTINF:2.0,\nmissing.ts\n#EXT-X-ENDLIST",
            ),
        )

        val error = strategy.download(candidate("/index.m3u8"), target()) {}.exceptionOrNull()
        assertTrue(error is DownloadError.Http)
        assertEquals(404, (error as DownloadError.Http).code)
    }

    @Test
    fun `an empty playlist is reported as unsupported`() = runTest {
        serve(routes = mapOf("/index.m3u8" to "#EXTM3U\n#EXT-X-ENDLIST"))

        val error = strategy.download(candidate("/index.m3u8"), target()) {}.exceptionOrNull()
        assertTrue(error is DownloadError.Unsupported)
    }

    @Test
    fun `an init segment is written before the media segments`() = runTest {
        serve(
            routes = mapOf(
                "/index.m3u8" to """
                    #EXTM3U
                    #EXT-X-MAP:URI="init.mp4"
                    #EXTINF:2.0,
                    a.m4s
                    #EXT-X-ENDLIST
                """.trimIndent(),
            ),
            binary = mapOf(
                "/init.mp4" to "INIT".toByteArray(),
                "/a.m4s" to "DATA".toByteArray(),
            ),
        )

        val file = strategy.download(candidate("/index.m3u8"), target()) {}.getOrThrow()
        assertEquals("INITDATA", file.readText())
    }

    @Test
    fun `candidate headers are sent on every request`() = runTest {
        serve(
            routes = mapOf("/index.m3u8" to "#EXTM3U\n#EXTINF:2.0,\na.ts\n#EXT-X-ENDLIST"),
            binary = mapOf("/a.ts" to "AAA".toByteArray()),
        )

        strategy.download(candidate("/index.m3u8"), target()) {}.getOrThrow()

        repeat(server.requestCount) {
            val request = server.takeRequest()
            assertEquals(
                "https://example.com/watch",
                request.getHeader("Referer"),
            )
        }
    }

    @Test
    fun `progress is reported for every segment`() = runTest {
        serve(
            routes = mapOf(
                "/index.m3u8" to
                    "#EXTM3U\n#EXTINF:2.0,\na.ts\n#EXTINF:2.0,\nb.ts\n#EXT-X-ENDLIST",
            ),
            binary = mapOf(
                "/a.ts" to ByteArray(100),
                "/b.ts" to ByteArray(100),
            ),
        )

        strategy.download(candidate("/index.m3u8"), target()) { progress += it }.getOrThrow()

        assertEquals(2, progress.size)
        assertEquals(100L, progress[0].bytesDownloaded)
        assertEquals(200L, progress[1].bytesDownloaded)
        // Total is extrapolated from what has been fetched so far.
        assertNotNull(progress[0].totalBytes)
        assertEquals(200L, progress[1].totalBytes)
    }

    @Test
    fun `canHandle only claims HLS candidates`() {
        assertTrue(strategy.canHandle(candidate("/index.m3u8")))
        assertFalse(strategy.canHandle(candidate("/x.mp4").copy(type = MediaType.PROGRESSIVE)))
    }
}
