package com.ampgames.vidsaver.domain.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressThrottleTest {

    private var now = 0L
    private fun throttle(intervalMs: Long = 500) = ProgressThrottle(intervalMs) { now }

    @Test
    fun `the first update always emits`() {
        val sample = throttle().onProgress(0)
        assertNotNull(sample)
        assertEquals(0L, sample!!.speedBytesPerSecond)
    }

    @Test
    fun `updates inside the interval are dropped`() {
        val throttle = throttle(intervalMs = 500)
        throttle.onProgress(0)

        now = 100
        assertNull(throttle.onProgress(1_000))
        now = 499
        assertNull(throttle.onProgress(5_000))
    }

    @Test
    fun `an update after the interval emits with a speed`() {
        val throttle = throttle(intervalMs = 500)
        throttle.onProgress(0)

        now = 1_000
        val sample = throttle.onProgress(2_000)

        assertNotNull(sample)
        assertEquals(2_000L, sample!!.bytesDownloaded)
        // 2000 bytes in 1000 ms.
        assertEquals(2_000L, sample.speedBytesPerSecond)
    }

    @Test
    fun `speed is measured between emitted samples, not since the start`() {
        val throttle = throttle(intervalMs = 500)
        throttle.onProgress(0)

        now = 1_000
        throttle.onProgress(1_000) // 1000 B/s

        now = 2_000
        val second = throttle.onProgress(5_000)

        // 4000 bytes in the second window, not 2500 averaged over both.
        assertEquals(4_000L, second!!.speedBytesPerSecond)
    }

    /** A resumed download can report fewer bytes than the previous attempt. */
    @Test
    fun `a backwards byte count never produces a negative speed`() {
        val throttle = throttle(intervalMs = 500)
        throttle.onProgress(10_000)

        now = 1_000
        val sample = throttle.onProgress(2_000)

        assertEquals(0L, sample!!.speedBytesPerSecond)
    }

    @Test
    fun `the final sample emits regardless of timing`() {
        val throttle = throttle(intervalMs = 500)
        throttle.onProgress(0)

        now = 10
        assertNull(throttle.onProgress(100))

        val final = throttle.finalSample(100)
        assertEquals(100L, final.bytesDownloaded)
    }

    @Test
    fun `a zero-length window does not divide by zero`() {
        val throttle = throttle(intervalMs = 0)
        throttle.onProgress(0)

        // Same timestamp: elapsed is 0.
        val sample = throttle.onProgress(1_000)
        assertEquals(0L, sample!!.speedBytesPerSecond)
    }

    @Test
    fun `a realistic transfer emits about twice a second`() {
        val throttle = throttle(intervalMs = 500)
        var emitted = 0
        var bytes = 0L

        // 5 seconds of 8 KB chunks arriving every 10 ms.
        repeat(500) {
            now += 10
            bytes += 8_192
            if (throttle.onProgress(bytes) != null) emitted++
        }

        assertEquals(10, emitted)
    }
}
