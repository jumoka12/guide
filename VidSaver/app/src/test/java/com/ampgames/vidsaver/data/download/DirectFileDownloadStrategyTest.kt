package com.ampgames.vidsaver.data.download

import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.MediaType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectFileDownloadStrategyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var strategy: DirectFileDownloadStrategy

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        strategy = DirectFileDownloadStrategy(OkHttpClient(), Dispatchers.IO)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun candidate() = MediaCandidate(
        url = server.url("/video.mp4").toString(),
        pageUrl = "https://example.com/watch",
        type = MediaType.PROGRESSIVE,
        headers = mapOf("Referer" to "https://example.com/watch"),
        suggestedFileName = "video.mp4",
    )

    private fun target() = File(tempFolder.root, "video.mp4")

    @Test
    fun `a fresh download writes the whole body`() = runTest {
        server.enqueue(MockResponse().setBody("HELLO WORLD"))

        val file = strategy.download(candidate(), target()) {}.getOrThrow()

        assertEquals("HELLO WORLD", file.readText())
        assertEquals("https://example.com/watch", server.takeRequest().getHeader("Referer"))
    }

    @Test
    fun `an existing partial file is resumed with a Range header`() = runTest {
        val target = target().apply { writeText("HELLO ") }
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 6-10/11")
                .setBody("WORLD"),
        )

        val file = strategy.download(candidate(), target) {}.getOrThrow()

        assertEquals("bytes=6-", server.takeRequest().getHeader("Range"))
        assertEquals("HELLO WORLD", file.readText())
    }

    /**
     * Regression guard: when the server ignores the Range header and replies 200
     * with the whole body, appending would corrupt the file. It must restart.
     */
    @Test
    fun `a server that ignores Range causes a clean restart, not a corrupt append`() = runTest {
        val target = target().apply { writeText("HELLO ") }
        server.enqueue(MockResponse().setResponseCode(200).setBody("HELLO WORLD"))

        val file = strategy.download(candidate(), target) {}.getOrThrow()

        assertEquals("HELLO WORLD", file.readText())
        assertFalse("file must not contain a duplicated prefix", file.readText().startsWith("HELLO HELLO"))
    }

    @Test
    fun `an http error is reported and not written to disk`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val error = strategy.download(candidate(), target()) {}.exceptionOrNull()

        assertTrue(error is DownloadError.Http)
        assertEquals(403, (error as DownloadError.Http).code)
    }

    @Test
    fun `progress reports the final byte count`() = runTest {
        val body = "x".repeat(4096)
        server.enqueue(MockResponse().setBody(body))
        val seen = mutableListOf<DownloadProgress>()

        strategy.download(candidate(), target()) { seen += it }.getOrThrow()

        assertEquals(4096L, seen.last().bytesDownloaded)
        assertEquals(4096L, seen.last().totalBytes)
        assertEquals(1f, seen.last().fraction!!, 0.0001f)
    }

    @Test
    fun `canHandle only claims progressive candidates`() {
        assertTrue(strategy.canHandle(candidate()))
        assertFalse(strategy.canHandle(candidate().copy(type = MediaType.HLS)))
    }
}
