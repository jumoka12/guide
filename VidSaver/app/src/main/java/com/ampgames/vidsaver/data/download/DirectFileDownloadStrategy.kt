package com.ampgames.vidsaver.data.download

import com.ampgames.vidsaver.di.IoDispatcher
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.MediaType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import kotlin.coroutines.coroutineContext

/**
 * Downloads a single file over HTTP, resuming with a `Range` header when a
 * partial file is already on disk and the server supports it.
 */
class DirectFileDownloadStrategy @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DownloadStrategy {

    override val name: String = "direct"

    override fun canHandle(candidate: MediaCandidate): Boolean =
        candidate.type == MediaType.PROGRESSIVE

    override suspend fun download(
        candidate: MediaCandidate,
        target: File,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            target.parentFile?.mkdirs()

            val existingBytes = if (target.exists()) target.length() else 0L
            val request = Request.Builder()
                .url(candidate.url)
                .apply {
                    candidate.headers.forEach { (key, value) -> header(key, value) }
                    if (existingBytes > 0) header("Range", "bytes=$existingBytes-")
                }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadError.Http(response.code, "Server returned ${response.code}")
                }

                // 206 means the server honoured the range; anything else means we
                // must start over rather than corrupt the file by appending.
                val resuming = existingBytes > 0 && response.code == 206
                if (existingBytes > 0 && !resuming) {
                    Timber.d("Server ignored Range for %s, restarting", candidate.url)
                }

                val body = response.body ?: throw DownloadError.Network("Empty response body")
                val reportedLength = body.contentLength().takeIf { it > 0 }
                val totalBytes = when {
                    reportedLength == null -> candidate.sizeBytes
                    resuming -> reportedLength + existingBytes
                    else -> reportedLength
                }

                var written = if (resuming) existingBytes else 0L
                onProgress(DownloadProgress(written, totalBytes))

                body.byteStream().use { input ->
                    // append = resuming; otherwise truncate and start clean.
                    FileOutputStream(target, resuming).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var lastReported = written
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            written += read
                            if (written - lastReported >= PROGRESS_INTERVAL_BYTES) {
                                onProgress(DownloadProgress(written, totalBytes))
                                lastReported = written
                            }
                        }
                        output.flush()
                    }
                }

                onProgress(DownloadProgress(written, totalBytes ?: written))
                target
            }
        }.recoverCatching { error ->
            throw when (error) {
                is DownloadError -> error
                is IOException -> DownloadError.Network(error.message ?: "Network error", error)
                else -> error
            }
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_BYTES = 256L * 1024
    }
}
