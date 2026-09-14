package com.ampgames.vidsaver.domain.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GallerySelectionTest {

    private fun video(id: Long) = GalleryVideo(
        id = id,
        uri = "content://video/$id",
        displayName = "clip$id.mp4",
        sizeBytes = id * 100,
        durationMs = 1_000,
        dateAddedMillis = id,
        width = 1920,
        height = 1080,
        mimeType = "video/mp4",
    )

    private val videos = (1L..4L).map { video(it) }
    private val ids = videos.map { it.id }

    @Test
    fun `an empty selection is inactive`() {
        assertFalse(GallerySelection.EMPTY.isActive)
        assertEquals(0, GallerySelection.EMPTY.count)
    }

    @Test
    fun `toggle selects then deselects`() {
        val once = GallerySelection.EMPTY.toggle(1)
        assertTrue(once.isSelected(1))
        assertTrue(once.isActive)

        val twice = once.toggle(1)
        assertFalse(twice.isSelected(1))
        assertFalse(twice.isActive)
    }

    @Test
    fun `toggleAll selects everything when anything is unselected`() {
        val partial = GallerySelection.EMPTY.toggle(1)
        assertEquals(ids.toSet(), partial.toggleAll(ids).selectedIds)
    }

    @Test
    fun `toggleAll clears when everything is already selected`() {
        val all = GallerySelection(ids.toSet())
        assertEquals(GallerySelection.EMPTY, all.toggleAll(ids))
    }

    @Test
    fun `toggleAll on an empty gallery selects nothing`() {
        assertEquals(GallerySelection.EMPTY, GallerySelection.EMPTY.toggleAll(emptyList()))
    }

    /** Deleting outside the app must not leave the counter lying. */
    @Test
    fun `retaining drops ids that no longer exist`() {
        val selection = GallerySelection(setOf(1L, 2L, 99L))
        val retained = selection.retaining(ids)

        assertEquals(setOf(1L, 2L), retained.selectedIds)
        assertEquals(2, retained.count)
    }

    @Test
    fun `retaining returns the same instance when nothing changed`() {
        val selection = GallerySelection(setOf(1L, 2L))
        assertTrue(selection === selection.retaining(ids))
    }

    @Test
    fun `resolve returns the selected videos in screen order`() {
        val selection = GallerySelection(setOf(3L, 1L))
        assertEquals(listOf(1L, 3L), selection.resolve(videos).map { it.id })
    }

    @Test
    fun `resolve ignores ids that are not in the list`() {
        assertTrue(GallerySelection(setOf(99L)).resolve(videos).isEmpty())
    }

    @Test
    fun `clear empties the selection`() {
        assertEquals(GallerySelection.EMPTY, GallerySelection(setOf(1L, 2L)).clear())
    }
}
