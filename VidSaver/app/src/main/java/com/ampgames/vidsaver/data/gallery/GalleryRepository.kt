package com.ampgames.vidsaver.data.gallery

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.ampgames.vidsaver.di.IoDispatcher
import com.ampgames.vidsaver.domain.gallery.GalleryVideo
import com.ampgames.vidsaver.domain.media.FileNames
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * The saved-video library.
 *
 * Reads are driven by a [ContentObserver] rather than polling, so the grid
 * updates as soon as a download finishes or the user deletes something from
 * another app.
 */
@Singleton
class GalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: MediaStoreGallerySource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Fires once immediately, then on every MediaStore change. Carries no data:
     * it is purely a "something changed, re-read" signal, so a burst of changes
     * collapses into one query.
     */
    private val changeSignals: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }

        runCatching {
            context.contentResolver.registerContentObserver(source.collection, true, observer)
        }.onFailure { Timber.w(it, "Could not observe MediaStore; the grid will not auto-refresh") }

        trySend(Unit)

        awaitClose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }.conflate()

    /** The current library, re-read whenever MediaStore changes. */
    val videos: Flow<List<GalleryVideo>> = changeSignals
        .map { source.query() }
        .flowOn(ioDispatcher)

    suspend fun refresh(): List<GalleryVideo> = source.query()

    /**
     * Renames a video, preserving its extension and sanitising the input so a
     * pasted title cannot introduce path separators.
     *
     * @param newBaseName name without the extension, as typed by the user.
     */
    suspend fun rename(video: GalleryVideo, newBaseName: String): Result<String> {
        val sanitized = FileNames.sanitize(newBaseName)
        if (sanitized.isBlank()) {
            return Result.failure(IllegalArgumentException("Empty name"))
        }
        val extension = video.extension
        val newName = if (extension.isBlank()) sanitized else "$sanitized.$extension"
        if (newName == video.displayName) return Result.success(newName)

        return source.rename(video, newName).map { newName }
    }

    suspend fun delete(videos: List<GalleryVideo>): DeleteOutcome {
        var deleted = 0
        val failed = mutableListOf<GalleryVideo>()
        videos.forEach { video ->
            source.delete(video).fold(
                onSuccess = { deleted++ },
                onFailure = { error ->
                    Timber.w(error, "Could not delete %s", video.displayName)
                    failed += video
                },
            )
        }
        return DeleteOutcome(deleted = deleted, failed = failed)
    }

    data class DeleteOutcome(val deleted: Int, val failed: List<GalleryVideo>) {
        val allSucceeded: Boolean get() = failed.isEmpty()
    }
}

/** Content URIs for a share sheet. */
fun List<GalleryVideo>.toUris(): List<Uri> = map { Uri.parse(it.uri) }
