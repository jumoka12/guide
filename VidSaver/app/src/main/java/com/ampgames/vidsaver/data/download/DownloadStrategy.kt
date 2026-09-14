package com.ampgames.vidsaver.data.download

import com.ampgames.vidsaver.domain.media.MediaCandidate
import java.io.File

/** Progress of an in-flight download. [totalBytes] is null when unknown. */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }
}

/** Why a download could not be performed. */
sealed class DownloadError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class Network(message: String, cause: Throwable? = null) : DownloadError(message, cause)

    class Http(val code: Int, message: String) : DownloadError(message)

    /**
     * The stream is access-controlled. We report it and stop; decrypting it
     * would be circumventing the site's protection.
     */
    class Encrypted(val method: String) :
        DownloadError("Stream is protected with $method and cannot be saved")

    class LiveStream : DownloadError("Live streams cannot be saved")

    class Unsupported(message: String) : DownloadError(message)
}

/**
 * How a given kind of media gets turned into a file on disk.
 *
 * Implementations write to [target] and must be safe to call again after a
 * failure: [DirectFileDownloadStrategy] resumes from what is already there,
 * [HlsDownloadStrategy] skips segments it already has.
 */
interface DownloadStrategy {

    val name: String

    fun canHandle(candidate: MediaCandidate): Boolean

    /**
     * @param target file to write; its parent directory is created if needed.
     * @param onProgress called from the download's own coroutine, throttled by
     *   the caller if needed.
     * @return the written file, or a [DownloadError] describing why not.
     */
    suspend fun download(
        candidate: MediaCandidate,
        target: File,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<File>
}
