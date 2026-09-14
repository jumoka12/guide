package com.ampgames.vidsaver.data.download

import com.ampgames.vidsaver.di.IoDispatcher
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.domain.media.MediaType
import com.ampgames.vidsaver.domain.media.hls.HlsMediaPlaylist
import com.ampgames.vidsaver.domain.media.hls.M3u8Parser
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
 * Downloads an HLS stream by fetching every segment and concatenating them, then
 * remuxing the result into MP4.
 *
 * Two things it deliberately refuses:
 * - **Encrypted playlists.** An `EXT-X-KEY` with a real method means the site is
 *   controlling access to the stream. We report it and stop rather than fetch
 *   the key and decrypt.
 * - **Live streams.** A playlist with no `EXT-X-ENDLIST` has no end to download.
 */
class HlsDownloadStrategy @Inject constructor(
    private val client: OkHttpClient,
    private val remuxer: Remuxer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DownloadStrategy {

    override val name: String = "hls"

    override fun canHandle(candidate: MediaCandidate): Boolean = candidate.type == MediaType.HLS

    override suspend fun download(
        candidate: MediaCandidate,
        target: File,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            target.parentFile?.mkdirs()

            val playlist = fetchMediaPlaylist(candidate.url, candidate)

            playlist.encryptionMethod?.let { throw DownloadError.Encrypted(it) }
            if (playlist.isLive) throw DownloadError.LiveStream()
            if (playlist.segments.isEmpty()) {
                throw DownloadError.Unsupported("Playlist contains no segments")
            }

            val concatenated = File(target.parentFile, "${target.nameWithoutExtension}.ts")
            downloadSegments(playlist, candidate, concatenated, onProgress)

            remuxer.remux(concatenated, target).getOrElse { error ->
                // Keeping the raw stream beats discarding a completed download.
                Timber.w(error, "Remux failed for %s; keeping the MPEG-TS stream", candidate.url)
                concatenated
            }
        }.recoverCatching { error ->
            throw when (error) {
                is DownloadError -> error
                is IOException -> DownloadError.Network(error.message ?: "Network error", error)
                else -> error
            }
        }
    }

    /**
     * Fetches the playlist at [url], following a master playlist to the variant
     * that best matches the candidate's resolution — highest quality when none
     * was requested.
     */
    private fun fetchMediaPlaylist(url: String, candidate: MediaCandidate): HlsMediaPlaylist {
        val content = fetchText(url, candidate)
        if (!M3u8Parser.isMasterPlaylist(content)) {
            return M3u8Parser.parseMedia(content, url)
        }

        val variants = M3u8Parser.parseMaster(content, url)
        if (variants.isEmpty()) throw DownloadError.Unsupported("Master playlist has no variants")

        val wanted = candidate.height
        val chosen = if (wanted != null && wanted > 0) {
            variants.minByOrNull { variant -> kotlin.math.abs((variant.height ?: 0) - wanted) }!!
        } else {
            variants.first() // parseMaster sorts best-first
        }
        Timber.d("HLS variant chosen: %dp (%s)", chosen.height ?: 0, chosen.url)

        val variantContent = fetchText(chosen.url, candidate)
        if (M3u8Parser.isMasterPlaylist(variantContent)) {
            throw DownloadError.Unsupported("Nested master playlists are not supported")
        }
        return M3u8Parser.parseMedia(variantContent, chosen.url)
    }

    private fun fetchText(url: String, candidate: MediaCandidate): String {
        val request = Request.Builder()
            .url(url)
            .apply { candidate.headers.forEach { (key, value) -> header(key, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadError.Http(response.code, "Playlist request failed (${response.code})")
            }
            return response.body?.string()
                ?: throw DownloadError.Network("Empty playlist response")
        }
    }

    private suspend fun downloadSegments(
        playlist: HlsMediaPlaylist,
        candidate: MediaCandidate,
        target: File,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val urls = buildList {
            playlist.initSegmentUrl?.let { add(it) }
            addAll(playlist.segments.map { it.url })
        }

        if (target.exists()) target.delete()
        var written = 0L

        FileOutputStream(target).use { output ->
            urls.forEachIndexed { index, segmentUrl ->
                coroutineContext.ensureActive()
                written += appendSegment(segmentUrl, candidate, output)
                onProgress(
                    DownloadProgress(
                        bytesDownloaded = written,
                        // Running estimate: no HLS playlist states a total size,
                        // so extrapolate from the segments fetched so far.
                        totalBytes = estimateTotal(written, index + 1, urls.size),
                    ),
                )
            }
            output.flush()
        }
    }

    private fun appendSegment(
        url: String,
        candidate: MediaCandidate,
        output: FileOutputStream,
    ): Long {
        val request = Request.Builder()
            .url(url)
            .apply { candidate.headers.forEach { (key, value) -> header(key, value) } }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadError.Http(response.code, "Segment request failed (${response.code})")
            }
            val body = response.body ?: throw DownloadError.Network("Empty segment response")
            var segmentBytes = 0L
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    segmentBytes += read
                }
            }
            return segmentBytes
        }
    }

    private fun estimateTotal(written: Long, done: Int, total: Int): Long? {
        if (done <= 0 || total <= 0) return null
        return (written.toDouble() / done * total).toLong()
    }
}
