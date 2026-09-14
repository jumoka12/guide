package com.ampgames.vidsaver.domain.download

/**
 * Decides when a download's progress is worth writing to the database, and
 * works out the transfer speed while it is at it.
 *
 * A download can call back thousands of times a second; writing every one would
 * hammer the DB and the UI for no benefit. This emits at most one sample per
 * [intervalMs], plus always the final one.
 *
 * Pure and clock-injected so the timing behaviour is unit-testable.
 */
class ProgressThrottle(
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val clock: () -> Long,
) {

    private var lastEmittedAt: Long = 0
    private var lastEmittedBytes: Long = 0
    private var started = false

    data class Sample(val bytesDownloaded: Long, val speedBytesPerSecond: Long)

    /**
     * @return a [Sample] when enough time has passed since the last one, or null
     *   when this update should be skipped.
     */
    fun onProgress(bytesDownloaded: Long): Sample? {
        val now = clock()
        if (!started) {
            started = true
            lastEmittedAt = now
            lastEmittedBytes = bytesDownloaded
            return Sample(bytesDownloaded, 0)
        }

        val elapsed = now - lastEmittedAt
        if (elapsed < intervalMs) return null

        val sample = Sample(bytesDownloaded, speedOver(elapsed, bytesDownloaded))
        lastEmittedAt = now
        lastEmittedBytes = bytesDownloaded
        return sample
    }

    /** Final sample, emitted regardless of timing so the UI lands on the true total. */
    fun finalSample(bytesDownloaded: Long): Sample {
        val elapsed = clock() - lastEmittedAt
        return Sample(bytesDownloaded, if (elapsed <= 0) 0 else speedOver(elapsed, bytesDownloaded))
    }

    private fun speedOver(elapsedMs: Long, bytesDownloaded: Long): Long {
        if (elapsedMs <= 0) return 0
        val delta = bytesDownloaded - lastEmittedBytes
        // A resumed download can report fewer bytes than the last sample of a
        // previous attempt; never report a negative speed.
        if (delta <= 0) return 0
        return delta * 1000 / elapsedMs
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 500L
    }
}
