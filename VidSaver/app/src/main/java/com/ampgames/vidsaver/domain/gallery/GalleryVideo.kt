package com.ampgames.vidsaver.domain.gallery

/**
 * A saved video as the gallery sees it.
 *
 * `uri` is a MediaStore `content://` URI, so the value stays valid across
 * restarts and can be handed to the player, a share sheet or another app.
 */
data class GalleryVideo(
    val id: Long,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateAddedMillis: Long,
    val width: Int,
    val height: Int,
    val mimeType: String?,
) {
    /** Name without the container extension, for the rename dialog. */
    val baseName: String get() = displayName.substringBeforeLast('.', displayName)

    val extension: String get() = displayName.substringAfterLast('.', "")

    val resolutionLabel: String? get() = if (height > 0) "${height}p" else null

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else DEFAULT_ASPECT_RATIO

    private companion object {
        const val DEFAULT_ASPECT_RATIO = 16f / 9f
    }
}

/** How the grid is ordered. Persisted, so the user's choice survives a restart. */
enum class GallerySort(val id: String) {
    NEWEST("newest"),
    OLDEST("oldest"),
    NAME("name"),
    LARGEST("largest"),
    ;

    fun apply(videos: List<GalleryVideo>): List<GalleryVideo> = when (this) {
        NEWEST -> videos.sortedByDescending { it.dateAddedMillis }
        OLDEST -> videos.sortedBy { it.dateAddedMillis }
        NAME -> videos.sortedBy { it.displayName.lowercase() }
        LARGEST -> videos.sortedByDescending { it.sizeBytes }
    }

    companion object {
        val DEFAULT = NEWEST

        fun fromId(id: String?): GallerySort =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
