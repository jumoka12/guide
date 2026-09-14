package com.ampgames.vidsaver.domain.player

import com.ampgames.vidsaver.domain.gallery.GalleryVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTest {

    private fun video(id: Long) = GalleryVideo(
        id = id,
        uri = "content://media/external/video/media/$id",
        displayName = "clip$id.mp4",
        sizeBytes = 1_000,
        durationMs = 60_000,
        dateAddedMillis = id * 1_000,
        width = 1920,
        height = 1080,
        mimeType = "video/mp4",
    )

    private val videos = (1L..5L).map { video(it) }

    @Test
    fun `startingAt opens the tapped video`() {
        val playlist = Playlist.startingAt(videos, startId = 3)
        assertEquals(2, playlist.currentIndex)
        assertEquals(3L, playlist.current?.id)
    }

    @Test
    fun `startingAt falls back to the first item for an unknown id`() {
        val playlist = Playlist.startingAt(videos, startId = 99)
        assertEquals(0, playlist.currentIndex)
        assertEquals(1L, playlist.current?.id)
    }

    @Test
    fun `an empty library yields an empty playlist`() {
        val playlist = Playlist.startingAt(emptyList(), startId = 1)
        assertEquals(Playlist.EMPTY, playlist)
        assertNull(playlist.current)
        assertFalse(playlist.hasNext)
        assertFalse(playlist.hasPrevious)
    }

    @Test
    fun `next and previous walk the list`() {
        var playlist = Playlist.startingAt(videos, startId = 1)
        assertFalse(playlist.hasPrevious)
        assertTrue(playlist.hasNext)

        playlist = playlist.next()
        assertEquals(2L, playlist.current?.id)
        assertTrue(playlist.hasPrevious)

        playlist = playlist.previous()
        assertEquals(1L, playlist.current?.id)
    }

    @Test
    fun `the ends of the list are dead stops, not wraps`() {
        val first = Playlist.startingAt(videos, startId = 1)
        assertEquals(first, first.previous())

        val last = Playlist.startingAt(videos, startId = 5)
        assertEquals(last, last.next())
    }

    @Test
    fun `jumpTo ignores an out-of-range index`() {
        val playlist = Playlist.startingAt(videos, startId = 1)
        assertEquals(playlist, playlist.jumpTo(-1))
        assertEquals(playlist, playlist.jumpTo(99))
        assertEquals(3, playlist.jumpTo(3).currentIndex)
    }

    /** The point of syncedWith: a library change must not restart playback. */
    @Test
    fun `syncing keeps the same video playing when the list is reordered`() {
        val playlist = Playlist.startingAt(videos, startId = 3)
        val reordered = videos.reversed()

        val synced = playlist.syncedWith(reordered)

        assertEquals(3L, synced.current?.id)
        assertEquals(2, synced.currentIndex) // same video, new position
    }

    @Test
    fun `syncing survives the current video being deleted`() {
        val playlist = Playlist.startingAt(videos, startId = 3)
        val remaining = videos.filterNot { it.id == 3L }

        val synced = playlist.syncedWith(remaining)

        // Falls back to whatever now occupies that position rather than
        // jumping back to the start.
        assertEquals(2, synced.currentIndex)
        assertEquals(4L, synced.current?.id)
    }

    @Test
    fun `syncing past the end clamps to the last item`() {
        val playlist = Playlist.startingAt(videos, startId = 5)
        val synced = playlist.syncedWith(listOf(video(7), video(8)))

        assertEquals(1, synced.currentIndex)
        assertEquals(8L, synced.current?.id)
    }

    @Test
    fun `syncing with an empty library empties the playlist`() {
        val playlist = Playlist.startingAt(videos, startId = 3)
        assertEquals(Playlist.EMPTY, playlist.syncedWith(emptyList()))
    }
}
