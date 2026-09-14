package com.ampgames.vidsaver.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ampgames.vidsaver.di.IoDispatcher
import com.ampgames.vidsaver.domain.media.MediaTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Moves a finished download out of app-private storage and into the user's
 * media library at `Movies/VidSaver/`.
 *
 * On API 29+ this goes through MediaStore with `IS_PENDING`, so the file is
 * invisible to other apps until it is fully written and no storage permission
 * is needed. On API 24–28 there is no scoped storage, so the file is written
 * under the public Movies directory and then registered with MediaStore; that
 * path is the only reason the app declares WRITE_EXTERNAL_STORAGE at all, and
 * the manifest caps it at `maxSdkVersion="28"`.
 */
@Singleton
class MediaStorePublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param source the completed temp file; deleted once copied.
     * @return the `content://` URI of the published video.
     */
    suspend fun publish(source: File, fileName: String, mimeType: String?): Result<Published> =
        withContext(ioDispatcher) {
            runCatching {
                require(source.exists() && source.length() > 0) {
                    "Nothing to publish: ${source.name} is missing or empty"
                }
                val size = source.length()
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    publishScoped(source, fileName, mimeType)
                } else {
                    publishLegacy(source, fileName, mimeType)
                }
                source.delete()
                Published(uri, size)
            }.onFailure { Timber.e(it, "Could not publish %s", fileName) }
        }

    private fun publishScoped(source: File, fileName: String, mimeType: String?): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, resolveMime(fileName, mimeType))
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            // Hidden from the gallery until the bytes are all there.
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri: Uri = resolver.insert(collection, values)
            ?: error("MediaStore refused to create an entry for $fileName")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not open an output stream for $fileName")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (e: Throwable) {
            // A half-written pending entry would linger invisibly forever.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }

        return uri.toString()
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(source: File, fileName: String, mimeType: String?): String {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val targetDir = File(moviesDir, FOLDER_NAME).apply { mkdirs() }
        val target = File(targetDir, fileName)

        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, resolveMime(fileName, mimeType))
            put(MediaStore.Video.Media.DATA, target.absolutePath)
            put(MediaStore.Video.Media.SIZE, target.length())
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: error("MediaStore refused to index $fileName")

        return uri.toString()
    }

    /** Removes a published file, used when the user deletes a download. */
    suspend fun delete(mediaStoreUri: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            context.contentResolver.delete(Uri.parse(mediaStoreUri), null, null) > 0
        }.getOrElse { error ->
            Timber.w(error, "Could not delete %s", mediaStoreUri)
            false
        }
    }

    private fun resolveMime(fileName: String, mimeType: String?): String {
        MediaTypes.normalizeMime(mimeType)
            ?.takeIf { it.startsWith("video/") }
            ?.let { return it }
        return MediaTypes.mimeForExtension(fileName.substringAfterLast('.', "mp4"))
    }

    data class Published(val uri: String, val sizeBytes: Long)

    companion object {
        const val FOLDER_NAME = "VidSaver"

        /** Where downloads land in the user's library. */
        val RELATIVE_PATH: String = "${Environment.DIRECTORY_MOVIES}/$FOLDER_NAME"
    }
}
