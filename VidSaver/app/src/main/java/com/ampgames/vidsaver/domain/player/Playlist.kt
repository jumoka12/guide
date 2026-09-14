package com.ampgames.vidsaver.domain.player

import com.ampgames.vidsaver.domain.gallery.GalleryVideo

/**
 * The queue the player walks through: whatever the gallery was showing, opened
 * at the video the user tapped.
 *
 * Pure, so the awkward cases — a video deleted mid-playback, an empty gallery,
 * the ends of the list — are settled here and tested rather than discovered in
 * the player.
 */
data class Playlist(
    val items: List<GalleryVideo>,
    val currentIndex: Int,
) {
    val current: GalleryVideo? get() = items.getOrNull(currentIndex)

    val hasNext: Boolean get() = currentIndex < items.lastIndex

    val hasPrevious: Boolean get() = currentIndex > 0

    val size: Int get() = items.size

    fun next(): Playlist = if (hasNext) copy(currentIndex = currentIndex + 1) else this

    fun previous(): Playlist = if (hasPrevious) copy(currentIndex = currentIndex - 1) else this

    fun jumpTo(index: Int): Playlist =
        if (index in items.indices) copy(currentIndex = index) else this

    /**
     * Rebuilds against a changed library, keeping the same video playing where
     * possible. If the current video is gone, falls back to whatever now sits at
     * the same position, so playback does not jump to the start of the list.
     */
    fun syncedWith(available: List<GalleryVideo>): Playlist {
        if (available.isEmpty()) return EMPTY

        val currentId = current?.id
        val newIndex = available.indexOfFirst { it.id == currentId }
        return Playlist(
            items = available,
            currentIndex = if (newIndex >= 0) {
                newIndex
            } else {
                currentIndex.coerceIn(0, available.lastIndex)
            },
        )
    }

    companion object {
        val EMPTY = Playlist(emptyList(), 0)

        /** Opens [videos] at [startId], or at the beginning when it is not present. */
        fun startingAt(videos: List<GalleryVideo>, startId: Long): Playlist {
            if (videos.isEmpty()) return EMPTY
            val index = videos.indexOfFirst { it.id == startId }
            return Playlist(videos, if (index >= 0) index else 0)
        }
    }
}
