package com.ampgames.vidsaver.domain.player

import com.ampgames.vidsaver.domain.gallery.GallerySort
import com.ampgames.vidsaver.domain.gallery.GalleryVideo
import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    @Test
    fun `the hour field only appears once it is needed`() {
        assertEquals("00:00", DurationFormat.position(0))
        assertEquals("00:09", DurationFormat.position(9_000))
        assertEquals("04:12", DurationFormat.position(252_000))
        assertEquals("59:59", DurationFormat.position(3_599_000))
        assertEquals("1:00:00", DurationFormat.position(3_600_000))
        assertEquals("1:02:33", DurationFormat.position(3_753_000))
    }

    @Test
    fun `a negative position is clamped rather than rendered as nonsense`() {
        assertEquals("00:00", DurationFormat.position(-5_000))
    }

    @Test
    fun `sub-second remainders are truncated, not rounded up`() {
        assertEquals("00:01", DurationFormat.position(1_999))
    }

    @Test
    fun `position over duration reads as a pair`() {
        assertEquals("00:30 / 02:00", DurationFormat.positionOfDuration(30_000, 120_000))
    }
}

class PlaybackSpeedTest {

    @Test
    fun `cycling wraps so the button never dead-ends`() {
        assertEquals(PlaybackSpeed.SLOWER, PlaybackSpeed.SLOWEST.next())
        assertEquals(PlaybackSpeed.SLOWEST, PlaybackSpeed.FASTEST.next())
    }

    @Test
    fun `a saved speed maps to the nearest supported one`() {
        assertEquals(PlaybackSpeed.NORMAL, PlaybackSpeed.nearest(1f))
        assertEquals(PlaybackSpeed.FAST, PlaybackSpeed.nearest(1.45f))
        // Values outside the range clamp to the nearest end.
        assertEquals(PlaybackSpeed.FASTEST, PlaybackSpeed.nearest(10f))
        assertEquals(PlaybackSpeed.SLOWEST, PlaybackSpeed.nearest(0.1f))
    }
}

class GallerySortTest {

    private fun video(id: Long, name: String, size: Long, added: Long) = GalleryVideo(
        id = id,
        uri = "content://video/$id",
        displayName = name,
        sizeBytes = size,
        durationMs = 1_000,
        dateAddedMillis = added,
        width = 1920,
        height = 1080,
        mimeType = "video/mp4",
    )

    private val videos = listOf(
        video(1, "Beta.mp4", size = 300, added = 200),
        video(2, "alpha.mp4", size = 100, added = 300),
        video(3, "Gamma.mp4", size = 200, added = 100),
    )

    @Test
    fun `each order does what its name says`() {
        assertEquals(listOf(2L, 1L, 3L), GallerySort.NEWEST.apply(videos).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), GallerySort.OLDEST.apply(videos).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), GallerySort.LARGEST.apply(videos).map { it.id })
    }

    @Test
    fun `name ordering is case insensitive`() {
        assertEquals(listOf(2L, 1L, 3L), GallerySort.NAME.apply(videos).map { it.id })
    }

    @Test
    fun `an unknown persisted id falls back to the default`() {
        assertEquals(GallerySort.NEWEST, GallerySort.fromId(null))
        assertEquals(GallerySort.NEWEST, GallerySort.fromId("nonsense"))
        assertEquals(GallerySort.LARGEST, GallerySort.fromId("largest"))
    }
}
