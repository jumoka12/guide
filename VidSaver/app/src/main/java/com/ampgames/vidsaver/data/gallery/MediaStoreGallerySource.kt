package com.ampgames.vidsaver.data.gallery

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ampgames.vidsaver.data.download.MediaStorePublisher
import com.ampgames.vidsaver.di.IoDispatcher
import com.ampgames.vidsaver.domain.gallery.GalleryVideo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Reads the app's own folder out of MediaStore.
 *
 * The query is scoped to `Movies/VidSaver` — the gallery shows what VidSaver
 * saved, never the user's whole video library. On API 29+ that is a
 * `RELATIVE_PATH` match; below, where the column does not exist, it falls back
 * to matching the legacy `DATA` path.
 */
@Singleton
class MediaStoreGallerySource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    val collection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    suspend fun query(): List<GalleryVideo> = withContext(ioDispatcher) {
        runCatching { queryInternal() }.getOrElse { error ->
            Timber.e(error, "Could not read the gallery")
            emptyList()
        }
    }

    private fun queryInternal(): List<GalleryVideo> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
        )

        val (selection, args) = folderSelection()

        val videos = mutableListOf<GalleryVideo>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                videos += GalleryVideo(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    displayName = cursor.getString(nameColumn) ?: "video",
                    sizeBytes = cursor.getLong(sizeColumn),
                    durationMs = cursor.getLong(durationColumn),
                    // DATE_ADDED is in seconds.
                    dateAddedMillis = cursor.getLong(dateColumn) * 1000,
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn),
                    mimeType = cursor.getString(mimeColumn),
                )
            }
        }
        return videos
    }

    private fun folderSelection(): Pair<String, Array<String>> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Trailing separator: MediaStore stores RELATIVE_PATH with one.
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?" to
                arrayOf("${MediaStorePublisher.RELATIVE_PATH}/%")
        } else {
            @Suppress("DEPRECATION")
            val legacyPath = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                .absolutePath + "/${MediaStorePublisher.FOLDER_NAME}/"
            @Suppress("DEPRECATION")
            "${MediaStore.Video.Media.DATA} LIKE ?" to arrayOf("$legacyPath%")
        }

    /** Renames the file in place. Returns false when MediaStore refuses. */
    suspend fun rename(video: GalleryVideo, newDisplayName: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, newDisplayName)
                }
                val updated = context.contentResolver.update(
                    Uri.parse(video.uri),
                    values,
                    null,
                    null,
                )
                check(updated > 0) { "MediaStore did not rename ${video.displayName}" }
            }
        }

    suspend fun delete(video: GalleryVideo): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val deleted = context.contentResolver.delete(Uri.parse(video.uri), null, null)
            check(deleted > 0) { "MediaStore did not delete ${video.displayName}" }
        }
    }
}
