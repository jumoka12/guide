package com.ampgames.vidsaver.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ByteFormatTest {

    @Test
    fun `sizes use binary units`() {
        assertEquals("0 B", ByteFormat.size(0))
        assertEquals("512 B", ByteFormat.size(512))
        assertEquals("1.0 KB", ByteFormat.size(1024))
        assertEquals("1.5 KB", ByteFormat.size(1536))
        assertEquals("1.0 MB", ByteFormat.size(1024L * 1024))
        assertEquals("2.5 GB", ByteFormat.size((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `a negative size is clamped rather than rendered as nonsense`() {
        assertEquals("0 B", ByteFormat.size(-1))
    }

    @Test
    fun `speed is null when there is nothing meaningful to report`() {
        assertNull(ByteFormat.speed(0))
        assertNull(ByteFormat.speed(-5))
        assertEquals("1.0 MB/s", ByteFormat.speed(1024L * 1024))
    }

    @Test
    fun `progress shows the total only when it is known`() {
        assertEquals("1.0 KB / 2.0 KB", ByteFormat.progress(1024, 2048))
        assertEquals("1.0 KB", ByteFormat.progress(1024, null))
        assertEquals("1.0 KB", ByteFormat.progress(1024, 0))
    }

    @Test
    fun `remaining time is estimated only when both inputs are known`() {
        assertNull(ByteFormat.remaining(0, null, 100))
        assertNull(ByteFormat.remaining(0, 1000, 0))
        assertNull(ByteFormat.remaining(1000, 1000, 100)) // already done
    }

    @Test
    fun `remaining time is formatted coarsely`() {
        assertEquals("10s", ByteFormat.remaining(0, 1_000, 100))
        assertEquals("1m 40s", ByteFormat.remaining(0, 10_000, 100))
        assertEquals("2h 46m", ByteFormat.remaining(0, 1_000_000, 100))
    }
}
