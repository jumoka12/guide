package com.ampgames.vidsaver.data.download

import com.ampgames.vidsaver.domain.download.DownloadFailure
import com.ampgames.vidsaver.domain.download.DownloadStatus
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadRepositoryTest {

    private lateinit var dao: FakeDownloadDao
    private lateinit var repository: DownloadRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        dao = FakeDownloadDao()
        repository = DownloadRepository(dao) { now }
    }

    private fun candidate(
        url: String = "https://cdn.example.com/a.mp4",
        fileName: String = "clip.mp4",
        type: MediaType = MediaType.PROGRESSIVE,
        headers: Map<String, String> = mapOf("Referer" to "https://example.com/watch"),
        size: Long? = 1_000L,
    ) = MediaCandidate(
        url = url,
        pageUrl = "https://example.com/watch",
        type = type,
        headers = headers,
        sizeBytes = size,
        title = "Clip",
        suggestedFileName = fileName,
    )

    // ------------------------------------------------------------------ enqueue

    @Test
    fun `enqueue stores the candidate as a queued download`() = runTest {
        val result = repository.enqueue(candidate())

        assertFalse(result.alreadyExisted)
        val row = dao.findById(result.id)!!
        assertEquals("https://cdn.example.com/a.mp4", row.url)
        assertEquals("clip.mp4", row.fileName)
        assertEquals(DownloadStatus.QUEUED, row.statusEnum)
        assertEquals(MediaType.PROGRESSIVE.name, row.mediaType)
        assertEquals(1_000L, row.totalBytes)
        assertEquals(now, row.createdAt)
    }

    @Test
    fun `enqueue round-trips the request headers`() = runTest {
        val headers = mapOf("Referer" to "https://example.com/watch", "Cookie" to "sid=abc")
        val id = repository.enqueue(candidate(headers = headers)).id

        val row = dao.findById(id)!!
        assertEquals(headers, repository.decodeHeaders(row))
    }

    @Test
    fun `malformed stored headers decode to empty rather than throwing`() = runTest {
        val id = repository.enqueue(candidate()).id
        val broken = dao.findById(id)!!.copy(headersJson = "not json")

        assertEquals(emptyMap<String, String>(), repository.decodeHeaders(broken))
    }

    /** Tapping Save twice must not produce two copies of the same video. */
    @Test
    fun `enqueueing the same url twice returns the existing row`() = runTest {
        val first = repository.enqueue(candidate())
        val second = repository.enqueue(candidate())

        assertFalse(first.alreadyExisted)
        assertTrue(second.alreadyExisted)
        assertEquals(first.id, second.id)
        assertEquals(1, dao.snapshot.size)
    }

    @Test
    fun `a cancelled download does not block re-enqueueing the same url`() = runTest {
        val first = repository.enqueue(candidate())
        repository.cancel(first.id)

        val second = repository.enqueue(candidate())

        assertFalse(second.alreadyExisted)
        assertNotEquals(first.id, second.id)
    }

    /** Two different videos that happen to share a title must not collide. */
    @Test
    fun `colliding file names are de-duplicated`() = runTest {
        val a = repository.enqueue(candidate(url = "https://cdn.example.com/a.mp4"))
        val b = repository.enqueue(candidate(url = "https://cdn.example.com/b.mp4"))

        assertEquals("clip.mp4", dao.findById(a.id)!!.fileName)
        assertEquals("clip (2).mp4", dao.findById(b.id)!!.fileName)
    }

    // ----------------------------------------------------------------- progress

    @Test
    fun `progress updates only the moving columns`() = runTest {
        val id = repository.enqueue(candidate()).id
        repository.updateProgress(id, bytesDownloaded = 400, totalBytes = 1_000, speed = 250)

        val row = dao.findById(id)!!
        assertEquals(400L, row.bytesDownloaded)
        assertEquals(1_000L, row.totalBytes)
        assertEquals(250L, row.speedBytesPerSecond)
        assertEquals(0.4f, row.progressFraction!!, 0.0001f)
        // File name and headers are untouched by a progress write.
        assertEquals("clip.mp4", row.fileName)
    }

    @Test
    fun `progress fraction is null when the total size is unknown`() = runTest {
        val id = repository.enqueue(candidate(size = null)).id
        repository.updateProgress(id, bytesDownloaded = 400, totalBytes = null, speed = 0)

        assertNull(dao.findById(id)!!.progressFraction)
    }

    // ------------------------------------------------------------------ outcome

    @Test
    fun `completion records the uri, the size and the time`() = runTest {
        val id = repository.enqueue(candidate()).id
        now = 5_000L

        repository.markCompleted(id, "content://media/external/video/media/42", 2_048)

        val row = dao.findById(id)!!
        assertEquals(DownloadStatus.COMPLETED, row.statusEnum)
        assertEquals("content://media/external/video/media/42", row.mediaStoreUri)
        assertEquals(2_048L, row.bytesDownloaded)
        assertEquals(2_048L, row.totalBytes)
        assertEquals(5_000L, row.completedAt)
        assertEquals(0L, row.speedBytesPerSecond)
    }

    @Test
    fun `a retryable failure increments the retry count and stays recoverable`() = runTest {
        val id = repository.enqueue(candidate()).id

        repository.markFailed(id, DownloadFailure.NETWORK, "offline", willRetry = true)

        val row = dao.findById(id)!!
        assertEquals(DownloadStatus.RETRY_SCHEDULED, row.statusEnum)
        assertEquals(DownloadFailure.NETWORK, row.failureEnum)
        assertEquals("offline", row.error)
        assertEquals(1, row.retryCount)
    }

    @Test
    fun `a permanent failure is terminal and does not consume a retry`() = runTest {
        val id = repository.enqueue(candidate()).id

        repository.markFailed(id, DownloadFailure.ENCRYPTED, "protected", willRetry = false)

        val row = dao.findById(id)!!
        assertEquals(DownloadStatus.FAILED, row.statusEnum)
        assertTrue(row.statusEnum.isTerminal)
        assertEquals(0, row.retryCount)
    }

    @Test
    fun `the retry budget runs out`() = runTest {
        val id = repository.enqueue(candidate()).id

        repeat(DownloadRepository.MAX_RETRIES) {
            assertTrue(repository.hasRetriesLeft(id))
            repository.markFailed(id, DownloadFailure.NETWORK, "offline", willRetry = true)
        }

        assertFalse(repository.hasRetriesLeft(id))
        assertEquals(DownloadRepository.MAX_RETRIES, dao.findById(id)!!.retryCount)
    }

    @Test
    fun `markFailed on a missing row is a no-op rather than a crash`() = runTest {
        repository.markFailed(999, DownloadFailure.NETWORK, "gone", willRetry = true)
        assertTrue(dao.snapshot.isEmpty())
    }

    @Test
    fun `requeue clears the previous error`() = runTest {
        val id = repository.enqueue(candidate()).id
        repository.markFailed(id, DownloadFailure.HTTP, "403", willRetry = false)

        repository.requeue(id)

        val row = dao.findById(id)!!
        assertEquals(DownloadStatus.QUEUED, row.statusEnum)
        assertEquals(DownloadFailure.NONE, row.failureEnum)
        assertNull(row.error)
    }

    // ------------------------------------------------------------------ queries

    @Test
    fun `active and finished are partitioned by status`() = runTest {
        val running = repository.enqueue(candidate(url = "https://c.example/1.mp4")).id
        val paused = repository.enqueue(candidate(url = "https://c.example/2.mp4")).id
        val done = repository.enqueue(candidate(url = "https://c.example/3.mp4")).id

        repository.setStatus(running, DownloadStatus.RUNNING)
        repository.setStatus(paused, DownloadStatus.PAUSED)
        repository.markCompleted(done, "content://x", 10)

        assertEquals(listOf(running, paused), repository.observeActive().first().map { it.id })
        assertEquals(listOf(done), repository.observeFinished().first().map { it.id })
        assertEquals(1, repository.activeCount())
    }

    @Test
    fun `nextQueued respects the concurrency limit and insert order`() = runTest {
        val ids = (1..5).map {
            now += 10
            repository.enqueue(candidate(url = "https://c.example/$it.mp4")).id
        }

        assertEquals(ids.take(3), repository.nextQueued(3).map { it.id })
    }

    @Test
    fun `resumable covers paused, retrying and queued`() = runTest {
        val paused = repository.enqueue(candidate(url = "https://c.example/1.mp4")).id
        val retrying = repository.enqueue(candidate(url = "https://c.example/2.mp4")).id
        val failed = repository.enqueue(candidate(url = "https://c.example/3.mp4")).id

        repository.setStatus(paused, DownloadStatus.PAUSED)
        repository.markFailed(retrying, DownloadFailure.NETWORK, null, willRetry = true)
        repository.markFailed(failed, DownloadFailure.ENCRYPTED, null, willRetry = false)

        assertEquals(setOf(paused, retrying), repository.resumable().map { it.id }.toSet())
    }

    @Test
    fun `clearFinished keeps in-flight downloads`() = runTest {
        val running = repository.enqueue(candidate(url = "https://c.example/1.mp4")).id
        val done = repository.enqueue(candidate(url = "https://c.example/2.mp4")).id
        repository.setStatus(running, DownloadStatus.RUNNING)
        repository.markCompleted(done, "content://x", 10)

        repository.clearFinished()

        assertEquals(listOf(running), dao.snapshot.map { it.id })
    }

    /** After a process death, nothing should still claim to be downloading. */
    @Test
    fun `resetInterrupted turns running and queued rows into paused`() = runTest {
        val running = repository.enqueue(candidate(url = "https://c.example/1.mp4")).id
        val queued = repository.enqueue(candidate(url = "https://c.example/2.mp4")).id
        val done = repository.enqueue(candidate(url = "https://c.example/3.mp4")).id
        repository.setStatus(running, DownloadStatus.RUNNING)
        repository.markCompleted(done, "content://x", 10)

        repository.resetInterrupted()

        assertEquals(DownloadStatus.PAUSED, dao.findById(running)!!.statusEnum)
        assertEquals(DownloadStatus.PAUSED, dao.findById(queued)!!.statusEnum)
        assertEquals(DownloadStatus.COMPLETED, dao.findById(done)!!.statusEnum)
    }

    @Test
    fun `delete removes the row`() = runTest {
        val id = repository.enqueue(candidate()).id
        repository.delete(id)
        assertNull(dao.findById(id))
    }
}
