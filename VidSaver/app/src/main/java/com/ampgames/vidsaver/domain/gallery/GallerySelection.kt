package com.ampgames.vidsaver.domain.gallery

/**
 * Multi-select state for the gallery grid.
 *
 * Immutable and pure so the selection rules — what happens when a selected item
 * is deleted underneath you, when the last item is deselected, what "select all"
 * toggles to — are unit-testable without a UI.
 */
data class GallerySelection(val selectedIds: Set<Long> = emptySet()) {

    val isActive: Boolean get() = selectedIds.isNotEmpty()

    val count: Int get() = selectedIds.size

    fun isSelected(id: Long): Boolean = id in selectedIds

    fun toggle(id: Long): GallerySelection =
        if (id in selectedIds) {
            GallerySelection(selectedIds - id)
        } else {
            GallerySelection(selectedIds + id)
        }

    fun clear(): GallerySelection = GallerySelection()

    /**
     * Select-all when anything is unselected, clear when everything is already
     * selected — one button that always does the useful thing.
     */
    fun toggleAll(allIds: List<Long>): GallerySelection =
        if (selectedIds.containsAll(allIds) && allIds.isNotEmpty()) {
            clear()
        } else {
            GallerySelection(allIds.toSet())
        }

    /**
     * Drops ids that no longer exist. Without this, deleting outside the app
     * leaves phantom selections that make the counter lie.
     */
    fun retaining(available: Collection<Long>): GallerySelection {
        val kept = selectedIds intersect available.toSet()
        return if (kept.size == selectedIds.size) this else GallerySelection(kept)
    }

    /** The selected videos, in the order they appear on screen. */
    fun resolve(videos: List<GalleryVideo>): List<GalleryVideo> =
        videos.filter { it.id in selectedIds }

    companion object {
        val EMPTY = GallerySelection()
    }
}
